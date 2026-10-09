package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.*;

class MetricsProviderFailureTest {
    private final PrometheusApiClient api = mock(PrometheusApiClient.class);
    private final MetricsEndpointResolver resolver = mock(MetricsEndpointResolver.class);
    private final MetricsConfig config = mock(MetricsConfig.class);
    private final Target target = new Target(TargetId.of("failure-target"), "failure-target", TargetType.KEYCLOAK,
            TargetEnvironment.DEV, true, new KeycloakTargetConfiguration("http://127.0.0.1", "realm", "client", "ref", null),
            null, new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://127.0.0.1:9090", null, null, "NAMESPACE"), Map.of("target_id", "failure-target"));
    private PrometheusMetricsProvider provider;

    @BeforeEach void setup() {
        when(resolver.resolve(target)).thenReturn(Optional.of("http://127.0.0.1:9090"));
        when(resolver.queryContext(target)).thenReturn(MetricsQueryContext.fromTarget(target));
        when(config.operationTimeoutMs()).thenReturn(30000);
        when(config.maxRange()).thenReturn("24h"); when(config.staleAfter()).thenReturn("5m");
        when(config.maxSeries()).thenReturn(2); when(config.maxPoints()).thenReturn(2);
        provider = new PrometheusMetricsProvider(api, resolver, mock(CredentialProvider.class), config);
    }

    @Test void transportAndProviderMessagesAreNotCopiedToResults() {
        for (var status : List.of(PrometheusApiClient.Status.SERVER_ERROR, PrometheusApiClient.Status.NETWORK_ERROR)) {
            when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(PrometheusApiClient.Response.of(status, "private-provider-canary"));
            var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
            assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
            assertThat(result.reason()).doesNotContain("private-provider-canary");
            assertThat(result.value()).isNull(); assertThat(result.usableForFindings()).isFalse();
        }
    }

    @Test void responseLimitIsDegradedAndNotUsableForFindings() {
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(PrometheusApiClient.Response.of(PrometheusApiClient.Status.LIMIT_EXCEEDED, "ignored"));
        assertThat(provider.status(target)).isEqualTo(MetricsProviderStatus.DEGRADED);
        var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        assertThat(result.reason()).isEqualTo("LIMIT_EXCEEDED"); assertThat(result.usableForFindings()).isFalse();
    }

    @Test void rangeFailureNeverFabricatesAverageOrMaximumFromInstantValue() {
        when(api.queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PrometheusApiClient.Response.of(PrometheusApiClient.Status.LIMIT_EXCEEDED, "ignored"));
        var result = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.reason()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(result.current()).isNull(); assertThat(result.average()).isNull(); assertThat(result.max()).isNull();
        verify(api, never()).query(any(), any(), any(), any(), any(), any());
    }

    @Test void actualRangeSampleCountIsCheckedRatherThanTrustingRequestedStep() {
        when(api.queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok", List.of(series(1d,2d,3d))));
        var result = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.average()).isNull(); assertThat(result.reason()).isEqualTo("LIMIT_EXCEEDED");
    }

    @Test void instantSampleCannotImplementTheGenericTemporalFallback() {
        var instant = SemanticMetricResult.available("failure-target", SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M,
                42d, "count", "test", 1, Instant.now(), List.of());
        var result = RangeMetricSummary.fromInstant(instant);
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.average()).isNull(); assertThat(result.max()).isNull();
    }

    @Test void nonFiniteInstantSamplesAreNotAvailable() {
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(new PrometheusApiClient.Response(
                PrometheusApiClient.Status.OK, "ok", List.of(series(Double.POSITIVE_INFINITY))));
        var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.value()).isNull();
    }

    @Test void validBoundedRangeRemainsAvailable() {
        when(api.queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> rangeResponse(inv.getArgument(2), inv.getArgument(4), 1d, 3d));
        var result = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        assertThat(result.current()).isEqualTo(3d); assertThat(result.average()).isEqualTo(2d); assertThat(result.max()).isEqualTo(3d);
    }

    @Test void rangeWithMissingFirstMiddleOrFinalEvaluationIsUnavailable() {
        when(config.maxPoints()).thenReturn(3);
        for (int missing = 0; missing < 3; missing++) {
            final int omitted = missing;
            doAnswer(inv -> {
                var response = rangeResponse(inv.getArgument(2), inv.getArgument(4), 0d, 0d, 0d);
                var samples = new ArrayList<>(response.series().getFirst().samples());
                samples.remove(omitted);
                return new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok",
                        List.of(new MetricSeries("metric", Map.of(), samples)));
            }).when(api).queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertRangeUnavailable(SemanticMetricResult.REASON_TEMPORAL_COVERAGE);
        }
    }

    @Test void rangeCannotUseAFiniteSubsetOrAnEmptySeries() {
        for (Double invalid : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            doAnswer(inv -> rangeResponse(inv.getArgument(2), inv.getArgument(4), 0d, invalid))
                    .when(api).queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertRangeUnavailable(SemanticMetricResult.REASON_INVALID_SAMPLE);
        }
        doReturn(new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok", List.of(series())))
                .when(api).queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertRangeUnavailable(SemanticMetricResult.REASON_TEMPORAL_COVERAGE);
    }

    @Test void reversedDuplicateOffGridOldOrFutureRangePointsAreUnavailable() {
        for (int variant = 0; variant < 5; variant++) {
            final int kind = variant;
            doAnswer(inv -> {
                Instant start = inv.getArgument(2), end = inv.getArgument(3);
                List<MetricSample> samples = switch (kind) {
                    case 0 -> List.of(sample(end, 0d), sample(start, 0d));
                    case 1 -> List.of(sample(start, 0d), sample(start, 0d));
                    case 2 -> List.of(sample(start.plusMillis(1), 0d), sample(end, 0d));
                    case 3 -> List.of(sample(start.minusSeconds(1), 0d), sample(end, 0d));
                    default -> List.of(sample(start, 0d), sample(end.plusSeconds(1), 0d));
                };
                return new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok",
                        List.of(new MetricSeries("metric", Map.of(), samples)));
            }).when(api).queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any());
            assertRangeUnavailable(SemanticMetricResult.REASON_TEMPORAL_COVERAGE);
        }
    }

    @Test void unexpectedMultipleRangeAggregatesAreNotAveragedOrOrderDependent() {
        when(api.queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            var first = rangeResponse(inv.getArgument(2), inv.getArgument(4), 0d, 0d).series().getFirst();
            var second = rangeResponse(inv.getArgument(2), inv.getArgument(4), 20d, 20d).series().getFirst();
            return new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok", List.of(first, second));
        });
        assertRangeUnavailable(SemanticMetricResult.REASON_UNEXPECTED_SERIES);
    }

    @Test void allFiniteExtremeSamplesDoNotOverflowTheAverage() {
        when(api.queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> rangeResponse(inv.getArgument(2), inv.getArgument(4), Double.MAX_VALUE, Double.MAX_VALUE));
        var result = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        assertThat(result.average()).isEqualTo(Double.MAX_VALUE);
    }

    @Test void instantFutureMissingTimestampAndMultipleSamplesDoNotBecomeCurrentValues() {
        for (MetricSeries invalid : List.of(
                new MetricSeries("metric", Map.of(), List.of(sample(Instant.now().plusSeconds(60), 0d))),
                new MetricSeries("metric", Map.of(), List.of(sample(null, 0d))),
                series(0d, 0d))) {
            when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(
                    new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok", List.of(invalid)));
            var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
            assertThat(result.reason()).isEqualTo(SemanticMetricResult.REASON_INVALID_SAMPLE);
            assertThat(result.value()).isNull();
            assertThat(result.usableForFindings()).isFalse();
        }
    }

    @Test void instantMixedSeriesCannotSelectFiniteOrFreshFavorableSubset() {
        for (Double other : new Double[]{0d, null, Double.NaN, Double.POSITIVE_INFINITY}) {
            when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(new PrometheusApiClient.Response(
                    PrometheusApiClient.Status.OK, "ok", List.of(series(0d), series(other))));
            var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
            assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
            assertThat(result.value()).isNull();
        }
    }

    @Test void oldValidInstantRemainsStaleNotAvailable() {
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(new PrometheusApiClient.Response(
                PrometheusApiClient.Status.OK, "ok", List.of(new MetricSeries("metric", Map.of(),
                        List.of(sample(Instant.now().minusSeconds(600), 42d))))));
        var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.STALE);
        assertThat(result.value()).isEqualTo(42d);
        assertThat(result.usableForFindings()).isFalse();
    }

    @Test void incompleteResponseReasonIsAllowlistedAndProviderStatusDegrades() {
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(
                PrometheusApiClient.Response.of(PrometheusApiClient.Status.INCOMPLETE, "private-provider-canary"));
        assertThat(provider.status(target)).isEqualTo(MetricsProviderStatus.DEGRADED);
        var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        assertThat(result.reason()).isEqualTo(SemanticMetricResult.REASON_TEMPORAL_COVERAGE);
        assertThat(result.value()).isNull();
    }

    @Test void nonFiniteDomainResultNeverDrivesFindingsEvenIfMarkedAvailable() {
        for (Double invalid : new Double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var result = SemanticMetricResult.available("failure-target", SemanticMetric.DB_POOL_ACTIVE,
                    MetricWindow.W_5M, invalid, "count", "test", 1, Instant.now(), List.of());
            assertThat(result.usableForFindings()).isFalse();
        }
    }

    private void assertRangeUnavailable(String reason) {
        var result = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M);
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.reason()).isEqualTo(reason);
        assertThat(result.current()).isNull();
        assertThat(result.average()).isNull();
        assertThat(result.max()).isNull();
    }

    private static PrometheusApiClient.Response rangeResponse(Instant start, Duration step, Double... values) {
        List<MetricSample> samples = new ArrayList<>();
        for (int i = 0; i < values.length; i++) samples.add(sample(start.plus(step.multipliedBy(i)), values[i]));
        return new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok",
                List.of(new MetricSeries("metric", Map.of(), samples)));
    }

    private static MetricSample sample(Instant timestamp, Double value) {
        return new MetricSample(timestamp, value, Map.of());
    }

    private static MetricSeries series(Double... values) {
        return new MetricSeries("metric", Map.of(), java.util.Arrays.stream(values)
                .map(value -> new MetricSample(Instant.now(), value, Map.of())).toList());
    }
}
