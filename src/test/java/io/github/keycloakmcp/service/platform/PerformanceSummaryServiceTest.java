package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.same;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.observability.metrics.*;
import io.github.keycloakmcp.target.*;

class PerformanceSummaryServiceTest {
    private final MetricsProvider provider = mock(MetricsProvider.class);
    private final MetricAvailabilityService availability = mock(MetricAvailabilityService.class);
    private final Target target = new Target(TargetId.of("summary-target"), "summary-target", TargetType.KEYCLOAK,
            TargetEnvironment.DEV, true, new KeycloakTargetConfiguration("http://127.0.0.1", "realm", "client", "ref", null),
            null, new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://127.0.0.1:9090", null, null, "NAMESPACE"), Map.of());
    private PerformanceSummaryService service;

    @Test void publicSummaryKeepsParentDeadlineAndRejectsLateQueryWithoutFurtherFanout() {
        var clock = new AtomicLong();
        var parent = new CollectionBudget(Duration.ofMillis(100), clock::get);
        var ordered = SemanticMetric.values();
        when(provider.query(eq(target), any(), any(), any())).thenAnswer(inv -> {
            var metric = (SemanticMetric) inv.getArgument(1);
            if (metric == ordered[1]) clock.set(Duration.ofMillis(100).toNanos());
            return available(metric, 42d);
        });
        try (var scope = CollectionBudget.open(target.id().value(), parent)) {
            var summary = service.summarize(target, MetricWindow.W_5M);
            assertThat(summary.availability()).containsEntry("COLLECTION_BUDGET", MetricAvailability.NOT_AVAILABLE)
                    .containsEntry(ordered[0].name(), MetricAvailability.AVAILABLE)
                    .containsEntry(ordered[1].name(), MetricAvailability.NOT_AVAILABLE);
            assertThat(summary.providerStatus()).isEqualTo(MetricsProviderStatus.DEGRADED);
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
        verify(provider, times(2)).query(eq(target), any(), any(), any());
        verify(provider, never()).queryRange(any(), any(), any(), any());
        verifyNoInteractions(availability);
        assertThat(CollectionBudget.current()).isNull();
    }

    @BeforeEach void setup() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.maxRange()).thenReturn("24h");
        when(config.operationTimeoutMs()).thenReturn(30000);
        MetricsProviderFactory factory = mock(MetricsProviderFactory.class);
        when(factory.forTarget(target)).thenReturn(provider);
        when(provider.status(eq(target), any())).thenReturn(MetricsProviderStatus.AVAILABLE);
        when(provider.query(eq(target), any(), any(), any())).thenAnswer(inv -> missing(inv.getArgument(1), "Unavailable"));
        when(provider.queryRange(eq(target), any(), any(), any())).thenReturn(
                RangeMetricSummary.notAvailable(SemanticMetricResult.REASON_TEMPORAL_COVERAGE));
        when(availability.detect(eq(target), any())).thenReturn(Map.of(MetricAvailabilityService.SeriesKey.HTTP_BUCKET, true));
        service = new PerformanceSummaryService(factory, availability, config);
    }

    @Test void histogramAndKnownEmptyPercentileNeedObservedZeroTraffic() {
        when(query(SemanticMetric.HTTP_P99_LATENCY))
                .thenReturn(missing(SemanticMetric.HTTP_P99_LATENCY, SemanticMetricResult.REASON_NO_SERIES));
        assertThat(service.summarize(target, MetricWindow.W_5M).http().noTrafficInWindow()).isFalse();
        when(query(SemanticMetric.HTTP_REQUEST_RATE))
                .thenReturn(available(SemanticMetric.HTTP_REQUEST_RATE, 0d));
        var summary = service.summarize(target, MetricWindow.W_5M);
        assertThat(summary.http().noTrafficInWindow()).isTrue();
        assertThat(summary.http().p99Ms()).isNull();
    }

    @Test void invalidBackendTemporalAndUnknownReasonsNeverBecomeNoTraffic() {
        when(query(SemanticMetric.HTTP_REQUEST_RATE))
                .thenReturn(available(SemanticMetric.HTTP_REQUEST_RATE, 0d));
        for (String reason : new String[]{null, "Timed out", "Malformed response", "No time series private-canary",
                SemanticMetricResult.REASON_TEMPORAL_COVERAGE, SemanticMetricResult.REASON_INVALID_SAMPLE,
                SemanticMetricResult.REASON_UNEXPECTED_SERIES, SemanticMetricResult.REASON_SERIES_LIMIT}) {
            when(query(SemanticMetric.HTTP_P99_LATENCY))
                    .thenReturn(missing(SemanticMetric.HTTP_P99_LATENCY, reason));
            var summary = service.summarize(target, MetricWindow.W_5M);
            assertThat(summary.http().noTrafficInWindow()).as(reason).isFalse();
            assertThat(summary.http().p99Ms()).isNull();
        }
    }

    @Test void observedTrafficAndNonfiniteTrafficCannotBecomeNoTraffic() {
        when(query(SemanticMetric.HTTP_P99_LATENCY))
                .thenReturn(missing(SemanticMetric.HTTP_P99_LATENCY, SemanticMetricResult.REASON_NO_SERIES));
        for (Double value : new Double[]{1d, -1d, Double.NaN, Double.POSITIVE_INFINITY}) {
            when(query(SemanticMetric.HTTP_REQUEST_RATE))
                    .thenReturn(available(SemanticMetric.HTTP_REQUEST_RATE, value));
            assertThat(service.summarize(target, MetricWindow.W_5M).http().noTrafficInWindow()).isFalse();
        }
    }

    @Test void staleZeroTrafficCannotProveAnIdleWindow() {
        when(query(SemanticMetric.HTTP_P99_LATENCY))
                .thenReturn(missing(SemanticMetric.HTTP_P99_LATENCY, SemanticMetricResult.REASON_NO_SERIES));
        when(query(SemanticMetric.HTTP_REQUEST_RATE)).thenReturn(
                SemanticMetricResult.stale(target.id().value(), SemanticMetric.HTTP_REQUEST_RATE, MetricWindow.W_5M,
                        0d, "rps", "test", 1, Instant.now().minusSeconds(600), List.of()));
        assertThat(service.summarize(target, MetricWindow.W_5M).http().noTrafficInWindow()).isFalse();
    }

    @Test void incompleteWindowRetainsAnInstantCurrentButNeverInventsWindowStatistics() {
        when(query(SemanticMetric.DB_POOL_AWAITING))
                .thenReturn(available(SemanticMetric.DB_POOL_AWAITING, 0d));
        var summary = service.summarize(target, MetricWindow.W_5M);
        assertThat(summary.database().poolAwaiting()).isZero();
        assertThat(summary.database().poolAwaitingAverage()).isNull();
        assertThat(summary.database().poolAwaitingMax()).isNull();
        assertThat(summary.availability().get("DB_POOL_AWAITING_RANGE")).isEqualTo(MetricAvailability.NOT_AVAILABLE);
    }

    @Test void oneBudgetReachesEveryChildAndIndependentCallsCreateAnother() {
        MetricsOperationBudget budget = MetricsOperationBudget.start(java.time.Duration.ofSeconds(30));
        var summary = service.summarize(target, MetricWindow.W_5M, budget);
        verify(provider).status(target, budget);
        verify(provider, times(SemanticMetric.values().length)).query(eq(target), any(), eq(MetricWindow.W_5M), same(budget));
        verify(availability).detect(target, budget);
        verify(provider).queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M, budget);
        assertThat(summary.availability().get("COLLECTION_BUDGET")).isEqualTo(MetricAvailability.AVAILABLE);
        var captor = org.mockito.ArgumentCaptor.forClass(MetricsOperationBudget.class);
        service.summarize(target, MetricWindow.W_5M);
        verify(provider, times(2)).status(eq(target), captor.capture());
        assertThat(captor.getAllValues().get(1)).isNotSameAs(budget);
    }

    @Test void expiredBudgetDoesNotStartProviderOrProbeWork() {
        MetricsOperationBudget budget = mock(MetricsOperationBudget.class);
        when(budget.exhausted()).thenReturn(true);
        when(budget.reason()).thenReturn("OPERATION_BUDGET_EXCEEDED");
        var summary = service.summarize(target, MetricWindow.W_5M, budget);
        verifyNoInteractions(provider, availability);
        assertThat(summary.providerStatus()).isEqualTo(MetricsProviderStatus.DEGRADED);
        assertThat(summary.availability().get("COLLECTION_BUDGET")).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(summary.http().requestRate()).isNull();
        assertThat(summary.http().noTrafficInWindow()).isFalse();
    }

    @Test void expiryDuringQueryPreservesEarlierValuesRejectsLateValueAndStopsFanout() {
        AtomicBoolean expired = new AtomicBoolean();
        MetricsOperationBudget budget = mock(MetricsOperationBudget.class);
        when(budget.exhausted()).thenAnswer(inv -> expired.get());
        when(budget.reason()).thenReturn("OPERATION_BUDGET_EXCEEDED");
        var ordered = SemanticMetric.values();
        doAnswer(inv -> {
            SemanticMetric metric = inv.getArgument(1);
            if (metric == ordered[1]) expired.set(true);
            return available(metric, 42d);
        }).when(provider).query(eq(target), any(), any(), same(budget));
        var summary = service.summarize(target, MetricWindow.W_5M, budget);
        assertThat(summary.availability().get(ordered[0].name())).isEqualTo(MetricAvailability.AVAILABLE);
        assertThat(summary.availability().get(ordered[1].name())).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(summary.providerStatus()).isEqualTo(MetricsProviderStatus.DEGRADED);
        assertThat(summary.http().noTrafficInWindow()).isFalse();
        verify(provider, times(2)).query(eq(target), any(), any(), same(budget));
        verify(provider, never()).queryRange(any(), any(), any(), any());
        verifyNoInteractions(availability);
    }

    @Test void interruptedCallerKeepsFlagAndDoesNoProviderWork() {
        Thread.currentThread().interrupt();
        try {
            var summary = service.summarize(target, MetricWindow.W_5M);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(summary.providerStatus()).isEqualTo(MetricsProviderStatus.DEGRADED);
            verifyNoInteractions(provider, availability);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void unknownHistogramCannotProveNoTraffic() {
        when(availability.detect(eq(target), any())).thenReturn(Map.of());
        when(query(SemanticMetric.HTTP_REQUEST_RATE)).thenReturn(available(SemanticMetric.HTTP_REQUEST_RATE, 0));
        when(query(SemanticMetric.HTTP_P99_LATENCY)).thenReturn(missing(SemanticMetric.HTTP_P99_LATENCY,
                SemanticMetricResult.REASON_NO_SERIES));
        var summary = service.summarize(target, MetricWindow.W_5M);
        assertThat(summary.availability().get("HTTP_BUCKET_SERIES")).isEqualTo(MetricAvailability.UNKNOWN);
        assertThat(summary.http().noTrafficInWindow()).isFalse();
    }

    private SemanticMetricResult query(SemanticMetric metric) {
        return provider.query(eq(target), eq(metric), eq(MetricWindow.W_5M), any(MetricsOperationBudget.class));
    }

    private SemanticMetricResult missing(SemanticMetric metric, String reason) {
        return SemanticMetricResult.notAvailable(target.id().value(), metric, MetricWindow.W_5M, "test", reason);
    }

    private SemanticMetricResult available(SemanticMetric metric, double value) {
        return SemanticMetricResult.available(target.id().value(), metric, MetricWindow.W_5M, value, "unit",
                "test", 1, Instant.now(), List.of());
    }
}
