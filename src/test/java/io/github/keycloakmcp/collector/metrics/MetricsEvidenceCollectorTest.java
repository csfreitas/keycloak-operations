package io.github.keycloakmcp.collector.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.config.PerformanceConfig;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricAvailabilityService;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

class MetricsEvidenceCollectorTest {

    private MetricsService metricsService;
    private PerformanceConfig performanceConfig;
    private MetricAvailabilityService availabilityService;
    private MetricsEvidenceCollector collector;

    @BeforeEach
    void setUp() {
        metricsService = mock(MetricsService.class);
        performanceConfig = mock(PerformanceConfig.class);
        availabilityService = mock(MetricAvailabilityService.class);
        when(performanceConfig.latencyP99Ms()).thenReturn(OptionalDouble.of(200));
        when(performanceConfig.latencyP95Ms()).thenReturn(OptionalDouble.empty());
        when(performanceConfig.serverErrorRatePercent()).thenReturn(OptionalDouble.of(1.0));
        when(performanceConfig.dbAwaitingWarning()).thenReturn(OptionalInt.of(5));
        when(performanceConfig.dbAwaitingCritical()).thenReturn(OptionalInt.empty());
        when(performanceConfig.heapUtilizationWarningPercent()).thenReturn(OptionalDouble.of(85));
        when(performanceConfig.gcPauseWarningMs()).thenReturn(OptionalDouble.empty());
        when(performanceConfig.minimumCacheHitRatio()).thenReturn(OptionalDouble.empty());
        when(availabilityService.detect(any())).thenReturn(emptySeries());
        collector = new MetricsEvidenceCollector(metricsService, performanceConfig, availabilityService);
    }

    @Test
    void returnsEmptyWhenMetricsNotConfigured() {
        Target target = target(null);
        assertThat(collector.collect(target)).isEmpty();
    }

    @Test
    void emitsSloBooleansAndDoesNotCoerceMissingToZero() {
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        PerformanceSummary summary = new PerformanceSummary(
                "lab-a",
                MetricWindow.W_15M,
                MetricsProviderStatus.AVAILABLE,
                "PROMETHEUS",
                Instant.now(),
                new PerformanceSummary.Http(10.0, 2.5, 50.0, null, null, 350.0, null, true),
                new PerformanceSummary.Database(null, 3.0, 8.0, null),
                new PerformanceSummary.Jvm(null, null, null, 0.9, null),
                PerformanceSummary.Cache.empty(),
                new PerformanceSummary.Cluster(3.0),
                PerformanceSummary.Runtime.empty(),
                Map.of());
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        List<Evidence> evidence = collector.collect(target);
        Map<String, Object> byKey = evidence.stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));

        assertThat(byKey.get("metrics.source.available")).isEqualTo(true);
        assertThat(byKey.get("metrics.http.p99Ms")).isEqualTo(350.0);
        assertThat(byKey.containsKey("metrics.http.p50Ms")).isFalse();
        assertThat(byKey.get("metrics.slo.p99Configured")).isEqualTo(true);
        assertThat(byKey.get("metrics.slo.p99Exceeded")).isEqualTo(true);
        assertThat(byKey.get("metrics.slo.errorRateExceeded")).isEqualTo(true);
        assertThat(byKey.get("metrics.db.awaitingWarning")).isEqualTo(true);
        assertThat(byKey.get("metrics.jvm.heapPressure")).isEqualTo(true);
        assertThat(byKey.get("metrics.cluster.size")).isEqualTo(3.0);
        assertThat(byKey.containsKey("metrics.cache.hitRatio")).isFalse();
    }

    @Test
    void emitsHistogramRequiredButMissingWhenP99Absent() {
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        PerformanceSummary summary = new PerformanceSummary(
                "lab-a",
                MetricWindow.W_15M,
                MetricsProviderStatus.AVAILABLE,
                "PROMETHEUS",
                Instant.now(),
                new PerformanceSummary.Http(1.0, null, null, null, null, null, null, false),
                PerformanceSummary.Database.empty(),
                PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of(SemanticMetricKey.HTTP_P99, MetricAvailability.NOT_AVAILABLE));
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        Map<String, Object> byKey = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));
        assertThat(byKey.get("metrics.http.histogram.requiredButMissing")).isEqualTo(true);
        assertThat(byKey.containsKey("metrics.slo.p99Exceeded")).isFalse();
    }

    private static Map<MetricAvailabilityService.SeriesKey, Boolean> emptySeries() {
        Map<MetricAvailabilityService.SeriesKey, Boolean> m = new EnumMap<>(MetricAvailabilityService.SeriesKey.class);
        for (MetricAvailabilityService.SeriesKey k : MetricAvailabilityService.SeriesKey.values()) {
            m.put(k, false);
        }
        return m;
    }

    private static Target target(ObservabilityTargetConfiguration obs) {
        return new Target(
                TargetId.of("lab-a"),
                "Lab A",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "cli", "lab-a", null),
                null,
                obs,
                Map.of());
    }

    /** Local alias so availability map keys stay readable. */
    private static final class SemanticMetricKey {
        static final String HTTP_P99 = "HTTP_P99_LATENCY";
    }
}
