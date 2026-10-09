package io.github.keycloakmcp.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.StaleObjectStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import io.github.keycloakmcp.target.RegistryOwner;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.SynchronizationType;

/** Exercises real ORM version checks in the existing disposable PostgreSQL fixture. */
@QuarkusTest
class TargetRegistryRevisionTest {
    @Inject TargetRepository targets;
    @Inject TargetPersistenceMapper mapper;
    @Inject EntityManagerFactory entityManagerFactory;

    private final List<String> ownedTargetIds = new ArrayList<>();

    @AfterEach
    void removeOnlyOwnedFixtures() {
        if (ownedTargetIds.isEmpty()) return;
        QuarkusTransaction.requiringNew().run(() -> {
            for (String id : ownedTargetIds) targets.deleteById(id);
        });
    }

    @Test
    void mapperDoesNotInferConfigurationOwnershipFromDomainValues() {
        TargetEntity entity = mapper.toEntity(target("lab-keycloak-a", "Same configured ID"));

        assertThat(entity.registryOwner).isEqualTo(RegistryOwner.LEGACY_UNCLASSIFIED);
        assertThat(entity.registryRevision).isZero();
    }

    @ParameterizedTest
    @EnumSource(RegistryOwner.class)
    void mapperUpdatesAndReadsDoNotReassignOwnerOrRevision(RegistryOwner owner) {
        TargetEntity entity = mapper.toEntity(target("mapper-owner-fixture", "Original"));
        entity.registryOwner = owner;
        entity.registryRevision = 17;

        mapper.updateEntity(target(entity.id, "Updated"), entity);
        Target result = mapper.toDomain(entity);

        assertThat(result.displayName()).isEqualTo("Updated");
        assertThat(entity.registryOwner).isEqualTo(owner);
        assertThat(entity.registryRevision).isEqualTo(17);
    }

    @Test
    void committedChangesIncrementRowRevisionButReadOnlyTransactionsDoNot() {
        String id = createOwnedFixture();
        State initial = state(id);
        assertThat(initial).isEqualTo(new State(RegistryOwner.CONFIGURATION, 0, "Original", "observed-uid", 7, true));

        QuarkusTransaction.requiringNew().run(() -> targets.findById(id).displayName = "First update");

        assertThat(state(id)).isEqualTo(new State(RegistryOwner.CONFIGURATION, 1, "First update", "observed-uid", 7, true));
        QuarkusTransaction.requiringNew().run(() -> assertThat(targets.findById(id).registryRevision).isEqualTo(1));
        assertThat(state(id).registryRevision()).isEqualTo(1);

        QuarkusTransaction.requiringNew().run(() -> targets.findById(id).displayName = "Second update");

        assertThat(state(id)).isEqualTo(new State(RegistryOwner.CONFIGURATION, 2, "Second update", "observed-uid", 7, true));
    }

    @Test
    void bindingRevisionAndRowRevisionRemainSeparate() {
        String id = createOwnedFixture();

        QuarkusTransaction.requiringNew().run(() -> {
            TargetEntity entity = targets.findById(id);
            entity.installationUid = "confirmed-replacement-uid";
            entity.installationRevision++;
        });

        assertThat(state(id)).isEqualTo(new State(RegistryOwner.CONFIGURATION, 1, "Original",
                "confirmed-replacement-uid", 8, true));
    }

    @Test
    void staleIndependentEntityManagerCannotOverwriteCommittedTarget() {
        String id = createOwnedFixture();
        // Unsynchronized application-managed contexts survive the first transaction and
        // join subsequent transactions explicitly: no merge/refresh hides the stale version.
        EntityManager winner = entityManagerFactory.createEntityManager(SynchronizationType.UNSYNCHRONIZED);
        EntityManager stale = entityManagerFactory.createEntityManager(SynchronizationType.UNSYNCHRONIZED);
        try {
            TargetEntity[] snapshots = QuarkusTransaction.requiringNew().call(() -> {
                winner.joinTransaction();
                stale.joinTransaction();
                return new TargetEntity[] {winner.find(TargetEntity.class, id), stale.find(TargetEntity.class, id)};
            });
            assertThat(snapshots[0]).isNotSameAs(snapshots[1]);
            assertThat(snapshots[0].registryRevision).isZero();
            assertThat(snapshots[1].registryRevision).isZero();
            assertThat(winner.contains(snapshots[0])).isTrue();
            assertThat(stale.contains(snapshots[1])).isTrue();

            QuarkusTransaction.requiringNew().run(() -> {
                winner.joinTransaction();
                snapshots[0].displayName = "Committed winner";
            });
            assertThat(state(id).registryRevision()).isEqualTo(1);
            assertThat(snapshots[1].registryRevision).isZero();

            Throwable failure = catchThrowable(() -> QuarkusTransaction.requiringNew().run(() -> {
                stale.joinTransaction();
                snapshots[1].displayName = "Stale overwrite";
                snapshots[1].registryOwner = RegistryOwner.LEGACY_UNCLASSIFIED;
            }));

            assertThat(failure).isNotNull();
            assertThat(causes(failure)).anyMatch(cause -> cause instanceof OptimisticLockException
                    || cause instanceof StaleObjectStateException);
            assertThat(state(id)).isEqualTo(new State(RegistryOwner.CONFIGURATION, 1, "Committed winner",
                    "observed-uid", 7, true));
        } finally {
            winner.close();
            stale.close();
        }
    }

    private String createOwnedFixture() {
        String id = "registry-revision-" + UUID.randomUUID();
        ownedTargetIds.add(id);
        QuarkusTransaction.requiringNew().run(() -> {
            TargetEntity entity = mapper.toEntity(target(id, "Original"));
            // An explicit writer decision, not mapper inference or migration adoption.
            entity.registryOwner = RegistryOwner.CONFIGURATION;
            entity.installationRevision = 7;
            entity.installationManaged = true;
            targets.persist(entity);
        });
        return id;
    }

    private State state(String id) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TargetEntity entity = targets.findById(id);
            return new State(entity.registryOwner, entity.registryRevision, entity.displayName,
                    entity.installationUid, entity.installationRevision, entity.installationManaged);
        });
    }

    private static Target target(String id, String displayName) {
        return new Target(TargetId.of(id), displayName, TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("https://keycloak.example.invalid", "master", "reader", "credential-ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.OPENSHIFT, "test-cluster", "iam", "cluster-ref",
                        new KubernetesInstallationBinding("apps/v1", "Deployment", "rhbk", "observed-uid")),
                null, Map.of("suite", "registry-revision"));
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = failure; cause != null && !causes.contains(cause); cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }

    private record State(RegistryOwner registryOwner, long registryRevision, String displayName,
            String installationUid, long installationRevision, boolean installationManaged) {}
}
