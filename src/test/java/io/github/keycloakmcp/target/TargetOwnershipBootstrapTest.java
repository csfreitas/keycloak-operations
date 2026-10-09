package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import jakarta.persistence.LockModeType;

/** Pure interaction tests. ORM revision increments and transactional rollback require integration tests. */
class TargetOwnershipBootstrapTest {
    private static final String ID = "ownership-target";
    private static final Instant CREATED = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2025-02-01T00:00:00Z");
    private static final KubernetesInstallationBinding CONFIRMED =
            new KubernetesInstallationBinding("apps/v1", "Deployment", "selected", "confirmed-uid");
    private static final String OWNERSHIP_ERROR = "Configuration target ownership conflict; explicit adoption is required";

    private final ConfigurationTargetRegistry configured = mock(ConfigurationTargetRegistry.class);
    private final TargetRepository repository = mock(TargetRepository.class);
    private final TargetPersistenceMapper mapper = spy(new TargetPersistenceMapper());
    private final AuditRepository audits = mock(AuditRepository.class);
    private final TargetBootstrapService service = new TargetBootstrapService(configured, repository, mapper, audits);

    @BeforeEach
    void emptyConfiguration() {
        when(configured.list()).thenReturn(List.of());
    }

    @Test
    void emptyConfigurationDoesNotDeleteInspectOrAuditDatabaseRecords() {
        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        verifyNoInteractions(repository, audits, mapper);
    }

    @Test
    void newRecordHasExplicitConfigurationOwnershipAndMandatoryCreationAudit() {
        Target desired = target(ID);
        when(configured.list()).thenReturn(List.of(desired));

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);

        var entityCaptor = ArgumentCaptor.forClass(TargetEntity.class);
        var auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        var ordered = inOrder(repository, audits);
        ordered.verify(repository).findById(ID, LockModeType.PESSIMISTIC_WRITE);
        ordered.verify(repository).persist(entityCaptor.capture());
        ordered.verify(repository).flush();
        ordered.verify(audits).persistAndFlush(auditCaptor.capture());
        TargetEntity created = entityCaptor.getValue();
        assertThat(created.registryOwner).isEqualTo(RegistryOwner.CONFIGURATION);
        assertThat(mapper.toDomain(created)).isEqualTo(desired);
        assertThat(created.createdAt).isNotNull();
        assertAuditEnvelope(auditCaptor.getValue(), "TARGET_CONFIG_CREATED");
        assertThat(auditCaptor.getValue().metadata).containsEntry("previousRegistryRevision", null)
                .containsEntry("registryRevision", created.registryRevision)
                .containsEntry("bindingInvalidated", false);
    }

    @Test
    void aSecondIdenticalBootstrapIsANoopWithoutTimestampRevisionOrAuditChurn() {
        Target desired = target(ID);
        TargetEntity existing = configurationEntity(desired);
        bind(desired, existing);

        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(service.syncConfigTargetsToDatabase()).isZero();

        assertThat(existing.createdAt).isEqualTo(CREATED);
        assertThat(existing.updatedAt).isEqualTo(UPDATED);
        assertThat(existing.registryRevision).isEqualTo(7L);
        assertThat(existing.installationRevision).isEqualTo(3L);
        verify(repository, times(2)).findById(ID, LockModeType.PESSIMISTIC_WRITE);
        verify(repository, never()).persist(any(TargetEntity.class));
        verify(repository, never()).flush();
        verify(mapper, never()).updateEntity(any(Target.class), any(TargetEntity.class));
        verifyNoInteractions(audits);
    }

    @Test
    void mapInsertionOrderDoesNotCreateAnEffectiveConfigurationChange() {
        var firstTags = new LinkedHashMap<String, String>();
        firstTags.put("team", "platform"); firstTags.put("region", "south");
        var otherTags = new LinkedHashMap<String, String>();
        otherTags.put("region", "south"); otherTags.put("team", "platform");
        Target before = withTags(target(ID), firstTags);
        TargetEntity existing = configurationEntity(before);
        bind(withTags(target(ID), otherTags), existing);

        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(existing.updatedAt).isEqualTo(UPDATED);
        verify(repository, never()).flush();
        verifyNoInteractions(audits);
    }

    @Test
    void persistedManagementUrlProjectionDoesNotCauseAnIdenticalRestartToMutate() {
        Target base = target(ID);
        Target desired = withKeycloak(base, new KeycloakTargetConfiguration(base.keycloak().url(), "master",
                "reader", "keycloak-reference", "https://health.example.invalid/management"));
        TargetEntity existing = configurationEntity(desired);
        assertThat(existing.tags).containsEntry("management-url", "https://health.example.invalid/management");
        bind(desired, existing);

        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(existing.updatedAt).isEqualTo(UPDATED);
        verifyNoInteractions(audits);
    }

    @ParameterizedTest
    @MethodSource("nonConfigurationOwners")
    void unknownOrLegacyOwnershipCannotBeAdoptedEvenForIdenticalConfiguration(RegistryOwner owner) {
        Target desired = target(ID);
        TargetEntity existing = configurationEntity(desired);
        existing.registryOwner = owner;
        Target before = mapper.toDomain(existing);
        bind(desired, existing);

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isInstanceOf(IllegalStateException.class)
                .hasMessage(OWNERSHIP_ERROR).hasNoCause();

        assertThat(existing.registryOwner).isEqualTo(owner);
        assertThat(mapper.toDomain(existing)).isEqualTo(before);
        assertThat(existing.createdAt).isEqualTo(CREATED);
        assertThat(existing.updatedAt).isEqualTo(UPDATED);
        assertThat(existing.registryRevision).isEqualTo(7L);
        assertThat(existing.installationRevision).isEqualTo(3L);
        verify(mapper, never()).updateEntity(any(Target.class), any(TargetEntity.class));
        verify(repository, never()).persist(any(TargetEntity.class));
        verify(repository, never()).flush();
        verifyNoInteractions(audits);
    }

    @Test
    void conflictingLegacyDisabledRecordCannotBeEnabledOrOverwrittenByConfiguration() {
        Target desired = target(ID);
        TargetEntity existing = configurationEntity(withEnabled(desired, false));
        existing.registryOwner = RegistryOwner.LEGACY_UNCLASSIFIED;
        existing.displayName = "legacy-private-name";
        existing.keycloakUrl = "https://legacy-private.example.invalid";
        bind(desired, existing);

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isInstanceOf(IllegalStateException.class)
                .hasMessage(OWNERSHIP_ERROR).hasNoCause();

        assertThat(existing.enabled).isFalse();
        assertThat(existing.displayName).isEqualTo("legacy-private-name");
        assertThat(existing.keycloakUrl).isEqualTo("https://legacy-private.example.invalid");
        verifyNoInteractions(audits);
    }

    @Test
    void confirmedBindingWinsOverAbsentOrDifferentConfiguredBindingForTheSameConnection() {
        Target before = withBinding(target(ID), CONFIRMED);
        TargetEntity existing = configurationEntity(before);
        existing.installationManaged = true;
        Target configuredBinding = withBinding(target(ID), new KubernetesInstallationBinding(
                "apps/v1", "StatefulSet", "unselected", "unselected-uid"));
        bind(configuredBinding, existing);

        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        when(configured.list()).thenReturn(List.of(target(ID)));
        assertThat(service.syncConfigTargetsToDatabase()).isZero();

        assertThat(mapper.toDomain(existing).infrastructure().installation()).isEqualTo(CONFIRMED);
        assertThat(existing.installationManaged).isTrue();
        assertThat(existing.installationRevision).isEqualTo(3L);
        assertThat(existing.updatedAt).isEqualTo(UPDATED);
        verifyNoInteractions(audits);
    }

    @ParameterizedTest(name = "ambiguous configured binding: {0}")
    @MethodSource("ambiguousConfiguredBindings")
    void connectionChangeWithExplicitBindingRejectsEverySyncWithoutMutatingManagedState(
            String label, KubernetesInstallationBinding configuredBinding) {
        Target before = withBinding(target(ID), CONFIRMED);
        TargetEntity existing = configurationEntity(before);
        existing.installationManaged = true;
        Target desired = withInfrastructure(target(ID), new InfrastructureTargetConfiguration(
                InfrastructureType.KUBERNETES, "cluster-a", "changed-namespace", "cluster-reference", configuredBinding));
        bind(desired, existing);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(service::syncConfigTargetsToDatabase).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Configuration cannot replace a managed binding while changing its connection; remove the configured binding first")
                    .hasNoCause();

            assertThat(mapper.toDomain(existing)).isEqualTo(before);
            assertThat(existing.registryOwner).isEqualTo(RegistryOwner.CONFIGURATION);
            assertThat(existing.registryRevision).isEqualTo(7L);
            assertThat(existing.installationRevision).isEqualTo(3L);
            assertThat(existing.installationManaged).isTrue();
            assertThat(existing.createdAt).isEqualTo(CREATED);
            assertThat(existing.updatedAt).isEqualTo(UPDATED);
            verify(mapper, never()).updateEntity(any(Target.class), any(TargetEntity.class));
            verify(repository, never()).persist(any(TargetEntity.class));
            verify(repository, never()).flush();
            verifyNoInteractions(audits);
        }
        verify(repository, times(2)).findById(ID, LockModeType.PESSIMISTIC_WRITE);
    }

    @ParameterizedTest(name = "metadata change: {0}")
    @MethodSource("metadataChanges")
    void metadataChangesPreserveManagedBindingAndItsRevision(String label, UnaryOperator<Target> change) {
        Target before = withBinding(target(ID), CONFIRMED);
        TargetEntity existing = configurationEntity(before);
        existing.installationManaged = true;
        bind(change.apply(target(ID)), existing);

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);

        assertThat(mapper.toDomain(existing).infrastructure().installation()).isEqualTo(CONFIRMED);
        assertThat(existing.installationManaged).isTrue();
        assertThat(existing.installationRevision).isEqualTo(3L);
        assertThat(existing.registryOwner).isEqualTo(RegistryOwner.CONFIGURATION);
        assertThat(existing.createdAt).isEqualTo(CREATED);
        var auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(audits).persistAndFlush(auditCaptor.capture());
        assertAuditEnvelope(auditCaptor.getValue(), "TARGET_CONFIG_UPDATED");
        assertThat(auditCaptor.getValue().metadata).containsEntry("bindingInvalidated", false)
                .containsEntry("previousInstallationRevision", 3L).containsEntry("installationRevision", 3L);
    }

    @ParameterizedTest(name = "connection change: {0}")
    @MethodSource("connectionChanges")
    void everyRelevantConnectionChangeInvalidatesManagedBindingOnce(String label, UnaryOperator<Target> change) {
        TargetEntity existing = configurationEntity(withBinding(target(ID), CONFIRMED));
        existing.installationManaged = true;
        bind(change.apply(target(ID)), existing);

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);

        assertThat(existing.installationApiVersion).isNull();
        assertThat(existing.installationKind).isNull();
        assertThat(existing.installationName).isNull();
        assertThat(existing.installationUid).isNull();
        assertThat(existing.installationManaged).isFalse();
        assertThat(existing.installationRevision).isEqualTo(4L);
        assertThat(existing.registryOwner).isEqualTo(RegistryOwner.CONFIGURATION);
        var auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(audits).persistAndFlush(auditCaptor.capture());
        assertAuditEnvelope(auditCaptor.getValue(), "TARGET_CONFIG_UPDATED");
        assertThat(auditCaptor.getValue().metadata).containsEntry("bindingInvalidated", true)
                .containsEntry("previousInstallationRevision", 3L).containsEntry("installationRevision", 4L);
        assertThat(service.syncConfigTargetsToDatabase()).isZero();
        assertThat(existing.installationRevision).isEqualTo(4L);
        verify(audits).persistAndFlush(any(AuditEventEntity.class));
    }

    @Test
    void changedConfiguredBindingAdvancesItsRevisionWithoutInventingManagedOwnership() {
        TargetEntity existing = configurationEntity(withBinding(target(ID), CONFIRMED));
        var replacement = new KubernetesInstallationBinding("apps/v1", "Deployment", "selected", "replacement-uid");
        bind(withBinding(target(ID), replacement), existing);

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);

        assertThat(mapper.toDomain(existing).infrastructure().installation()).isEqualTo(replacement);
        assertThat(existing.installationManaged).isFalse();
        assertThat(existing.installationRevision).isEqualTo(4L);
        verify(audits).persistAndFlush(any(AuditEventEntity.class));
    }

    @Test
    void auditUsesObservedPostFlushVersionRatherThanManuallyIncrementingOrmVersion() {
        TargetEntity existing = configurationEntity(target(ID));
        Target desired = withName(target(ID), "changed-private-display-name");
        bind(desired, existing);
        doAnswer(invocation -> {
            // Simulate a version supplied by persistence; this is not evidence of actual ORM locking.
            assertThat(existing.registryRevision).isEqualTo(7L);
            existing.registryRevision = 8L;
            return null;
        }).when(repository).flush();

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(1);

        var auditCaptor = ArgumentCaptor.forClass(AuditEventEntity.class);
        var ordered = inOrder(repository, audits);
        ordered.verify(repository).flush();
        ordered.verify(audits).persistAndFlush(auditCaptor.capture());
        assertThat(auditCaptor.getValue().metadata).containsEntry("previousRegistryRevision", 7L)
                .containsEntry("registryRevision", 8L);
        assertThat(auditCaptor.getValue().metadata.toString()).doesNotContain("changed-private-display-name",
                "https://sso.example.invalid", "keycloak-reference", "cluster-reference");
    }

    @Test
    void failureToFlushTheTargetPreventsAnySuccessAudit() {
        TargetEntity existing = configurationEntity(target(ID));
        bind(withName(target(ID), "changed"), existing);
        RuntimeException failure = new IllegalStateException("synthetic flush failure");
        doThrow(failure).when(repository).flush();

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isSameAs(failure);
        verifyNoInteractions(audits);
        // No rollback assertion: the transactional interceptor is absent in this pure test.
    }

    @Test
    void mandatoryAuditFailurePropagatesToTheTransactionalCaller() {
        TargetEntity existing = configurationEntity(target(ID));
        bind(withName(target(ID), "changed"), existing);
        RuntimeException failure = new IllegalStateException("synthetic audit failure");
        doThrow(failure).when(audits).persistAndFlush(any(AuditEventEntity.class));

        assertThatThrownBy(service::syncConfigTargetsToDatabase).isSameAs(failure);
        verify(repository).flush();
        // Propagation is checked here; the root's database tests establish atomic rollback.
    }

    @Test
    void targetsAreLockedInStableIdOrderAndCountIncludesOnlyEffectiveChanges() {
        Target alpha = target("a-target"), zeta = target("z-target"), unchanged = target("m-target");
        TargetEntity existing = configurationEntity(unchanged);
        when(configured.list()).thenReturn(List.of(zeta, unchanged, alpha));
        when(repository.findById("m-target", LockModeType.PESSIMISTIC_WRITE)).thenReturn(existing);

        assertThat(service.syncConfigTargetsToDatabase()).isEqualTo(2);

        var order = inOrder(repository);
        order.verify(repository).findById("a-target", LockModeType.PESSIMISTIC_WRITE);
        order.verify(repository).findById("m-target", LockModeType.PESSIMISTIC_WRITE);
        order.verify(repository).findById("z-target", LockModeType.PESSIMISTIC_WRITE);
        verify(repository, times(2)).persist(any(TargetEntity.class));
        verify(audits, times(2)).persistAndFlush(any(AuditEventEntity.class));
    }

    private void bind(Target desired, TargetEntity existing) {
        when(configured.list()).thenReturn(List.of(desired));
        when(repository.findById(desired.id().value(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(existing);
    }

    private TargetEntity configurationEntity(Target target) {
        TargetEntity entity = mapper.toEntity(target);
        entity.registryOwner = RegistryOwner.CONFIGURATION;
        entity.registryRevision = 7;
        entity.installationRevision = 3;
        entity.createdAt = CREATED;
        entity.updatedAt = UPDATED;
        return entity;
    }

    private static void assertAuditEnvelope(AuditEventEntity audit, String operation) {
        assertThat(audit.source).isEqualTo("SYSTEM");
        assertThat(audit.tool).isEqualTo("registry.bootstrap");
        assertThat(audit.operation).isEqualTo(operation);
        assertThat(audit.status).isEqualTo("SUCCESS");
        assertThat(audit.id).isNotBlank();
        assertThat(audit.createdAt).isNotNull();
        assertThat(audit.params == null || audit.params.isEmpty()).isTrue();
        assertThat(audit.metadata).containsOnlyKeys("origin", "actorKind", "owner", "previousRegistryRevision",
                "registryRevision", "previousInstallationRevision", "installationRevision", "bindingInvalidated", "changedFields")
                .containsEntry("origin", "CONFIGURATION_BOOTSTRAP")
                .containsEntry("actorKind", "SYSTEM").containsEntry("owner", "CONFIGURATION");
        assertThat(audit.metadata.get("changedFields")).isInstanceOf(List.class);
        var fields = (List<?>) audit.metadata.get("changedFields");
        assertThat(fields).isNotEmpty();
        assertThat(fields).allSatisfy(field -> assertThat(field).isInstanceOfSatisfying(String.class,
                name -> assertThat(name).matches("[A-Za-z][A-Za-z0-9.]*")));
        assertThat(audit.metadata.toString()).doesNotContain("https://", "keycloak-reference", "cluster-reference",
                "confirmed-uid", "replacement-uid", "sso.example.invalid");
    }

    static Stream<RegistryOwner> nonConfigurationOwners() {
        return Stream.of(RegistryOwner.LEGACY_UNCLASSIFIED, null);
    }

    static Stream<Arguments> ambiguousConfiguredBindings() {
        return Stream.of(
                Arguments.of("same UID", CONFIRMED),
                Arguments.of("different UID", new KubernetesInstallationBinding(
                        "apps/v1", "Deployment", "selected", "replacement-uid")));
    }

    static Stream<Arguments> metadataChanges() {
        return Stream.of(
                Arguments.of("displayName", (UnaryOperator<Target>) t -> withName(t, "Changed display")),
                Arguments.of("enabled", (UnaryOperator<Target>) t -> withEnabled(t, false)),
                Arguments.of("tags", (UnaryOperator<Target>) t -> withTags(t, Map.of("team", "updated"))),
                Arguments.of("productType", (UnaryOperator<Target>) t -> new Target(t.id(), t.displayName(), TargetType.KEYCLOAK,
                        t.environment(), t.enabled(), t.keycloak(), t.infrastructure(), t.observability(), t.tags())),
                Arguments.of("environment", (UnaryOperator<Target>) t -> new Target(t.id(), t.displayName(), t.type(),
                        TargetEnvironment.PRD, t.enabled(), t.keycloak(), t.infrastructure(), t.observability(), t.tags())));
    }

    static Stream<Arguments> connectionChanges() {
        List<Arguments> cases = new ArrayList<>();
        cases.add(Arguments.of("Keycloak URL", (UnaryOperator<Target>) t -> withKeycloak(t,
                new KeycloakTargetConfiguration("https://changed.example.invalid", "master", "reader", "keycloak-reference"))));
        cases.add(Arguments.of("auth realm", (UnaryOperator<Target>) t -> withKeycloak(t,
                new KeycloakTargetConfiguration(t.keycloak().url(), "another-realm", "reader", "keycloak-reference"))));
        cases.add(Arguments.of("client ID", (UnaryOperator<Target>) t -> withKeycloak(t,
                new KeycloakTargetConfiguration(t.keycloak().url(), "master", "changed-client", "keycloak-reference"))));
        cases.add(Arguments.of("Keycloak credential reference", (UnaryOperator<Target>) t -> withKeycloak(t,
                new KeycloakTargetConfiguration(t.keycloak().url(), "master", "reader", "changed-reference"))));
        cases.add(Arguments.of("management URL", (UnaryOperator<Target>) t -> withKeycloak(t,
                new KeycloakTargetConfiguration(t.keycloak().url(), "master", "reader", "keycloak-reference", "https://health.example.invalid"))));
        cases.add(Arguments.of("infrastructure type", (UnaryOperator<Target>) t -> withInfrastructure(t,
                new InfrastructureTargetConfiguration(InfrastructureType.OPENSHIFT, "cluster-a", "iam", "cluster-reference"))));
        cases.add(Arguments.of("cluster identity", (UnaryOperator<Target>) t -> withInfrastructure(t,
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster-b", "iam", "cluster-reference"))));
        cases.add(Arguments.of("namespace", (UnaryOperator<Target>) t -> withInfrastructure(t,
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster-a", "another-namespace", "cluster-reference"))));
        cases.add(Arguments.of("cluster credential reference", (UnaryOperator<Target>) t -> withInfrastructure(t,
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster-a", "iam", "changed-reference"))));
        cases.add(Arguments.of("removed infrastructure", (UnaryOperator<Target>) t -> withInfrastructure(t, null)));
        return cases.stream();
    }

    private static Target target(String id) {
        return new Target(TargetId.of(id), "Original", TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("https://sso.example.invalid", "master", "reader", "keycloak-reference"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster-a", "iam", "cluster-reference"),
                null, Map.of("team", "platform"));
    }

    private static Target withKeycloak(Target t, KeycloakTargetConfiguration keycloak) {
        return new Target(t.id(), t.displayName(), t.type(), t.environment(), t.enabled(), keycloak, t.infrastructure(), t.observability(), t.tags());
    }

    private static Target withInfrastructure(Target t, InfrastructureTargetConfiguration infrastructure) {
        return new Target(t.id(), t.displayName(), t.type(), t.environment(), t.enabled(), t.keycloak(), infrastructure, t.observability(), t.tags());
    }

    private static Target withBinding(Target t, KubernetesInstallationBinding binding) {
        var i = t.infrastructure();
        return withInfrastructure(t, new InfrastructureTargetConfiguration(i.type(), i.clusterId(), i.namespace(), i.credentialRef(), binding));
    }

    private static Target withName(Target t, String name) {
        return new Target(t.id(), name, t.type(), t.environment(), t.enabled(), t.keycloak(), t.infrastructure(), t.observability(), t.tags());
    }

    private static Target withEnabled(Target t, boolean enabled) {
        return new Target(t.id(), t.displayName(), t.type(), t.environment(), enabled, t.keycloak(), t.infrastructure(), t.observability(), t.tags());
    }

    private static Target withTags(Target t, Map<String, String> tags) {
        return new Target(t.id(), t.displayName(), t.type(), t.environment(), t.enabled(), t.keycloak(), t.infrastructure(), t.observability(), tags);
    }
}
