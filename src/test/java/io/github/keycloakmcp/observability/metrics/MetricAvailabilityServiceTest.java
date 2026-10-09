package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.target.*;

class MetricAvailabilityServiceTest {
    private final MetricsProvider provider = mock(MetricsProvider.class);
    private final MetricsProviderFactory factory = mock(MetricsProviderFactory.class);
    private final Target target = target("a", "http://localhost:9090");
    private MetricAvailabilityService service;

    @BeforeEach void setup() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.operationTimeoutMs()).thenReturn(30000);
        when(config.availabilityCacheTtlSeconds()).thenReturn(60);
        when(factory.forTarget(any())).thenReturn(provider);
        when(provider.supported(any())).thenReturn(true);
        when(provider.probeSeries(any(), any(), any())).thenReturn(observation(1));
        service = new MetricAvailabilityService(config, factory);
    }

    @Test void successfulProbesShareBudgetAndCacheObservedFalse() {
        var budget = budget();
        when(provider.probeSeries(eq(target), eq("http_server_requests_seconds_bucket"), same(budget)))
                .thenReturn(observation(0));
        var flags = service.detect(target, budget);
        assertThat(flags).hasSize(6).containsEntry(MetricAvailabilityService.SeriesKey.HTTP_BUCKET, false);
        verify(provider, times(6)).probeSeries(eq(target), any(), same(budget));
        assertThat(service.detect(target, budget())).isEqualTo(flags);
        verify(provider, times(6)).probeSeries(any(), any(), any());
    }

    @Test void failedProbeIsUnknownAndNotCachedAsAbsence() {
        when(provider.probeSeries(any(), eq("http_server_requests_seconds_bucket"), any())).thenReturn(
                SemanticMetricResult.notAvailable("a", null, MetricWindow.W_5M, "test", "Timed out"));
        assertThat(service.detect(target)).doesNotContainKey(MetricAvailabilityService.SeriesKey.HTTP_BUCKET);
        when(provider.probeSeries(any(), eq("http_server_requests_seconds_bucket"), any())).thenReturn(observation(1));
        assertThat(service.detect(target)).containsEntry(MetricAvailabilityService.SeriesKey.HTTP_BUCKET, true);
        verify(provider, times(12)).probeSeries(any(), any(), any());
    }

    @Test void expiryDiscardsLateProbeStopsSweepAndDoesNotPoisonCache() {
        var expired = new AtomicBoolean();
        var budget = mock(MetricsOperationBudget.class);
        when(budget.exhausted()).thenAnswer(inv -> expired.get());
        doAnswer(inv -> { expired.set(true); return observation(0); })
                .when(provider).probeSeries(any(), any(), same(budget));
        assertThat(service.detect(target, budget)).isEmpty();
        verify(provider, times(1)).probeSeries(any(), any(), same(budget));
        assertThat(service.detect(target, budget())).hasSize(6)
                .containsEntry(MetricAvailabilityService.SeriesKey.HTTP_BUCKET, true);
        verify(provider, times(7)).probeSeries(any(), any(), any());
    }

    @Test void expiredBudgetDoesNotEvenConsumeAValidCachedResult() {
        service.detect(target);
        clearInvocations(factory, provider);
        var budget = mock(MetricsOperationBudget.class);
        when(budget.exhausted()).thenReturn(true);
        assertThat(service.detect(target, budget)).isEmpty();
        verifyNoInteractions(factory, provider);
    }

    @Test void interruptionPreventsProbesAndPreservesFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThat(service.detect(target)).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verifyNoInteractions(factory, provider);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void anotherTargetOrChangedConnectionCannotReuseCachedFlags() {
        service.detect(target);
        service.detect(target("b", "http://localhost:9090"));
        service.detect(target("a", "http://localhost:9091"));
        verify(provider, times(18)).probeSeries(any(), any(), any());
    }

    @Test void staleAndNonfiniteProbeValuesRemainUnknown() {
        when(provider.probeSeries(any(), any(), any())).thenReturn(observation(Double.NaN));
        assertThat(service.detect(target)).isEmpty();
        when(provider.probeSeries(any(), any(), any())).thenReturn(SemanticMetricResult.stale(
                "a", null, MetricWindow.W_5M, 1d, "count", "test", 1,
                Instant.now().minusSeconds(600), List.of()));
        assertThat(service.detect(target)).isEmpty();
    }

    private static MetricsOperationBudget budget() {
        return MetricsOperationBudget.start(Duration.ofSeconds(30));
    }

    private static SemanticMetricResult observation(double value) {
        return SemanticMetricResult.available("a", null, MetricWindow.W_5M, value, "count", "test", 1,
                Instant.now(), List.of());
    }

    private static Target target(String id, String endpoint) {
        return new Target(TargetId.of(id), id, TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://localhost", "realm", "client", "ref", null), null,
                new ObservabilityTargetConfiguration("PROMETHEUS", null, endpoint, null, null, "NAMESPACE"), Map.of());
    }
}
