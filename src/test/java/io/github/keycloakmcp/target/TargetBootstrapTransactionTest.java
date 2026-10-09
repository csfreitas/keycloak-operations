package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;

/** Real PostgreSQL transactions; all fixtures and cleanup are scoped to generated IDs. */
@QuarkusTest
class TargetBootstrapTransactionTest {

    private static final Instant ORIGINAL_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final KubernetesInstallationBinding BINDING =
            new KubernetesInstallationBinding("apps/v1", "Deployment", "synthetic-rhbk", "synthetic-installation-uid");

    @Inject TargetBootstrapService service;
    @Inject TargetRepository targets;
    @Inject TargetPersistenceMapper mapper;
    @InjectMock ConfigurationTargetRegistry configuration;
    @InjectSpy AuditRepository audits;

    private final List<String> ownedIds = new ArrayList<>();

    @BeforeEach
    void resetCollaborators() {
        reset(configuration, audits);
        when(configuration.list()).thenReturn(List.of());
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        reset(configuration, audits);
        QuarkusTransaction.requiringNew().run(() -> {
            for (String id : ownedIds) {
                audits.delete("targetId", id);
                targets.deleteById(id);
            }
        });
    }

    @Test
    void createRepeatedNormalizedConfigurationAndUpdateHaveExactCommittedRevisions() {
        String id = newId();
        Target initial = target(id, "Synthetic target", "master", null);
        when(configuration.list()).thenReturn(List.of(initial));

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);
        TargetSnapshot created = snapshot(id);
        assertThat(created.owner()).isEqualTo(RegistryOwner.CONFIGURATION);
        assertThat(created.registryRevision()).isZero();
        assertThat(created.installationRevision()).isZero();
        assertThat(created.createdAt()).isEqualTo(created.updatedAt());
        assertThat(created.target().keycloak().managementUrl()).isEqualTo(initial.keycloak().managementUrl());
        assertThat(created.target().tags()).containsEntry("management-url", initial.keycloak().managementUrl());
        AuditSnapshot createAudit = audit(id, "TARGET_CONFIG_CREATED");
        assertSafeAudit(createAudit);
        assertThat(createAudit.metadata()).containsEntry("previousRegistryRevision", null)
                .containsEntry("registryRevision", 0).containsEntry("bindingInvalidated", false);

        // The mapper adds management-url to persisted tags. That normalization is not a new change.
        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(snapshot(id)).isEqualTo(created);
        assertThat(auditCount(id)).isEqualTo(1);

        when(configuration.list()).thenReturn(List.of(target(id, "Synthetic target renamed", "master", null)));
        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);
        TargetSnapshot updated = snapshot(id);
        assertThat(updated.target().displayName()).isEqualTo("Synthetic target renamed");
        assertThat(updated.registryRevision()).isEqualTo(1);
        assertThat(updated.installationRevision()).isZero();
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
        assertThat(auditCount(id)).isEqualTo(2);
        AuditSnapshot updateAudit = audit(id, "TARGET_CONFIG_UPDATED");
        assertSafeAudit(updateAudit);
        assertThat(updateAudit.metadata()).containsEntry("previousRegistryRevision", 0)
                .containsEntry("registryRevision", 1).containsEntry("bindingInvalidated", false);
        assertThat(updateAudit.metadata().get("changedFields")).isEqualTo(List.of("displayName"));

        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(snapshot(id)).isEqualTo(updated);
        assertThat(auditCount(id)).isEqualTo(2);
    }

    @Test
    void connectionChangeInvalidatesManagedBindingWhileUnchangedRestartPreservesIt() {
        String id = newId();
        seed(id, RegistryOwner.CONFIGURATION, true);
        TargetSnapshot before = snapshot(id);
        when(configuration.list()).thenReturn(List.of(target(id, "Synthetic target", "master", null)));

        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(auditCount(id)).isZero();

        when(configuration.list()).thenReturn(List.of(target(id, "Synthetic target", "changed-auth-realm", null)));
        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);
        TargetSnapshot changed = snapshot(id);
        assertThat(changed.registryRevision()).isEqualTo(before.registryRevision() + 1);
        assertThat(changed.installationRevision()).isEqualTo(before.installationRevision() + 1);
        assertThat(changed.managed()).isFalse();
        assertThat(changed.target().infrastructure().installation()).isNull();
        AuditSnapshot event = audit(id, "TARGET_CONFIG_UPDATED");
        assertSafeAudit(event);
        assertThat(event.metadata()).containsEntry("bindingInvalidated", true)
                .containsEntry("previousInstallationRevision", 7).containsEntry("installationRevision", 8);
        assertThat(event.metadata().get("changedFields")).asList().contains("keycloak.authRealm");
    }

    @Test
    void repeatedAmbiguousConnectionChangesCannotResurrectAnExplicitConfiguredBinding() {
        String id = newId();
        seed(id, RegistryOwner.CONFIGURATION, true);
        TargetSnapshot original = snapshot(id);
        when(configuration.list()).thenReturn(List.of(
                target(id, "Synthetic target", "changed-auth-realm", BINDING)));

        // Repeating the identical configuration must not first clear and then silently rebind.
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(service::syncConfigTargetsToDatabase)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Configuration cannot replace a managed binding while changing its connection; "
                            + "remove the configured binding first");
            assertThat(snapshot(id)).isEqualTo(original);
            assertThat(auditCount(id)).isZero();
        }
    }

    @Test
    void lateOwnershipConflictRollsBackEarlierCreateUpdateBindingAndAudits() {
        // Bootstrap sorts IDs before locking; keep the ownership conflict deliberately last.
        String createdId = newId("a-created");
        String changedId = newId("b-changed");
        String conflictingId = newId("z-conflict");
        seed(changedId, RegistryOwner.CONFIGURATION, true);
        seed(conflictingId, RegistryOwner.LEGACY_UNCLASSIFIED, false);
        TargetSnapshot beforeChanged = snapshot(changedId);
        TargetSnapshot beforeConflict = snapshot(conflictingId);
        when(configuration.list()).thenReturn(List.of(
                target(createdId, "Synthetic new target", "master", null),
                target(changedId, "Synthetic changed target", "changed-auth-realm", null),
                target(conflictingId, "Must not adopt legacy target", "changed-auth-realm", null)));

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isInstanceOf(RuntimeException.class);

        verify(audits).persistAndFlush(argThat(event -> createdId.equals(event.targetId)));
        verify(audits).persistAndFlush(argThat(event -> changedId.equals(event.targetId)));
        assertThat(snapshot(createdId)).isNull();
        assertThat(snapshot(changedId)).isEqualTo(beforeChanged);
        assertThat(snapshot(conflictingId)).isEqualTo(beforeConflict);
        assertThat(auditCount(createdId)).isZero();
        assertThat(auditCount(changedId)).isZero();
        assertThat(auditCount(conflictingId)).isZero();
    }

    @Test
    void mandatoryAuditFailureRollsBackNewTargetAndInsertedAudit() {
        String id = newId();
        when(configuration.list()).thenReturn(List.of(target(id, "Synthetic target", "master", null)));
        AtomicReference<TargetSnapshot> stateSeenAtFailure = failAfterAuditFlush(id);

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isInstanceOf(RuntimeException.class);

        assertThat(stateSeenAtFailure.get()).isNotNull();
        assertThat(stateSeenAtFailure.get().registryRevision()).isZero();
        assertThat(snapshot(id)).isNull();
        assertThat(auditCount(id)).isZero();
    }

    @Test
    void mandatoryAuditFailureRollsBackFlushedConnectionBindingAndOptimisticVersion() {
        String id = newId();
        seed(id, RegistryOwner.CONFIGURATION, true);
        TargetSnapshot original = snapshot(id);
        when(configuration.list()).thenReturn(List.of(target(id, "Synthetic changed target", "changed-auth-realm", null)));
        AtomicReference<TargetSnapshot> stateSeenAtFailure = failAfterAuditFlush(id);

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isInstanceOf(RuntimeException.class);

        TargetSnapshot flushed = stateSeenAtFailure.get();
        assertThat(flushed).isNotNull();
        assertThat(flushed.registryRevision()).isEqualTo(original.registryRevision() + 1);
        assertThat(flushed.installationRevision()).isEqualTo(original.installationRevision() + 1);
        assertThat(flushed.managed()).isFalse();
        assertThat(flushed.target().infrastructure().installation()).isNull();
        assertThat(snapshot(id)).isEqualTo(original);
        assertThat(auditCount(id)).isZero();
    }

    private AtomicReference<TargetSnapshot> failAfterAuditFlush(String id) {
        AtomicReference<TargetSnapshot> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            AuditEventEntity event = invocation.getArgument(0);
            Object result = invocation.callRealMethod();
            if (id.equals(event.targetId) && "registry.bootstrap".equals(event.tool)) {
                seen.set(toSnapshot(targets.findById(id)));
                throw new IllegalStateException("Synthetic mandatory audit failure");
            }
            return result;
        }).when(audits).persistAndFlush(any(AuditEventEntity.class));
        return seen;
    }

    private String newId() {
        return newId("single");
    }

    private String newId(String label) {
        String id = "bootstrap-tx-" + label + "-" + UUID.randomUUID();
        ownedIds.add(id);
        return id;
    }

    private void seed(String id, RegistryOwner owner, boolean managed) {
        QuarkusTransaction.requiringNew().run(() -> {
            TargetEntity entity = mapper.toEntity(target(id, "Synthetic target", "master", managed ? BINDING : null));
            entity.registryOwner = owner;
            entity.installationManaged = managed;
            entity.installationRevision = managed ? 7 : 0;
            entity.createdAt = ORIGINAL_TIME;
            entity.updatedAt = ORIGINAL_TIME;
            targets.persistAndFlush(entity);
        });
    }

    private Target target(String id, String name, String realm, KubernetesInstallationBinding binding) {
        return new Target(TargetId.of(id), name, TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("https://synthetic-target.example.invalid", realm,
                        "synthetic-reader", "synthetic-keycloak-reference", "https://synthetic-management.example.invalid"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES,
                        "synthetic-cluster", "synthetic-namespace", "synthetic-infra-reference", binding),
                null, Map.of("synthetic-label", "synthetic-value"));
    }

    private TargetSnapshot snapshot(String id) {
        return QuarkusTransaction.requiringNew().call(() -> toSnapshot(targets.findById(id)));
    }

    private TargetSnapshot toSnapshot(TargetEntity entity) {
        if (entity == null) return null;
        return new TargetSnapshot(mapper.toDomain(entity), entity.registryOwner, entity.registryRevision,
                entity.installationRevision, entity.installationManaged, entity.createdAt, entity.updatedAt);
    }

    private long auditCount(String id) {
        return QuarkusTransaction.requiringNew().call(() -> audits.count("targetId", id));
    }

    private AuditSnapshot audit(String id, String operation) {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<AuditEventEntity> found = audits.list("targetId = ?1 and operation = ?2", id, operation);
            assertThat(found).hasSize(1);
            AuditEventEntity event = found.getFirst();
            return new AuditSnapshot(event.source, event.tool, event.status, event.params,
                    Collections.unmodifiableMap(new LinkedHashMap<>(event.metadata)));
        });
    }

    private void assertSafeAudit(AuditSnapshot event) {
        assertThat(event.source()).isEqualTo("SYSTEM");
        assertThat(event.tool()).isEqualTo("registry.bootstrap");
        assertThat(event.status()).isEqualTo("SUCCESS");
        assertThat(event.params()).isNullOrEmpty();
        assertThat(event.metadata()).containsOnlyKeys("origin", "actorKind", "owner", "previousRegistryRevision",
                "registryRevision", "previousInstallationRevision", "installationRevision", "bindingInvalidated", "changedFields");
        assertThat(event.metadata()).containsEntry("origin", "CONFIGURATION_BOOTSTRAP")
                .containsEntry("actorKind", "SYSTEM").containsEntry("owner", "CONFIGURATION");
        assertThat(event.metadata().toString()).doesNotContain("https://", "synthetic-reader",
                "synthetic-keycloak-reference", "synthetic-infra-reference", "synthetic-value", BINDING.uid());
    }

    private record TargetSnapshot(Target target, RegistryOwner owner, long registryRevision,
            long installationRevision, boolean managed, Instant createdAt, Instant updatedAt) {}

    private record AuditSnapshot(String source, String tool, String status,
            Map<String, Object> params, Map<String, Object> metadata) {}
}
