package io.github.keycloakmcp.collector.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.config.PerformanceConfig;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricAvailability;
import io.github.keycloakmcp.observability.metrics.MetricAvailabilityService;
import io.github.keycloakmcp.observability.metrics.MetricWindow;
import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;
import io.github.keycloakmcp.observability.metrics.ScrapeReadiness;
import io.github.keycloakmcp.observability.metrics.ServiceMonitorProbe;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

class MetricsEvidenceCollectorTest {

    private MetricsService metricsService;
    private PerformanceConfig performanceConfig;
    private MetricAvailabilityService availabilityService;
    private InventoryService inventoryService;
    private ServiceMonitorProbe serviceMonitorProbe;
    private MetricsEvidenceCollector collector;

    @BeforeEach
    void setUp() {
        metricsService = mock(MetricsService.class);
        performanceConfig = mock(PerformanceConfig.class);
        availabilityService = mock(MetricAvailabilityService.class);
        inventoryService = mock(InventoryService.class);
        serviceMonitorProbe = mock(ServiceMonitorProbe.class);
        when(performanceConfig.latencyP99Ms()).thenReturn(OptionalDouble.of(200));
        when(performanceConfig.latencyP95Ms()).thenReturn(OptionalDouble.empty());
        when(performanceConfig.serverErrorRatePercent()).thenReturn(OptionalDouble.of(1.0));
        when(performanceConfig.dbAwaitingWarning()).thenReturn(OptionalInt.of(5));
        when(performanceConfig.dbAwaitingCritical()).thenReturn(OptionalInt.empty());
        when(performanceConfig.heapUtilizationWarningPercent()).thenReturn(OptionalDouble.of(85));
        when(performanceConfig.gcPauseWarningMs()).thenReturn(OptionalDouble.empty());
        when(performanceConfig.minimumCacheHitRatio()).thenReturn(OptionalDouble.empty());
        when(availabilityService.detect(any())).thenReturn(emptySeries());
        when(serviceMonitorProbe.probe(any())).thenReturn(
                new ServiceMonitorProbe.Result(ScrapeReadiness.UNKNOWN, null, null, null, "n/a"));
        collector = new MetricsEvidenceCollector(
                metricsService, performanceConfig, availabilityService, inventoryService, serviceMonitorProbe);
    }

    @Test
    void returnsEmptyWhenMetricsNotConfigured() {
        Target target = target(null);
        assertThat(collector.collect(target)).isEmpty();
    }

    @Test
    void expiredParentDoesNotStartAnyCollection() {
        var clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(100), clock::get);
        clock.set(Duration.ofMillis(100).toNanos());
        try (var scope = CollectionBudget.open("lab-a", budget)) {
            assertThat(collected(targetWithInfrastructure())).containsExactly(
                    Map.entry("metrics.collection.complete", false));
        }
        verifyNoInteractions(metricsService, availabilityService, inventoryService, serviceMonitorProbe);
        assertThat(CollectionBudget.current()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unmarkedLateSummaryOrExceptionCannotEstablishAnyEvidence(boolean throwsFailure) {
        var clock = new AtomicLong();
        var target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenAnswer(inv -> {
            clock.set(Duration.ofMillis(100).toNanos());
            if (throwsFailure) throw new IllegalStateException("private-provider-canary");
            return completedSummary();
        });
        try (var scope = CollectionBudget.open("lab-a", new CollectionBudget(Duration.ofMillis(100), clock::get))) {
            assertThat(collected(target)).containsExactly(Map.entry("metrics.collection.complete", false));
        }
        verifyNoInteractions(availabilityService, inventoryService, serviceMonitorProbe);
    }

    @ParameterizedTest
    @ValueSource(strings = {"availability", "inventory", "monitor"})
    void followOnDeadlinePreservesSummaryButRejectsLateSourceAndStopsFanout(String phase) {
        var clock = new AtomicLong();
        var target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenReturn(completedSummary());
        if (phase.equals("availability")) {
            when(availabilityService.detect(target)).thenAnswer(inv -> {
                clock.set(Duration.ofMillis(100).toNanos());
                return emptySeries();
            });
        } else if (phase.equals("inventory")) {
            when(inventoryService.collect("lab-a")).thenAnswer(inv -> {
                clock.set(Duration.ofMillis(100).toNanos());
                return null;
            });
        } else {
            when(serviceMonitorProbe.probe(target)).thenAnswer(inv -> {
                clock.set(Duration.ofMillis(100).toNanos());
                return new ServiceMonitorProbe.Result(ScrapeReadiness.SCRAPE_HEALTHY, true, "30s", "10s", "safe");
            });
        }
        try (var scope = CollectionBudget.open("lab-a", new CollectionBudget(Duration.ofMillis(100), clock::get))) {
            var evidence = collected(target);
            assertThat(evidence).containsEntry("metrics.collection.complete", false)
                    .containsEntry("metrics.http.requestRate", 10.0)
                    .containsEntry("metrics.cluster.size", 3.0)
                    .doesNotContainKeys("metrics.scrape.readiness", "metrics.serviceMonitor.present",
                            "metrics.cluster.readyReplicas", "metrics.cluster.sizeMismatch");
            if (phase.equals("availability")) {
                assertThat(evidence).doesNotContainKey("metrics.series.httpCount");
                verifyNoInteractions(inventoryService, serviceMonitorProbe);
            } else {
                assertThat(evidence).containsEntry("metrics.series.httpCount", false);
                verify(inventoryService).collect("lab-a");
                if (phase.equals("inventory")) verifyNoInteractions(serviceMonitorProbe);
            }
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test
    void interruptedFollowOnPreservesEarlierEvidenceAndInterruptFlag() {
        var target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenReturn(completedSummary());
        when(availabilityService.detect(target)).thenAnswer(inv -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("private-provider-canary");
        });
        try {
            assertThat(collected(target)).containsEntry("metrics.collection.complete", false)
                    .containsEntry("metrics.http.requestRate", 10.0)
                    .doesNotContainKey("metrics.series.httpCount");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(inventoryService, serviceMonitorProbe);
            assertThat(CollectionBudget.current()).isNull();
        } finally {
            Thread.interrupted();
        }
    }

    private Map<String, Object> collected(Target target) {
        return collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));
    }

    private static PerformanceSummary completedSummary() {
        return new PerformanceSummary("lab-a", MetricWindow.W_15M, MetricsProviderStatus.AVAILABLE, "PROMETHEUS", Instant.now(),
                new PerformanceSummary.Http(10.0, null, null, null, null, null, null, false, false),
                PerformanceSummary.Database.empty(), PerformanceSummary.Jvm.empty(), PerformanceSummary.Cache.empty(),
                new PerformanceSummary.Cluster(3.0), PerformanceSummary.Runtime.empty(),
                Map.of("COLLECTION_BUDGET", MetricAvailability.AVAILABLE, "HTTP_BUCKET_SERIES", MetricAvailability.UNKNOWN));
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
                new PerformanceSummary.Http(10.0, 2.5, 50.0, null, null, 350.0, null, true, false),
                new PerformanceSummary.Database(null, 3.0, 8.0, 6.0, 9.0, null),
                new PerformanceSummary.Jvm(null, null, null, 0.9, null),
                PerformanceSummary.Cache.empty(),
                new PerformanceSummary.Cluster(3.0),
                PerformanceSummary.Runtime.empty(),
                Map.of("DB_POOL_AWAITING_RANGE", MetricAvailability.AVAILABLE));
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
                new PerformanceSummary.Http(1.0, null, null, null, null, null, null, false, false),
                PerformanceSummary.Database.empty(),
                PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of("HTTP_P99_LATENCY", MetricAvailability.NOT_AVAILABLE));
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        Map<String, Object> byKey = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));
        assertThat(byKey.get("metrics.http.histogram.requiredButMissing")).isEqualTo(true);
        assertThat(byKey.containsKey("metrics.slo.p99Exceeded")).isFalse();
    }

    @Test
    void histogramPresentWithNoTrafficDoesNotRecommendEnablingHistogram() {
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        PerformanceSummary summary = new PerformanceSummary(
                "lab-a",
                MetricWindow.W_15M,
                MetricsProviderStatus.AVAILABLE,
                "PROMETHEUS",
                Instant.now(),
                new PerformanceSummary.Http(null, null, null, null, null, null, null, true, true),
                PerformanceSummary.Database.empty(),
                PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of("HTTP_P99_LATENCY", MetricAvailability.NOT_AVAILABLE));
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        Map<String, Object> byKey = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));
        assertThat(byKey.get("metrics.http.histogram.available")).isEqualTo(true);
        assertThat(byKey.get("metrics.http.noTrafficInWindow")).isEqualTo(true);
        assertThat(byKey.get("metrics.http.histogram.requiredButMissing")).isEqualTo(false);
        assertThat(byKey.containsKey("metrics.slo.p99Exceeded")).isFalse();
    }

    @Test
    void prefersCriticalDbAwaitingOverWarning() {
        when(performanceConfig.dbAwaitingCritical()).thenReturn(OptionalInt.of(20));
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        PerformanceSummary summary = new PerformanceSummary(
                "lab-a",
                MetricWindow.W_15M,
                MetricsProviderStatus.AVAILABLE,
                "PROMETHEUS",
                Instant.now(),
                PerformanceSummary.Http.empty(),
                new PerformanceSummary.Database(null, null, 25.0, 22.0, 30.0, null),
                PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of("DB_POOL_AWAITING_RANGE", MetricAvailability.AVAILABLE));
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        Map<String, Object> byKey = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));
        assertThat(byKey.get("metrics.db.awaitingCritical")).isEqualTo(true);
        assertThat(byKey.get("metrics.db.awaitingWarning")).isEqualTo(false);
    }

    @Test
    void emitsGcPauseExceededWhenConfigured() {
        when(performanceConfig.gcPauseWarningMs()).thenReturn(OptionalDouble.of(100));
        when(performanceConfig.latencyP99Ms()).thenReturn(OptionalDouble.empty());
        when(performanceConfig.serverErrorRatePercent()).thenReturn(OptionalDouble.empty());
        when(performanceConfig.dbAwaitingWarning()).thenReturn(OptionalInt.empty());
        when(performanceConfig.heapUtilizationWarningPercent()).thenReturn(OptionalDouble.empty());
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        PerformanceSummary summary = new PerformanceSummary(
                "lab-a",
                MetricWindow.W_15M,
                MetricsProviderStatus.AVAILABLE,
                "PROMETHEUS",
                Instant.now(),
                PerformanceSummary.Http.empty(),
                PerformanceSummary.Database.empty(),
                new PerformanceSummary.Jvm(null, null, null, null, 250.0),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of());
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        Map<String, Object> byKey = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));
        assertThat(byKey.get("metrics.jvm.gcPauseExceeded")).isEqualTo(true);
        assertThat(byKey.get("metrics.jvm.gcPauseMaxMs")).isEqualTo(250.0);
    }

    @Test
    void staleMetricsDoNotEmitSloExceeded() {
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        PerformanceSummary summary = new PerformanceSummary(
                "lab-a",
                MetricWindow.W_15M,
                MetricsProviderStatus.AVAILABLE,
                "PROMETHEUS",
                Instant.now(),
                new PerformanceSummary.Http(10.0, 5.0, null, null, null, 900.0, null, true, false),
                PerformanceSummary.Database.empty(),
                PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(),
                Map.of(
                        "HTTP_P99_LATENCY", MetricAvailability.STALE,
                        "HTTP_ERROR_RATE", MetricAvailability.STALE));
        when(metricsService.summaryForAssessment(target)).thenReturn(summary);

        Map<String, Object> byKey = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value, (a, b) -> a));
        assertThat(byKey.get("metrics.stale.present")).isEqualTo(true);
        assertThat(byKey.containsKey("metrics.slo.p99Exceeded")).isFalse();
        assertThat(byKey.containsKey("metrics.slo.errorRateExceeded")).isFalse();
    }

    @Test
    void heapMeasurementsDoNotImplyPolicyUntilThresholdIsConfigured() {
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        when(metricsService.summaryForAssessment(target)).thenReturn(new PerformanceSummary(
                "lab-a", MetricWindow.W_15M, MetricsProviderStatus.AVAILABLE, "PROMETHEUS", Instant.now(),
                PerformanceSummary.Http.empty(), PerformanceSummary.Database.empty(),
                new PerformanceSummary.Jvm(90.0, 100.0, 100.0, 0.9, null),
                PerformanceSummary.Cache.empty(), PerformanceSummary.Cluster.empty(), PerformanceSummary.Runtime.empty(),
                Map.of("JVM_HEAP_UTILIZATION", MetricAvailability.AVAILABLE)));
        when(performanceConfig.heapUtilizationWarningPercent()).thenReturn(OptionalDouble.empty());
        var absent = collector.collect(target).stream().collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));
        assertThat(absent).containsEntry("metrics.jvm.heapUtilization", 0.9).doesNotContainKey("metrics.jvm.heapPressure");
        when(performanceConfig.heapUtilizationWarningPercent()).thenReturn(OptionalDouble.of(85));
        var configured = collector.collect(target).stream().collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));
        assertThat(configured).containsEntry("metrics.jvm.heapPressure", true);
        when(performanceConfig.heapUtilizationWarningPercent()).thenReturn(OptionalDouble.of(95));
        var below = collector.collect(target).stream().collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));
        assertThat(below).containsEntry("metrics.jvm.heapPressure", false);
    }

    @ParameterizedTest
    @EnumSource(value = MetricAvailability.class, names = "AVAILABLE", mode = EnumSource.Mode.EXCLUDE)
    void unavailableRangeNeverUsesInstantOrRetainedNumbersForSustainedFinding(MetricAvailability state) {
        assertThat(databaseEvidence(25.0, 22.0, 30.0, Map.of("DB_POOL_AWAITING_RANGE", state)))
                .doesNotContainKeys("metrics.db.awaitingWarning", "metrics.db.awaitingCritical")
                .containsEntry("metrics.db.awaitingCurrent", 25.0);
    }

    @Test
    void missingRangeMetadataNeverAdvertisesSustainedPassOrFailure() {
        assertThat(databaseEvidence(0.0, 0.0, 0.0, Map.of()))
                .doesNotContainKeys("metrics.db.awaitingWarning", "metrics.db.awaitingCritical");
        assertThat(databaseEvidence(25.0, null, null, Map.of()))
                .doesNotContainKeys("metrics.db.awaitingWarning", "metrics.db.awaitingCritical");
    }

    @Test
    void invalidRangeNumbersDoNotProduceSustainedEvidence() {
        for (Double[] values : List.of(new Double[]{null, 30.0}, new Double[]{22.0, null},
                new Double[]{Double.NaN, 30.0}, new Double[]{22.0, Double.POSITIVE_INFINITY},
                new Double[]{-1.0, 30.0}, new Double[]{30.0, 22.0})) {
            assertThat(databaseEvidence(25.0, values[0], values[1],
                    Map.of("DB_POOL_AWAITING_RANGE", MetricAvailability.AVAILABLE)))
                    .doesNotContainKeys("metrics.db.awaitingWarning", "metrics.db.awaitingCritical");
        }
    }

    @Test
    void validatedZeroRangeStillProducesObservedNegativeFindings() {
        assertThat(databaseEvidence(0.0, 0.0, 0.0, Map.of("DB_POOL_AWAITING_RANGE", MetricAvailability.AVAILABLE)))
                .containsEntry("metrics.db.awaitingWarning", false)
                .containsEntry("metrics.db.awaitingCritical", false);
    }

    @Test
    void validatedRangeDoesNotDependOnSeparateInstantAvailability() {
        assertThat(databaseEvidence(null, 22.0, 30.0, Map.of(
                "DB_POOL_AWAITING_RANGE", MetricAvailability.AVAILABLE,
                "DB_POOL_AWAITING", MetricAvailability.STALE)))
                .containsEntry("metrics.db.awaitingCritical", true)
                .containsEntry("metrics.db.awaitingWarning", false);
    }

    @ParameterizedTest
    @EnumSource(value = MetricsProviderStatus.class, names = {"AVAILABLE", "DEGRADED"})
    void abortedSummaryPreservesObservationsButSkipsAdditionalRemoteCollectionAndUnknownFlags(MetricsProviderStatus status) {
        Target target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenReturn(new PerformanceSummary(
                "lab-a", MetricWindow.W_15M, status, "PROMETHEUS", Instant.now(),
                new PerformanceSummary.Http(10.0, 2.5, null, null, null, null, null, false, false),
                PerformanceSummary.Database.empty(), new PerformanceSummary.Jvm(null, null, null, 0.9, null),
                PerformanceSummary.Cache.empty(), new PerformanceSummary.Cluster(3.0), PerformanceSummary.Runtime.empty(),
                Map.of("COLLECTION_BUDGET", MetricAvailability.NOT_AVAILABLE,
                        "HTTP_BUCKET_SERIES", MetricAvailability.UNKNOWN,
                        "HTTP_ERROR_RATE", MetricAvailability.AVAILABLE,
                        "JVM_HEAP_UTILIZATION", MetricAvailability.AVAILABLE)));

        Map<String, Object> evidence = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));

        assertThat(evidence).containsEntry("metrics.collection.complete", false)
                .containsEntry("metrics.http.requestRate", 10.0)
                .containsEntry("metrics.slo.errorRateExceeded", true)
                .containsEntry("metrics.jvm.heapPressure", true)
                .containsEntry("metrics.cluster.size", 3.0)
                .doesNotContainKeys("metrics.source.available", "metrics.http.histogram.available", "metrics.http.histogram.requiredButMissing",
                        "metrics.http.noTrafficInWindow", "metrics.stale.present", "metrics.series.httpCount",
                        "metrics.cluster.readyReplicas", "metrics.scrape.readiness");
        verifyNoInteractions(availabilityService, inventoryService, serviceMonitorProbe);
    }

    @ParameterizedTest
    @EnumSource(value = MetricsProviderStatus.class, names = {"AVAILABLE", "DEGRADED"})
    void abortBeforeObservationsNeverClaimsSourceReachability(MetricsProviderStatus status) {
        Target target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenReturn(new PerformanceSummary(
                "lab-a", MetricWindow.W_15M, status, "PROMETHEUS", Instant.now(),
                PerformanceSummary.Http.empty(), PerformanceSummary.Database.empty(), PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(), PerformanceSummary.Cluster.empty(), PerformanceSummary.Runtime.empty(),
                Map.of("COLLECTION_BUDGET", MetricAvailability.NOT_AVAILABLE,
                        "HTTP_BUCKET_SERIES", MetricAvailability.UNKNOWN)));

        Map<String, Object> evidence = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));

        assertThat(evidence).containsEntry("metrics.collection.complete", false)
                .containsEntry("metrics.provider.status", status.name())
                .doesNotContainKeys("metrics.source.available", "metrics.http.requestRate",
                        "metrics.http.histogram.available", "metrics.stale.present");
        verifyNoInteractions(availabilityService, inventoryService, serviceMonitorProbe);
    }

    @Test
    void completedSummaryWithUnknownHistogramDoesNotTreatFailedPresenceProbeAsAbsent() {
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        when(metricsService.summaryForAssessment(target)).thenReturn(summaryWithHistogram(
                Map.of("COLLECTION_BUDGET", MetricAvailability.AVAILABLE,
                        "HTTP_BUCKET_SERIES", MetricAvailability.UNKNOWN), false));
        when(availabilityService.detect(target)).thenReturn(Map.of(
                MetricAvailabilityService.SeriesKey.HTTP_COUNT, false,
                MetricAvailabilityService.SeriesKey.JVM_HEAP, true));

        Map<String, Object> evidence = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));

        assertThat(evidence).containsEntry("metrics.series.httpCount", false)
                .containsEntry("metrics.series.jvmHeap", true)
                .doesNotContainKeys("metrics.series.httpBucket", "metrics.series.agroal",
                        "metrics.series.events", "metrics.series.cluster", "metrics.http.histogram.available",
                        "metrics.http.histogram.requiredButMissing", "metrics.http.noTrafficInWindow");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void observedHistogramPresenceSurvivesLaterAbort(boolean present) {
        Target target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenReturn(summaryWithHistogram(
                Map.of("COLLECTION_BUDGET", MetricAvailability.NOT_AVAILABLE,
                        "HTTP_BUCKET_SERIES", MetricAvailability.AVAILABLE), present));

        Map<String, Object> evidence = collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));

        assertThat(evidence).containsEntry("metrics.collection.complete", false)
                .containsEntry("metrics.http.histogram.available", present)
                .containsEntry("metrics.http.histogram.requiredButMissing", !present)
                .doesNotContainKeys("metrics.http.noTrafficInWindow", "metrics.slo.p99Exceeded");
        verifyNoInteractions(availabilityService, inventoryService, serviceMonitorProbe);
    }

    @Test
    void observedStaleMetricSurvivesAbortedCollection() {
        Target target = targetWithInfrastructure();
        when(metricsService.summaryForAssessment(target)).thenReturn(summaryWithHistogram(
                Map.of("COLLECTION_BUDGET", MetricAvailability.NOT_AVAILABLE,
                        "HTTP_BUCKET_SERIES", MetricAvailability.UNKNOWN,
                        "HTTP_ERROR_RATE", MetricAvailability.STALE), false));
        assertThat(collector.collect(target)).anySatisfy(evidence -> {
            assertThat(evidence.key()).isEqualTo("metrics.stale.present");
            assertThat(evidence.value()).isEqualTo(true);
        });
        verifyNoInteractions(availabilityService, inventoryService, serviceMonitorProbe);
    }

    private static PerformanceSummary summaryWithHistogram(Map<String, MetricAvailability> availability, boolean present) {
        return new PerformanceSummary(
                "lab-a", MetricWindow.W_15M, MetricsProviderStatus.AVAILABLE, "PROMETHEUS", Instant.now(),
                new PerformanceSummary.Http(null, null, null, null, null, null, null, present, false),
                PerformanceSummary.Database.empty(), PerformanceSummary.Jvm.empty(),
                PerformanceSummary.Cache.empty(), PerformanceSummary.Cluster.empty(),
                PerformanceSummary.Runtime.empty(), availability);
    }

    private static Target targetWithInfrastructure() {
        Target base = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        return new Target(base.id(), base.displayName(), base.type(), base.environment(), true, base.keycloak(),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "cluster", "namespace", "ref"),
                base.observability(), Map.of());
    }

    private Map<String, Object> databaseEvidence(Double current, Double average, Double max,
            Map<String, MetricAvailability> availability) {
        when(performanceConfig.dbAwaitingCritical()).thenReturn(OptionalInt.of(20));
        Target target = target(new ObservabilityTargetConfiguration(
                "PROMETHEUS", null, "http://localhost:9090", null, null, "NAMESPACE"));
        when(metricsService.summaryForAssessment(target)).thenReturn(new PerformanceSummary(
                "lab-a", MetricWindow.W_15M, MetricsProviderStatus.AVAILABLE, "PROMETHEUS", Instant.now(),
                PerformanceSummary.Http.empty(),
                new PerformanceSummary.Database(null, null, current, average, max, null),
                PerformanceSummary.Jvm.empty(), PerformanceSummary.Cache.empty(),
                PerformanceSummary.Cluster.empty(), PerformanceSummary.Runtime.empty(), availability));
        return collector.collect(target).stream()
                .collect(java.util.stream.Collectors.toMap(Evidence::key, Evidence::value));
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
}
