package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProvider;
import io.github.keycloakmcp.observability.metrics.MetricsProviderFactory;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.observability.metrics.SemanticMetric;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

/**
 * Ensures MetricsService routes through per-target providers and does not mix series.
 */
class MultiTargetMetricsIsolationTest {

    private TargetResolver resolver;
    private TargetAuthorizationService authz;
    private MetricsProviderFactory factory;
    private PerformanceSummaryService summaryService;
    private MetricsService metricsService;

    private MetricsProvider providerA;
    private MetricsProvider providerB;

    @BeforeEach
    void setUp() {
        resolver = mock(TargetResolver.class);
        authz = mock(TargetAuthorizationService.class);
        factory = mock(MetricsProviderFactory.class);
        summaryService = mock(PerformanceSummaryService.class);
        providerA = mock(MetricsProvider.class);
        providerB = mock(MetricsProvider.class);
        metricsService = new MetricsService(resolver, authz, factory, summaryService);

        Target a = target("target-a");
        Target b = target("target-b");
        when(resolver.require("target-a")).thenReturn(a);
        when(resolver.require("target-b")).thenReturn(b);
        when(factory.forTarget(a)).thenReturn(providerA);
        when(factory.forTarget(b)).thenReturn(providerB);
        when(factory.resolve(a)).thenReturn(providerA);
        when(factory.resolve(b)).thenReturn(providerB);
        when(providerA.status(a)).thenReturn(MetricsProviderStatus.AVAILABLE);
        when(providerB.status(b)).thenReturn(MetricsProviderStatus.AVAILABLE);
        when(summaryService.interactiveWindow()).thenReturn(MetricWindow.W_5M);
        when(summaryService.category(eq(a), eq(MetricCategory.HTTP), any()))
                .thenReturn(List.of(result("target-a", 11.0)));
        when(summaryService.category(eq(b), eq(MetricCategory.HTTP), any()))
                .thenReturn(List.of(result("target-b", 22.0)));
    }

    @Test
    void categoryResultsAreScopedPerTarget() {
        List<SemanticMetricResult> a = metricsService.category("target-a", MetricCategory.HTTP, "5m");
        List<SemanticMetricResult> b = metricsService.category("target-b", MetricCategory.HTTP, "5m");

        assertThat(a).hasSize(1);
        assertThat(b).hasSize(1);
        assertThat(a.get(0).targetId()).isEqualTo("target-a");
        assertThat(b.get(0).targetId()).isEqualTo("target-b");
        assertThat(a.get(0).value()).isEqualTo(11.0);
        assertThat(b.get(0).value()).isEqualTo(22.0);
        assertThat(a.get(0).value()).isNotEqualTo(b.get(0).value());
    }

    @Test
    void statusUsesResolvedTargetProvider() {
        when(factory.forTarget(any())).thenAnswer(inv -> {
            Target t = inv.getArgument(0);
            return "target-a".equals(t.id().value()) ? providerA : providerB;
        });
        when(providerA.status(any())).thenReturn(MetricsProviderStatus.AVAILABLE);
        when(providerB.status(any())).thenReturn(MetricsProviderStatus.DEGRADED);

        assertThat(metricsService.status("target-a").status()).isEqualTo(MetricsProviderStatus.AVAILABLE);
        assertThat(metricsService.status("target-b").status()).isEqualTo(MetricsProviderStatus.DEGRADED);
    }

    private static SemanticMetricResult result(String targetId, double value) {
        return SemanticMetricResult.available(
                targetId,
                SemanticMetric.HTTP_REQUEST_RATE,
                MetricWindow.W_5M,
                value,
                "rps",
                "mock",
                1,
                null,
                List.of());
    }

    private static Target target(String id) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "cli", "ref", null),
                null,
                new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"),
                java.util.Map.of());
    }
}
