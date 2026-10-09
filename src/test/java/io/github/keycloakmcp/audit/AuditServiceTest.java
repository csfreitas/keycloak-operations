package io.github.keycloakmcp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import io.github.keycloakmcp.config.PlatformConfig;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.security.SensitiveDataFilter;

class AuditServiceTest {
    private static final String ACTOR = "a".repeat(64);
    private static final String CLIENT = "b".repeat(64);
    private static final String OBSERVATION = "10000000-0000-4000-8000-000000000001";
    private static final String TARGET = "lab-keycloak-a";
    private static final String SCOPE = "application-portal";
    private static final String INVALID_MESSAGE = "Invalid configuration audit metadata";
    private static final String CANARY = "synthetic-person@example.invalid";

    private final SensitiveDataFilter filter = mock(SensitiveDataFilter.class);
    private final AuditEventPersister persister = mock(AuditEventPersister.class);

    @ParameterizedTest
    @ValueSource(strings = {"METADATA", "SANITIZED", "FULL", "unknown"})
    void configurationAttributionSurvivesEveryAuditModeWithoutFactsOrParameters(String mode) {
        service(true, mode).recordConfigurationRead(AuditSource.REST, "configuration-read", TARGET, SCOPE,
                ACTOR, CLIENT, "SUCCESS", 17L, OBSERVATION);

        verify(persister).persist(AuditSource.REST, "keycloak_read_configuration", TARGET,
                "configuration-read", "SUCCESS", 17L, OBSERVATION, null,
                Map.of("schemaVersion", "1.0", "actorFingerprint", ACTOR,
                        "clientFingerprint", CLIENT, "scopeId", SCOPE));
        verifyNoInteractions(filter);
    }

    @Test
    void scopeListingHasOnlyCallerFingerprintsAndGeneratesAnIndependentCorrelationId() {
        service(true, "METADATA").recordConfigurationRead(AuditSource.MCP, "configuration-scopes", null, null,
                ACTOR, CLIENT, "SUCCESS", 0L, null);

        ArgumentCaptor<String> trace = ArgumentCaptor.forClass(String.class);
        verify(persister).persist(eq(AuditSource.MCP), eq("keycloak_list_configuration_scopes"), isNull(),
                eq("configuration-scopes"), eq("SUCCESS"), eq(0L), trace.capture(), isNull(),
                eq(Map.of("schemaVersion", "1.0", "actorFingerprint", ACTOR, "clientFingerprint", CLIENT)));
        assertThat(UUID.fromString(trace.getValue()).toString()).isEqualTo(trace.getValue());
        verifyNoInteractions(filter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILURE", "DENIED", "UNAVAILABLE", "COMPLETE", "PARTIAL"})
    void failedOrDeniedReadsDoNotNeedAnUnvalidatedAttemptedScope(String status) {
        service(true, "METADATA").recordConfigurationRead(AuditSource.MCP, "configuration-read", null, null,
                ACTOR, CLIENT, status, 1L, OBSERVATION);

        verify(persister).persist(AuditSource.MCP, "keycloak_read_configuration", null,
                "configuration-read", status, 1L, OBSERVATION, null,
                Map.of("schemaVersion", "1.0", "actorFingerprint", ACTOR, "clientFingerprint", CLIENT));
    }

    @ParameterizedTest
    @EnumSource(value = AuditSource.class, names = {"REST", "MCP", "WEB"})
    void explicitInteractiveSourceIsPreserved(AuditSource source) {
        service(true, "METADATA").recordConfigurationRead(source, "configuration-read", TARGET, SCOPE,
                ACTOR, CLIENT, "SUCCESS", 1L, OBSERVATION);
        verify(persister).persist(source, "keycloak_read_configuration", TARGET, "configuration-read", "SUCCESS",
                1L, OBSERVATION, null, Map.of("schemaVersion", "1.0", "actorFingerprint", ACTOR,
                        "clientFingerprint", CLIENT, "scopeId", SCOPE));
    }

    @Test
    void disabledAuditDoesNotPersistOrProjectAnyConfigurationReadData() {
        service(false, "METADATA").recordConfigurationRead(AuditSource.REST, "configuration-read", TARGET, SCOPE,
                ACTOR, CLIENT, "SUCCESS", 0L, OBSERVATION);
        verifyNoInteractions(persister, filter);
    }

    @ParameterizedTest
    @MethodSource("invalidFingerprints")
    void malformedFingerprintsFailClosedWithoutEchoingPrincipalOrProviderValues(String invalid) {
        AuditService service = service(true, "METADATA");
        assertSafeFailure(() -> service.recordConfigurationRead(AuditSource.REST, "configuration-read", TARGET,
                SCOPE, invalid, CLIENT, "SUCCESS", 0L, OBSERVATION));
        assertSafeFailure(() -> service.recordConfigurationRead(AuditSource.REST, "configuration-read", TARGET,
                SCOPE, ACTOR, invalid, "SUCCESS", 0L, OBSERVATION));
        verifyNoInteractions(persister, filter);
    }

    @ParameterizedTest
    @MethodSource("invalidMetadata")
    void malformedEnvelopeFieldsCannotBecomeAuditLogOrDatabaseContent(
            AuditSource source, String operation, String target, String scope, String status,
            long duration, String observation) {
        AuditService service = service(true, "METADATA");
        assertSafeFailure(() -> service.recordConfigurationRead(source, operation, target, scope,
                ACTOR, CLIENT, status, duration, observation));
        verifyNoInteractions(persister, filter);
    }

    @Test
    void legacyMetadataModeContractIsUnchanged() {
        service(true, "METADATA").record(AuditSource.REST, "legacy-tool", TARGET,
                "legacy-read", "SUCCESS", 5L, Map.of("description", CANARY), OBSERVATION);
        verify(persister).persist(AuditSource.REST, "legacy-tool", TARGET, "legacy-read", "SUCCESS", 5L,
                OBSERVATION, null, Map.of("mode", "METADATA"));
        verifyNoInteractions(filter);
    }

    private AuditService service(boolean enabled, String mode) {
        PlatformConfig config = mock(PlatformConfig.class);
        PlatformConfig.Audit audit = mock(PlatformConfig.Audit.class);
        when(config.audit()).thenReturn(audit);
        when(audit.enabled()).thenReturn(enabled);
        when(audit.mode()).thenReturn(mode);
        return new AuditService(filter, config, persister);
    }

    private static void assertSafeFailure(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(INVALID_MESSAGE).hasNoCause();
    }

    private static Stream<String> invalidFingerprints() {
        return Stream.of(null, "", CANARY, "a".repeat(63), "a".repeat(65),
                "A".repeat(64), "g".repeat(64), ACTOR + "\n");
    }

    private static Stream<Arguments> invalidMetadata() {
        return Stream.of(
                Arguments.of(null, "configuration-read", TARGET, SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.SYSTEM, "configuration-read", TARGET, SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.SCHEDULED, "configuration-read", TARGET, SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, null, TARGET, SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, CANARY, TARGET, SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", CANARY, SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", "x".repeat(129), SCOPE, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, CANARY, "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, "x".repeat(129), "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, "scope\n", "SUCCESS", 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, SCOPE, null, 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, SCOPE, CANARY, 0L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, SCOPE, "SUCCESS", -1L, OBSERVATION),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, SCOPE, "SUCCESS", 0L, CANARY),
                Arguments.of(AuditSource.REST, "configuration-read", TARGET, SCOPE, "SUCCESS", 0L, ""));
    }
}
