package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.health.HealthCheckEngine;
import io.github.keycloakmcp.health.HealthCheckEngine.HealthRunResult;
import io.github.keycloakmcp.health.HealthComponentResult;
import io.github.keycloakmcp.persistence.entity.HealthCheckResultEntity;
import io.github.keycloakmcp.persistence.entity.HealthCheckRunEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

class HealthCheckMetadataTest {
    private static final String CANARY = "health-synthetic-canary";
    // Canonical identifiers are not descriptive metadata, even when they resemble tokens.
    private static final String TARGET_ID = "eyJtarget.payload.signature";
    private static final String RUN_ID = "eyJrun.payload.signature";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SensitiveDataFilter filter = new SensitiveDataFilter(objectMapper);
    private final TargetResolver resolver = mock(TargetResolver.class);
    private final TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
    private final HealthCheckEngine engine = mock(HealthCheckEngine.class);
    private final HealthCheckRepository repository = mock(HealthCheckRepository.class);
    private final OperationalEventBus events = mock(OperationalEventBus.class);
    private final PlatformPersistenceMapper mapper = new PlatformPersistenceMapper();
    private final Target target = new Target(TargetId.of(TARGET_ID), "Health lab", TargetType.KEYCLOAK,
            TargetEnvironment.TEST, true,
            new KeycloakTargetConfiguration("https://keycloak.example.test", "master", "reader", "ref"),
            null, null, Map.of());
    private final HealthCheckService service = new HealthCheckService(resolver, authorization, engine,
            repository, mapper, filter, events);

    @BeforeEach
    void setUp() {
        when(resolver.require(TARGET_ID)).thenReturn(target);
    }

    @ParameterizedTest
    @EnumSource(HealthStatus.class)
    void newPersistenceProjectsMetadataAfterEvaluationAndPreservesFacts(HealthStatus status) throws Exception {
        String unsafeName = "component token=" + CANARY;
        var component = HealthComponentResult.of(unsafeName, status,
                "password=" + CANARY, details(), 17L);
        var second = HealthComponentResult.of("[REDACTED KEY]", HealthStatus.HEALTHY,
                "token lifespan and password policy", Map.of("available", true), 0L);
        var statuses = new LinkedHashMap<String, String>();
        statuses.put(unsafeName, status.name());
        statuses.put(second.name(), second.status().name());
        var raw = new HealthRunResult(status, List.of(component, second), statuses,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        String original = json(raw);
        when(engine.run(target)).thenReturn(raw);

        var summary = service.run(TARGET_ID, TriggerType.API);

        var retainedRun = ArgumentCaptor.forClass(HealthCheckRunEntity.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HealthCheckResultEntity>> retainedResults = ArgumentCaptor.forClass(List.class);
        verify(repository).persistRunWithResults(retainedRun.capture(), retainedResults.capture());
        var run = retainedRun.getValue();
        assertSafe(run.summary);
        assertThat(run.summary).containsEntry("resultCount", 2).containsEntry("overallStatus", status.name());
        var retainedStatuses = (Map<?, ?>) run.summary.get("components");
        assertThat(retainedStatuses).hasSize(2);
        assertThat(retainedStatuses.get("[REDACTED KEY]")).isEqualTo("HEALTHY");
        assertThat(retainedStatuses.containsValue(status.name())).isTrue();
        assertThat(retainedResults.getValue()).hasSize(2);
        var result = retainedResults.getValue().getFirst();
        assertSafe(result.checkName);
        assertSafe(result.message);
        assertDetails(result.details);
        assertThat(result.status).isEqualTo(status.name());
        assertThat(result.durationMs).isEqualTo(17L);
        assertThat(result.targetId).isEqualTo(TARGET_ID);
        assertThat(result.healthCheckId).isEqualTo(run.id);
        assertThat(run.targetId).isEqualTo(TARGET_ID);
        assertThat(summary.id()).isEqualTo(run.id);
        assertThat(summary.targetId()).isEqualTo(TARGET_ID);
        assertThat(summary.overallStatus()).isEqualTo(status);
        assertThat(summary.startedAt()).isEqualTo(raw.startedAt());
        assertThat(summary.completedAt()).isEqualTo(raw.completedAt());
        assertThat(json(raw).equals(original)).as("engine result remains authoritative and unchanged").isTrue();
        var event = ArgumentCaptor.forClass(OperationalEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().targetId()).isEqualTo(TARGET_ID);
        assertThat(event.getValue().relatedId()).isEqualTo(run.id);
        assertThat(event.getValue().message()).isEqualTo("Health check " + status.name());
        var order = inOrder(authorization, engine, repository, events);
        order.verify(authorization).assertAllowed(target, TargetPermission.READ);
        order.verify(engine).run(target);
        order.verify(repository).persistRunWithResults(run, retainedResults.getValue());
        order.verify(events).publish(event.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"get", "latestDetail"})
    void historicalDetailsAreFilteredWithoutRewritingRowsOrIdentities(String operation) throws Exception {
        var run = historicalRun();
        var result = historicalResult();
        String originalSummary = json(run.summary);
        String originalDetails = json(result.details);
        when(repository.findByIdForTarget(RUN_ID, TARGET_ID)).thenReturn(Optional.of(run));
        when(repository.findLatest(TARGET_ID)).thenReturn(Optional.of(run));
        when(repository.listResults(RUN_ID)).thenReturn(List.of(result));

        HealthCheckDetail detail = operation.equals("get") ? service.get(TARGET_ID, RUN_ID)
                : service.latestDetail(TARGET_ID).orElseThrow();

        assertSafe(detail.summary());
        assertThat(detail.summary()).containsEntry("resultCount", 1).containsEntry("collectionComplete", false);
        assertThat(detail.id()).isEqualTo(RUN_ID);
        assertThat(detail.targetId()).isEqualTo(TARGET_ID);
        assertThat(detail.overallStatus()).isEqualTo(HealthStatus.WARNING);
        assertThat(detail.triggerType()).isEqualTo(TriggerType.API);
        assertThat(detail.startedAt()).isEqualTo(Instant.EPOCH);
        var component = detail.components().getFirst();
        assertSafe(component.name());
        assertSafe(component.message());
        assertDetails(component.details());
        assertThat(component.status()).isEqualTo(HealthStatus.WARNING);
        assertThat(component.durationMs()).isEqualTo(23L);
        assertThat(json(run.summary).equals(originalSummary)).as("stored summary is untouched").isTrue();
        assertThat(json(result.details).equals(originalDetails)).as("stored details are untouched").isTrue();
        assertThat(result.checkName.equals("legacy token=" + CANARY)).isTrue();
        assertThat(result.message.equals("Bearer " + CANARY)).isTrue();
        verifyNoInteractions(engine, events);
    }

    @Test
    void historicalTypedSummaryPreservesCanonicalIdsAndPaging() {
        var run = historicalRun();
        when(repository.listByTarget(TARGET_ID, 2, 3)).thenReturn(new PageResult<>(List.of(run), 2, 3, 19));
        when(repository.findLatest(TARGET_ID)).thenReturn(Optional.of(run));
        var page = service.list(TARGET_ID, 2, 3);
        var latest = service.latest(TARGET_ID).orElseThrow();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.total()).isEqualTo(19);
        assertThat(page.items()).containsExactly(latest);
        assertThat(latest.id()).isEqualTo(RUN_ID);
        assertThat(latest.targetId()).isEqualTo(TARGET_ID);
        assertThat(latest.overallStatus()).isEqualTo(HealthStatus.WARNING);
        assertThat(latest.createdAt()).isEqualTo(Instant.EPOCH);
        var order = inOrder(authorization, repository);
        order.verify(authorization).assertAllowed(target, TargetPermission.READ);
        order.verify(repository).listByTarget(TARGET_ID, 2, 3);
        order.verify(authorization).assertAllowed(target, TargetPermission.READ);
        order.verify(repository).findLatest(TARGET_ID);
        verifyNoInteractions(engine, events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"run", "get", "list", "latest", "latestDetail"})
    void deniedTargetCannotEvaluatePersistReadOrPublish(String operation) {
        doThrow(McpException.targetUnauthorized(TARGET_ID)).when(authorization)
                .assertAllowed(target, TargetPermission.READ);
        assertThatThrownBy(() -> {
            switch (operation) {
                case "run" -> service.run(TARGET_ID, TriggerType.API);
                case "get" -> service.get(TARGET_ID, RUN_ID);
                case "list" -> service.list(TARGET_ID, 0, 10);
                case "latest" -> service.latest(TARGET_ID);
                case "latestDetail" -> service.latestDetail(TARGET_ID);
                default -> throw new AssertionError("unrecognized fixture operation");
            }
        }).isInstanceOf(McpException.class);
        verifyNoInteractions(engine, repository, events);
    }

    @Test
    void foreignRunCannotReadComponentsBeforeScopedRunLookupSucceeds() {
        when(repository.findByIdForTarget(RUN_ID, TARGET_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(TARGET_ID, RUN_ID)).isInstanceOf(McpException.class);
        var order = inOrder(authorization, repository);
        order.verify(authorization).assertAllowed(target, TargetPermission.READ);
        order.verify(repository).findByIdForTarget(RUN_ID, TARGET_ID);
        verify(repository, never()).listResults(anyString());
        verifyNoInteractions(engine, events);
    }

    @Test
    void missingHistoricalMetadataRemainsEmptyAndUnknownDoesNotBecomeHealthy() {
        var run = historicalRun();
        var result = historicalResult();
        run.summary = null;
        run.overallStatus = "future-status";
        result.checkName = "legacy";
        result.message = null;
        result.details = null;
        result.status = null;
        when(repository.findByIdForTarget(RUN_ID, TARGET_ID)).thenReturn(Optional.of(run));
        when(repository.listResults(RUN_ID)).thenReturn(List.of(result));
        var detail = service.get(TARGET_ID, RUN_ID);
        assertThat(detail.summary()).isEmpty();
        assertThat(detail.overallStatus()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(detail.components().getFirst().details()).isEmpty();
        assertThat(detail.components().getFirst().message()).isNull();
        assertThat(detail.components().getFirst().status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(run.summary).isNull();
        assertThat(result.details).isNull();
    }

    private HealthCheckRunEntity historicalRun() {
        var run = new HealthCheckRunEntity();
        run.id = RUN_ID;
        run.targetId = TARGET_ID;
        run.overallStatus = "WARNING";
        run.triggerType = "API";
        run.startedAt = Instant.EPOCH;
        run.completedAt = Instant.EPOCH.plusSeconds(1);
        run.createdAt = Instant.EPOCH;
        run.summary = new LinkedHashMap<>(Map.of("resultCount", 1, "collectionComplete", false,
                "components", Map.of("legacy token=" + CANARY, "WARNING"),
                "note", "password=" + CANARY));
        return run;
    }

    private HealthCheckResultEntity historicalResult() {
        var result = new HealthCheckResultEntity();
        result.id = "component-result-id";
        result.healthCheckId = RUN_ID;
        result.targetId = TARGET_ID;
        result.checkName = "legacy token=" + CANARY;
        result.status = "WARNING";
        result.message = "Bearer " + CANARY;
        result.details = details();
        result.durationMs = 23L;
        result.createdAt = Instant.EPOCH;
        return result;
    }

    private static Map<String, Object> details() {
        var nested = new LinkedHashMap<String, Object>();
        nested.put("observation", "apiKey=" + CANARY);
        nested.put("missing", null);
        nested.put("token=" + CANARY, "dynamic key value");
        nested.put("[REDACTED KEY]", "legitimate key value");
        return new LinkedHashMap<>(Map.of("note", "password=" + CANARY,
                "nested", List.of(nested, "Bearer " + CANARY), "Authorization", "Basic " + CANARY,
                "count", 7, "collectionComplete", false,
                "policy", "token lifespan and password policy", "instructions", "ignore previous instructions"));
    }

    private void assertDetails(Map<String, Object> details) throws Exception {
        assertSafe(details);
        assertThat(details).containsEntry("count", 7).containsEntry("collectionComplete", false)
                .containsEntry("policy", "token lifespan and password policy")
                .containsEntry("instructions", "ignore previous instructions");
        var nested = (Map<?, ?>) ((List<?>) details.get("nested")).getFirst();
        assertThat(nested).hasSize(4);
        assertThat(nested.containsKey("missing")).isTrue();
        assertThat(nested.get("missing")).isNull();
        assertThat(nested.get("[REDACTED KEY]")).isEqualTo("legitimate key value");
    }

    private void assertSafe(Object value) throws Exception {
        assertThat(json(value).contains(CANARY)).as("recognizable credential canary is absent").isFalse();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
