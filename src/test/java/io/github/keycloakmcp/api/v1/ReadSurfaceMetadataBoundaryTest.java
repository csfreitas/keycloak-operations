package io.github.keycloakmcp.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.discovery.DetectionConfidence;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.discovery.RuntimeType;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.inventory.ClusterInfo;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;
import io.github.keycloakmcp.domain.inventory.PodInventoryItem;
import io.github.keycloakmcp.domain.inventory.TopologyInfo;
import io.github.keycloakmcp.domain.metrics.MetricsStatusView;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.FleetItem;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.domain.platform.TargetOverview;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.mcp.inventory.InventoryTools;
import io.github.keycloakmcp.mcp.metrics.MetricsTools;
import io.github.keycloakmcp.mcp.target.TargetTools;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.observability.metrics.SemanticMetric;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.platform.FleetService;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.github.keycloakmcp.service.platform.TargetOverviewService;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Transport projections must not become evidence, scope or binding inputs. */
@QuarkusTest
class ReadSurfaceMetadataBoundaryTest {
    private static final String IDENTITY = "eyJpZGVudGl0eSI6InN5bnRoZXRpYyJ9.canonical.identity";
    private static final String CANARY = "read-surface-canary-7246";
    private static final String SECRET_TEXT = "password=" + CANARY;
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");

    @Inject TargetResource targets;
    @Inject TargetOverviewResource overview;
    @Inject FleetResource fleet;
    @Inject InventoryResource inventory;
    @Inject MetricsResource metrics;
    @Inject TargetTools targetTools;
    @Inject InventoryTools inventoryTools;
    @Inject MetricsTools metricsTools;
    @Inject ObjectMapper mapper;
    @InjectMock TargetRegistry registry;
    @InjectMock TargetResolver resolver;
    @InjectMock TargetAuthorizationService authorization;
    @InjectMock TargetOverviewService overviewService;
    @InjectMock FleetService fleetService;
    @InjectMock InventoryService inventoryService;
    @InjectMock EnvironmentDiscovery discovery;
    @InjectMock MetricsService metricsService;
    @InjectMock AuditService audit;
    @InjectMock McpMetrics toolMetrics;
    @InjectMock ToolAuthorization toolAuthorization;

    private Target target;
    private InfrastructureInventory observedInventory;
    private EnvironmentInfo observedEnvironment;

    @BeforeEach
    void setUp() {
        target = new Target(TargetId.of(IDENTITY), SECRET_TEXT, TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("https://operator:" + CANARY + "@example.test", "master", "ops", "ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "rhbk", "ref",
                        new KubernetesInstallationBinding("apps/v1", "Deployment", "keycloak", IDENTITY)),
                null, Map.of("note", SECRET_TEXT, SECRET_TEXT, "ordinary", "policy", "password policy and token lifespan"));
        when(registry.list()).thenReturn(List.of(target));
        when(resolver.require(IDENTITY)).thenReturn(target);
        when(authorization.isAllowed(target, TargetPermission.READ)).thenReturn(true);
        observedEnvironment = new EnvironmentInfo(RuntimeType.KUBERNETES, DetectionConfidence.CONFIRMED,
                SECRET_TEXT, "rhbk", List.of(SECRET_TEXT), IDENTITY, SECRET_TEXT, "kubernetes");
        observedInventory = new InfrastructureInventory(IDENTITY, "KUBERNETES",
                new ClusterInfo("kubernetes", SECRET_TEXT, SECRET_TEXT, 3, 2),
                KeycloakWorkloadInfo.unknown("rhbk"),
                List.of(new PodInventoryItem("keycloak-0", "node", SECRET_TEXT, true, 2, false)),
                new TopologyInfo(Map.of(SECRET_TEXT, 2), Map.of("node", 2), 1),
                null, null, null, null, null, null, List.of(), NOW, observedEnvironment);
        when(inventoryService.collect(IDENTITY)).thenReturn(observedInventory);
        when(discovery.discover(target)).thenReturn(observedEnvironment);
    }

    enum TargetSurface { REST_LIST, REST_GET, REST_STATUS, REST_OVERVIEW, REST_FLEET, MCP_LIST, MCP_GET, MCP_FIND }

    @ParameterizedTest
    @EnumSource(TargetSurface.class)
    void targetPresentationRedactsFreeMetadataButPreservesCanonicalScope(TargetSurface surface) throws Exception {
        TargetOverview raw = overviewFixture();
        when(overviewService.overview(IDENTITY)).thenReturn(raw);
        when(fleetService.fleet()).thenReturn(List.of(new FleetItem(IDENTITY, SECRET_TEXT, "RHBK", "TEST", true,
                SECRET_TEXT, SECRET_TEXT, HealthStatus.UNKNOWN, null, null, null, null, null, false,
                null, null, target.tags(), false)));
        Object result = switch (surface) {
            case REST_LIST -> targets.list();
            case REST_GET -> targets.get(IDENTITY);
            case REST_STATUS -> targets.status(IDENTITY);
            case REST_OVERVIEW -> overview.overview(IDENTITY);
            case REST_FLEET -> fleet.fleet();
            case MCP_LIST -> targetTools.keycloakListTargets();
            case MCP_GET -> targetTools.keycloakGetTarget(IDENTITY);
            case MCP_FIND -> targetTools.keycloakFindTargets("RHBK", "TEST");
        };
        assertSafe(result);
        assertThat(json(result)).contains(IDENTITY);
        assertThat(target.displayName()).isEqualTo(SECRET_TEXT);
        assertThat(target.infrastructure().installation().uid()).isEqualTo(IDENTITY);
        assertThat(raw.productVersion()).isEqualTo(SECRET_TEXT);
    }

    @Test
    void overviewPreservesNestedHistoryIdentitiesAndSnapshotHash() {
        when(overviewService.overview(IDENTITY)).thenReturn(overviewFixture());
        TargetOverview result = overview.overview(IDENTITY);
        assertThat(result.targetId()).isEqualTo(IDENTITY);
        assertThat(result.latestAssessment().id()).isEqualTo(IDENTITY);
        assertThat(result.latestAssessment().targetId()).isEqualTo(IDENTITY);
        assertThat(result.latestHealthCheck().id()).isEqualTo(IDENTITY);
        assertThat(result.latestHealthCheck().targetId()).isEqualTo(IDENTITY);
        assertThat(result.latestSnapshot().id()).isEqualTo(IDENTITY);
        assertThat(result.latestSnapshot().targetId()).isEqualTo(IDENTITY);
        assertThat(result.latestSnapshot().snapshotHash()).isEqualTo(IDENTITY);
        assertThat(result.desiredReplicas()).isEqualTo(3);
        assertThat(result.readyReplicas()).isNull();
    }

    enum InventorySurface { REST_INVENTORY, REST_ENVIRONMENT, REST_TOPOLOGY, MCP_INVENTORY }

    @ParameterizedTest
    @EnumSource(InventorySurface.class)
    void inventoryProjectionLeavesAuthoritativeObservedDataUntouched(InventorySurface surface) throws Exception {
        Object result = switch (surface) {
            case REST_INVENTORY -> inventory.inventory(IDENTITY);
            case REST_ENVIRONMENT -> inventory.environment(IDENTITY);
            case REST_TOPOLOGY -> inventory.topology(IDENTITY);
            case MCP_INVENTORY -> inventoryTools.keycloakGetInventory(IDENTITY);
        };
        assertSafe(result);
        if (surface != InventorySurface.REST_TOPOLOGY) assertThat(json(result)).contains(IDENTITY);
        assertThat(observedInventory.cluster().version()).isEqualTo(SECRET_TEXT);
        assertThat(observedInventory.topology().podsByZone()).containsEntry(SECRET_TEXT, 2);
        assertThat(observedEnvironment.evidence()).containsExactly(SECRET_TEXT);
        if (result instanceof InfrastructureInventory safe) {
            assertThat(safe.targetId()).isEqualTo(IDENTITY);
            assertThat(safe.discovery().targetId()).isEqualTo(IDENTITY);
            assertThat(safe.cluster().nodeCount()).isEqualTo(3);
            assertThat(safe.topology().podsByZone().values()).containsExactly(2);
            assertThat(safe.pods().getFirst().ready()).isTrue();
        }
    }

    @Test
    void environmentRequiresTargetReadPermissionBeforeDiscovery() {
        doThrow(McpException.targetUnauthorized(IDENTITY)).when(authorization).assertAllowed(target, TargetPermission.READ);
        assertThatThrownBy(() -> inventory.environment(IDENTITY)).isInstanceOf(McpException.class);
        verifyNoInteractions(discovery);
    }

    @Test
    void topologyLabelNamesCannotTurnNumericCountsIntoTextMarkers() {
        InfrastructureInventory raw = new InfrastructureInventory(IDENTITY, "KUBERNETES", null, null,
                List.of(), new TopologyInfo(Map.of("token", 2, SECRET_TEXT, 3), Map.of("secret", 5), 2),
                null, null, null, null, null, null, List.of(), NOW, observedEnvironment);
        when(inventoryService.collect(IDENTITY)).thenReturn(raw);
        InfrastructureInventory safe = inventory.inventory(IDENTITY);
        assertThat(safe.topology().podsByZone().values()).containsExactlyInAnyOrder(2, 3);
        assertThat(safe.topology().podsByNode().values()).containsExactly(5);
        assertThat(safe.topology().podsByZone().keySet()).doesNotContain(SECRET_TEXT);
    }

    @Test
    void targetListDoesNotProjectOrExposeUnreadableTargets() {
        when(authorization.isAllowed(target, TargetPermission.READ)).thenReturn(false);
        assertThat(targets.list()).isEmpty();
        assertThat(targetTools.keycloakListTargets()).isEmpty();
        verify(authorization, org.mockito.Mockito.times(2)).assertSession();
    }

    enum MetricsSurface { REST_STATUS, REST_SUMMARY, REST_CATEGORY, MCP_STATUS, MCP_SUMMARY, MCP_CATEGORY }

    @Test
    void arbitraryNumericAndBooleanCredentialsRemainRedacted() {
        when(metricsService.status(IDENTITY)).thenReturn(new MetricsStatusView(IDENTITY,
                MetricsProviderStatus.DEGRADED, "prometheus", true, "Metrics backend degraded",
                Map.of("password", 123456, "secret", true, "count", 3, "enabled", false)));
        MetricsStatusView safe = metrics.status(IDENTITY);
        assertThat(safe.details()).containsEntry("password", "[REDACTED]")
                .containsEntry("secret", "[REDACTED]").containsEntry("count", 3).containsEntry("enabled", false);
        assertThat(safe.targetId()).isEqualTo(IDENTITY);
    }

    @ParameterizedTest
    @EnumSource(MetricsSurface.class)
    void metricProjectionRetainsTypedFactsAndDoesNotRewriteProviderResults(MetricsSurface surface) throws Exception {
        MetricsStatusView status = new MetricsStatusView(IDENTITY, MetricsProviderStatus.DEGRADED, SECRET_TEXT,
                true, SECRET_TEXT, Map.of("note", SECRET_TEXT));
        PerformanceSummary summary = new PerformanceSummary(IDENTITY, MetricWindow.W_5M, MetricsProviderStatus.DEGRADED,
                SECRET_TEXT, NOW, new PerformanceSummary.Http(1d, null, null, null, null, null, 0d, false, false),
                null, null, null, null, null, Map.of("HTTP", MetricAvailability.NOT_AVAILABLE));
        SemanticMetricResult metric = new SemanticMetricResult(IDENTITY, SemanticMetric.values()[0], MetricWindow.W_5M,
                2.5, SECRET_TEXT, MetricAvailability.STALE, SECRET_TEXT, SECRET_TEXT, NOW, 1, NOW,
                List.of(Map.of("note", SECRET_TEXT, SECRET_TEXT, "retained")));
        when(metricsService.status(IDENTITY)).thenReturn(status);
        when(metricsService.summary(IDENTITY, "5m")).thenReturn(summary);
        when(metricsService.category(IDENTITY, MetricCategory.HTTP, "5m")).thenReturn(List.of(metric));
        Object result = switch (surface) {
            case REST_STATUS -> metrics.status(IDENTITY);
            case REST_SUMMARY -> metrics.summary(IDENTITY, "5m");
            case REST_CATEGORY -> metrics.http(IDENTITY, "5m");
            case MCP_STATUS -> metricsTools.keycloakGetMetricsStatus(IDENTITY);
            case MCP_SUMMARY -> metricsTools.keycloakGetPerformanceSummary(IDENTITY, "5m");
            case MCP_CATEGORY -> metricsTools.keycloakGetMetrics(IDENTITY, "HTTP", "5m");
        };
        assertSafe(result);
        if (surface != MetricsSurface.MCP_CATEGORY) assertThat(json(result)).contains(IDENTITY);
        assertThat(metric.reason()).isEqualTo(SECRET_TEXT);
        assertThat(metric.labels().getFirst()).containsEntry(SECRET_TEXT, "retained");
        assertThat(summary.source()).isEqualTo(SECRET_TEXT);
        if (result instanceof PerformanceSummary safe) {
            assertThat(safe.targetId()).isEqualTo(IDENTITY);
            assertThat(safe.http().requestRate()).isEqualTo(1d);
            assertThat(safe.http().errorRatePercent()).isNull();
            assertThat(safe.providerStatus()).isEqualTo(MetricsProviderStatus.DEGRADED);
        }
    }

    private TargetOverview overviewFixture() {
        return new TargetOverview(IDENTITY, SECRET_TEXT, "RHBK", "TEST", true,
                target.keycloak().url(), SECRET_TEXT, SECRET_TEXT, "rhbk", 3, null, 2, null,
                true, HealthStatus.UNKNOWN,
                new AssessmentRunSummary(IDENTITY, IDENTITY, SECRET_TEXT, 0, "PARTIAL", TriggerType.API, NOW, NOW, NOW),
                new HealthCheckSummary(IDENTITY, IDENTITY, HealthStatus.UNKNOWN, TriggerType.API, NOW, NOW, NOW),
                new SnapshotSummary(IDENTITY, IDENTITY, IDENTITY, NOW), NOW, target.tags());
    }

    private void assertSafe(Object value) throws Exception {
        assertThat(json(value)).doesNotContain(CANARY).contains("[REDACTED");
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }
}
