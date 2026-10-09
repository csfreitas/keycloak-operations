package io.github.keycloakmcp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.config.PlatformConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.AuditQueryService;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class AuditMetadataBoundaryTest {
    private static final String CANARY = "audit-synthetic-canary";
    // Valid configured identifier, deliberately matching a credential text pattern.
    private static final String TARGET_ID = "eyJtarget.payload.signature";
    private static final String TRACE_ID = "eyJtrace.payload.signature";

    @Inject
    PlatformPersistenceMapper mapper;

    @Inject
    SensitiveDataFilter filter;

    @Inject
    ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(strings = {"SANITIZED", "FULL", "invalid", ""})
    void enabledAuditRedactsNestedParamsWithoutChangingFactsOrInput(String mode) throws Exception {
        var persister = mock(AuditEventPersister.class);
        var service = service(true, mode, persister);
        Map<String, Object> input = metadata();
        String before = objectMapper.writeValueAsString(input);

        service.record(AuditSource.MCP, "tool", TARGET_ID, "operation", "SUCCESS", 17L, input, TRACE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(persister).persist(eq(AuditSource.MCP), eq("tool"), eq(TARGET_ID), eq("operation"),
                eq("SUCCESS"), eq(17L), eq(TRACE_ID), params.capture(), any());
        assertMetadataSafe(params.getValue());
        assertThat(objectMapper.writeValueAsString(input).equals(before)).as("input is not modified").isTrue();
    }

    @Test
    void metadataModeOmitsParamsAndDisabledAuditDoesNotPersist() {
        var persister = mock(AuditEventPersister.class);
        service(true, "METADATA", persister).record(
                AuditSource.REST, "tool", TARGET_ID, "read", "SUCCESS", 0L, metadata(), TRACE_ID);
        verify(persister).persist(AuditSource.REST, "tool", TARGET_ID, "read", "SUCCESS", 0L,
                TRACE_ID, null, Map.of("mode", "METADATA"));
        var disabledPersister = mock(AuditEventPersister.class);
        service(false, "SANITIZED", disabledPersister).record(
                AuditSource.REST, "tool", TARGET_ID, "read", "SUCCESS", 0L, metadata(), TRACE_ID);
        verifyNoInteractions(disabledPersister);
    }

    @Test
    void nullParamsRemainAbsent() {
        var persister = mock(AuditEventPersister.class);
        service(true, "SANITIZED", persister).record(
                AuditSource.REST, "tool", TARGET_ID, "read", "SUCCESS", 0L, null, TRACE_ID);
        verify(persister).persist(AuditSource.REST, "tool", TARGET_ID, "read", "SUCCESS", 0L,
                TRACE_ID, null, Map.of("mode", "SANITIZED"));
    }

    @Test
    void logProjectionDoesNotRewritePersistedTargetOrTraceIdentity() {
        var persister = mock(AuditEventPersister.class);
        try (var logs = capture(AuditService.class)) {
            service(true, "SANITIZED", persister).logToolInvocation(
                    TRACE_ID, "tool", TARGET_ID, "master", 1L, true);
            assertThat(logs.records).hasSize(1);
        }
        verify(persister).persist(AuditSource.MCP, "tool", TARGET_ID, "tool", "SUCCESS", 1L,
                TRACE_ID, Map.of("realm", "master"), Map.of("mode", "SANITIZED"));
    }

    @Test
    void requestIdAndRealmUseOnlySafeLogProjections() {
        try (var logs = capture(AuditService.class)) {
            service(false, "SANITIZED", mock(AuditEventPersister.class)).logToolInvocation(
                    "token=" + CANARY, "tool", "lab", "Authorization: Bearer " + CANARY, 1L, false);
            assertSafeLogs(logs.records);
        }
    }

    @Test
    void newAuditEventFiltersPayloadsAndPreservesCanonicalFields() throws Exception {
        var params = metadata();
        var metadata = metadata();
        String paramsBefore = objectMapper.writeValueAsString(params);
        String metadataBefore = objectMapper.writeValueAsString(metadata);
        var event = mapper.newAuditEvent(AuditSource.REST, "tool", TARGET_ID, "read", "SUCCESS", 7L,
                TRACE_ID, params, metadata);

        assertMetadataSafe(event.params);
        assertMetadataSafe(event.metadata);
        assertThat(event.targetId).isEqualTo(TARGET_ID);
        assertThat(event.traceId).isEqualTo(TRACE_ID);
        assertThat(event.source).isEqualTo("REST");
        assertThat(event.operation).isEqualTo("read");
        assertThat(event.status).isEqualTo("SUCCESS");
        assertThat(event.durationMs).isEqualTo(7L);
        assertThat(objectMapper.writeValueAsString(params).equals(paramsBefore)).isTrue();
        assertThat(objectMapper.writeValueAsString(metadata).equals(metadataBefore)).isTrue();
    }

    @Test
    void newAuditEventPreservesNullPayloadsAndDefaultStatus() {
        var event = mapper.newAuditEvent(null, null, TARGET_ID, null, null, null, TRACE_ID, null, null);
        assertThat(event.params).isNull();
        assertThat(event.metadata).isNull();
        assertThat(event.source).isEqualTo("SYSTEM");
        assertThat(event.status).isEqualTo("UNKNOWN");
    }

    @Test
    void historicalReadFiltersPayloadWithoutRewritingTheRowOrCorrelation() throws Exception {
        var event = historicalEvent();
        String before = objectMapper.writeValueAsString(event.metadata);

        var summary = mapper.toAuditSummary(event);

        assertMetadataSafe(summary.metadata());
        assertThat(summary.targetId()).isEqualTo(TARGET_ID);
        assertThat(summary.traceId()).isEqualTo(TRACE_ID);
        assertThat(summary.id()).isEqualTo("audit-id");
        assertThat(summary.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(objectMapper.writeValueAsString(event.metadata).equals(before)).isTrue();
    }

    @Test
    void persistenceFailureIsBestEffortWithFixedCauseFreeDiagnostics() {
        var repository = mock(AuditRepository.class);
        doThrow(new IllegalStateException("db payload password=" + CANARY))
                .when(repository).persist(any(AuditEventEntity.class));
        var persister = new AuditEventPersister(repository, mapper);
        try (var logs = capture(AuditEventPersister.class)) {
            persister.persist(AuditSource.SYSTEM, "tool token=" + CANARY,
                    "target token=" + CANARY, "read", "FAILURE", 0L, TRACE_ID, Map.of(), Map.of());
            assertSafeLogs(logs.records);
        }
        verify(repository).persist(any(AuditEventEntity.class));
    }

    @Test
    void queryAuthorizesBeforeDatabasePagingAndSanitizesHistoricalMetadata() throws Exception {
        var repository = mock(AuditRepository.class);
        var authorization = mock(TargetAuthorizationService.class);
        var registry = mock(TargetRegistry.class);
        var resolver = mock(TargetResolver.class);
        var target = target(TARGET_ID);
        var event = historicalEvent();
        when(resolver.require(TARGET_ID)).thenReturn(target);
        when(repository.listForTargets(Set.of(TARGET_ID), Optional.empty(), 3, 7))
                .thenReturn(new PageResult<>(List.of(event), 3, 7, 29));

        var result = new AuditQueryService(repository, mapper, filter, authorization, registry, resolver)
                .list(Optional.of(TARGET_ID), Optional.empty(), 3, 7);

        var order = inOrder(authorization, resolver, repository);
        order.verify(authorization).assertSession();
        order.verify(resolver).require(TARGET_ID);
        order.verify(authorization).assertAllowed(target, TargetPermission.READ);
        order.verify(repository).listForTargets(Set.of(TARGET_ID), Optional.empty(), 3, 7);
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(7);
        assertThat(result.total()).isEqualTo(29);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().targetId()).isEqualTo(TARGET_ID);
        assertMetadataSafe(result.items().getFirst().metadata());
        verifyNoInteractions(registry);
    }

    @Test
    void deniedSessionCannotResolveOrReadAuditRows() {
        var repository = mock(AuditRepository.class);
        var authorization = mock(TargetAuthorizationService.class);
        var registry = mock(TargetRegistry.class);
        var resolver = mock(TargetResolver.class);
        doThrow(McpException.authenticationFailed("authentication required")).when(authorization).assertSession();
        assertThatThrownBy(() -> new AuditQueryService(repository, mapper, filter, authorization, registry, resolver)
                .list(Optional.of(TARGET_ID), Optional.empty(), 0, 20)).isInstanceOf(McpException.class);
        verifyNoInteractions(repository, registry, resolver);
    }

    @Test
    void deniedTargetCannotReadAuditRows() {
        var repository = mock(AuditRepository.class);
        var authorization = mock(TargetAuthorizationService.class);
        var registry = mock(TargetRegistry.class);
        var resolver = mock(TargetResolver.class);
        var target = target(TARGET_ID);
        when(resolver.require(TARGET_ID)).thenReturn(target);
        doThrow(McpException.targetUnauthorized(TARGET_ID)).when(authorization)
                .assertAllowed(target, TargetPermission.READ);
        assertThatThrownBy(() -> new AuditQueryService(repository, mapper, filter, authorization, registry, resolver)
                .list(Optional.of(TARGET_ID), Optional.empty(), 0, 20)).isInstanceOf(McpException.class);
        verifyNoInteractions(repository, registry);
    }

    @Test
    void unfilteredQueryScopesOnlyAuthorizedTargetsBeforeDatabasePaging() {
        var repository = mock(AuditRepository.class);
        var authorization = mock(TargetAuthorizationService.class);
        var registry = mock(TargetRegistry.class);
        var resolver = mock(TargetResolver.class);
        var allowed = target(TARGET_ID);
        var denied = target("other-target");
        when(registry.list()).thenReturn(List.of(allowed, denied));
        when(authorization.isAllowed(allowed, TargetPermission.READ)).thenReturn(true);
        when(repository.listForTargets(Set.of(TARGET_ID), Optional.empty(), 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0));
        new AuditQueryService(repository, mapper, filter, authorization, registry, resolver)
                .list(Optional.empty(), Optional.empty(), 0, 20);
        var order = inOrder(authorization, registry, repository);
        order.verify(authorization).assertSession();
        order.verify(registry).list();
        order.verify(authorization).isAllowed(allowed, TargetPermission.READ);
        order.verify(authorization).isAllowed(denied, TargetPermission.READ);
        order.verify(repository).listForTargets(Set.of(TARGET_ID), Optional.empty(), 0, 20);
        verifyNoInteractions(resolver);
    }

    private AuditService service(boolean enabled, String mode, AuditEventPersister persister) {
        var config = mock(PlatformConfig.class);
        var audit = mock(PlatformConfig.Audit.class);
        when(config.audit()).thenReturn(audit);
        when(audit.enabled()).thenReturn(enabled);
        when(audit.mode()).thenReturn(mode);
        return new AuditService(filter, config, persister);
    }

    private static Target target(String id) {
        return new Target(TargetId.of(id), "Audit lab", TargetType.KEYCLOAK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("https://example.test", "master", "reader", "configured-ref"),
                null, null, Map.of());
    }

    private static AuditEventEntity historicalEvent() {
        var event = new AuditEventEntity();
        event.id = "audit-id";
        event.targetId = TARGET_ID;
        event.traceId = TRACE_ID;
        event.source = "SYSTEM";
        event.tool = "history";
        event.operation = "read";
        event.status = "SUCCESS";
        event.durationMs = 7L;
        event.createdAt = Instant.EPOCH;
        event.metadata = metadata();
        return event;
    }

    private static Map<String, Object> metadata() {
        var result = new LinkedHashMap<String, Object>();
        result.put("description", "password=" + CANARY);
        result.put("nested", List.of(Map.of("note", "Bearer " + CANARY), "apiKey=" + CANARY));
        result.put("Authorization", "Basic " + CANARY);
        result.put("Cookie", "session=" + CANARY);
        result.put("token=" + CANARY, "dynamic key value");
        result.put("[REDACTED KEY]", "existing key value");
        result.put("count", 7);
        result.put("enabled", false);
        result.put("missing", null);
        result.put("policy", "token lifespan and password policy");
        result.put("actor", "https://identity.example.test#subject-1");
        return result;
    }

    private void assertMetadataSafe(Map<String, Object> value) throws Exception {
        assertThat(objectMapper.writeValueAsString(value).contains(CANARY))
                .as("recognized credentials are absent from audit payload").isFalse();
        assertThat(value).hasSize(metadata().size()).containsEntry("count", 7).containsEntry("enabled", false)
                .containsEntry("missing", null).containsEntry("policy", "token lifespan and password policy")
                .containsEntry("actor", "https://identity.example.test#subject-1")
                .containsEntry("[REDACTED KEY]", "existing key value")
                .containsEntry("Authorization", "[REDACTED]").containsEntry("Cookie", "[REDACTED]");
    }

    private static void assertSafeLogs(List<LogRecord> records) {
        assertThat(records).isNotEmpty();
        for (var record : records) {
            assertThat(record.getThrown() == null).as("diagnostics exclude exception causes").isTrue();
            assertThat(record.getMessage().contains(CANARY)).as("message excludes credential canary").isFalse();
            if (record.getParameters() != null) {
                assertThat(java.util.Arrays.toString(record.getParameters()).contains(CANARY))
                        .as("parameters exclude credential canary").isFalse();
            }
        }
    }

    private static LogCapture capture(Class<?> type) { return new LogCapture(type); }

    private static final class LogCapture implements AutoCloseable {
        private final java.util.logging.Logger logger;
        private final Level previousLevel;
        private final boolean previousParentHandlers;
        private final List<LogRecord> records = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };

        private LogCapture(Class<?> type) {
            logger = java.util.logging.Logger.getLogger(type.getName());
            previousLevel = logger.getLevel();
            previousParentHandlers = logger.getUseParentHandlers();
            logger.setUseParentHandlers(false);
            handler.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
        }

        @Override public void close() {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParentHandlers);
        }
    }
}
