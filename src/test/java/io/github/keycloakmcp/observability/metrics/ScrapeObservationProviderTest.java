package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.*;

class ScrapeObservationProviderTest {
    private final PrometheusApiClient api = mock(PrometheusApiClient.class);
    private final MetricsEndpointResolver resolver = mock(MetricsEndpointResolver.class);
    private final CredentialProvider credentials = mock(CredentialProvider.class);
    private final MetricsConfig config = mock(MetricsConfig.class);
    private final Target target = new Target(TargetId.of("scrape-a"), "Scrape A", TargetType.KEYCLOAK,
            TargetEnvironment.DEV, true, new KeycloakTargetConfiguration("http://127.0.0.1", "realm", "client", "kc-ref", null),
            null, new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://127.0.0.1:9090", "metrics-ref", "workloads", "NAMESPACE"),
            Map.of("job", "shared-job", "target_id", "foreign-tag"));
    private final Instant evaluated = Instant.now().minusMillis(5);
    private PrometheusMetricsProvider provider;

    @BeforeEach void setup() {
        when(config.operationTimeoutMs()).thenReturn(30000);
        when(config.maxSeries()).thenReturn(3);
        when(config.staleAfter()).thenReturn("5m");
        when(resolver.resolve(target)).thenReturn(Optional.of("http://127.0.0.1:9090"));
        when(resolver.queryContext(target)).thenReturn(new MetricsQueryContext("wrong-context-id", "workloads", MetricsScope.NAMESPACE,
                Map.of("target_id", "foreign-tag", "job", "shared-job", "service", "kc-service", "pod", "kc-pod"), HttpMetricScope.ALL));
        when(credentials.getMetricsCredentials("metrics-ref")).thenReturn(MetricsCredentials.bearer("test-only-token", null, false));
        provider = new PrometheusMetricsProvider(api, resolver, credentials, config);
    }

    @Test void oneFailedScrapeIsNotHealthyJustBecauseItsSeriesExists() {
        respond(series("one", 0d));
        var result = provider.probeScrape(target);
        assertThat(result.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        assertThat(result.observed()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.evaluatedAt()).isEqualTo(evaluated);
    }

    @Test void mixedScrapesRetainFailedCountRegardlessOfOrder() {
        for (List<MetricSeries> series : List.of(List.of(series("one", 1d), series("two", 0d)),
                List.of(series("two", 0d), series("one", 1d)))) {
            respond(series);
            var result = provider.probeScrape(target);
            assertThat(result.availability()).isEqualTo(MetricAvailability.AVAILABLE);
            assertThat(result.observed()).isEqualTo(2);
            assertThat(result.successful()).isEqualTo(1);
            assertThat(result.failed()).isEqualTo(1);
        }
    }

    @Test void allObservedOnesAreSuccessfulButNoExpectedCoverageIsInvented() {
        respond(series("one", 1d), series("two", 1d));
        var result = provider.probeScrape(target);
        assertThat(result.observed()).isEqualTo(2);
        assertThat(result.successful()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.reason()).isNull();
        assertThat(result.toString()).doesNotContain("kc-service", "kc-pod", "shared-job", "instance", "http://");
    }

    @Test void queryIsUnaggregatedAndCannotTrustTagOrContextTargetId() {
        respond(series("one", 1d));
        provider.probeScrape(target);
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(api).query(any(), query.capture(), any(), any(), any(), any());
        assertThat(query.getValue()).startsWith("up{").endsWith("}")
                .contains("namespace=\"workloads\"", "target_id=\"scrape-a\"", "job=\"shared-job\"",
                        "service=\"kc-service\"", "pod=\"kc-pod\"")
                .doesNotContain("count(", "sum(", "max(", "foreign-tag", "wrong-context-id");
    }

    @Test void emptyAndNullResponsesAreUnavailableNotZeroSuccessfulTargets() {
        respond(List.of());
        assertUnavailable(provider.probeScrape(target), "NO_TIME_SERIES");
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(null);
        assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(new PrometheusApiClient.Response(
                PrometheusApiClient.Status.OK, "ignored", null));
        assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
    }

    @Test void invalidValuesInvalidateTheWholeVectorRatherThanSelectingHealthySubset() {
        for (Double invalid : new Double[]{null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1d, 0.5d, 2d}) {
            respond(series("one", 1d), series("two", invalid));
            assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
        }
    }

    @Test void duplicateSeriesCannotSupplyFabricatedRedundancy() {
        respond(series("one", 1d), series("one", 1d));
        assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
    }

    @Test void staleEvaluationCannotSupplyCounts() {
        respond(series("one", 1d, evaluated.minusSeconds(600), labels("one")));
        var result = provider.probeScrape(target);
        assertThat(result.availability()).isEqualTo(MetricAvailability.STALE);
        assertThat(result.observed()).isNull();
        assertThat(result.successful()).isNull();
        assertThat(result.failed()).isNull();
        assertThat(result.evaluatedAt()).isNull();
        assertThat(result.reason()).isEqualTo("STALE");
    }

    @Test void missingNegativeFutureOrInconsistentEvaluationTimesInvalidateResponse() {
        for (Instant timestamp : new Instant[]{null, Instant.ofEpochSecond(-1), Instant.now().plusSeconds(60)}) {
            respond(series("one", 1d, timestamp, labels("one")));
            assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
        }
        respond(series("one", 1d), series("two", 1d, evaluated.minusSeconds(1), labels("two")));
        assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
    }

    @Test void everyMandatoryLabelAndNamespaceMustMatch() {
        for (String key : List.of("target_id", "namespace", "job", "service", "pod")) {
            var wrong = new LinkedHashMap<>(labels("two"));
            wrong.put(key, "foreign-canary");
            respond(series("one", 1d), series("two", 1d, evaluated, wrong));
            assertUnavailable(provider.probeScrape(target), "SCOPE_MISMATCH");
            wrong.remove(key);
            respond(series("two", 1d, evaluated, wrong));
            assertUnavailable(provider.probeScrape(target), "SCOPE_MISMATCH");
        }
    }

    @Test void cardinalityLimitCannotReturnAFavorableSubset() {
        respond(series("one", 1d), series("two", 1d), series("three", 1d), series("four", 0d));
        assertUnavailable(provider.probeScrape(target), "LIMIT_EXCEEDED");
    }

    @Test void unsupportedShapeAndMismatchingSampleLabelsCannotBecomeScrapeEvidence() {
        var labels = labels("one");
        for (MetricSeries malformed : List.of(
                new MetricSeries("not_up", labels, List.of(new MetricSample(evaluated, 1d, labels))),
                new MetricSeries("up", labels, List.of()),
                new MetricSeries("up", labels, List.of(new MetricSample(evaluated, 1d, labels), new MetricSample(evaluated, 1d, labels))),
                new MetricSeries("up", labels, List.of(new MetricSample(evaluated, 1d, Map.of()))))) {
            respond(malformed);
            assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
        }
        var nullItem = new ArrayList<MetricSeries>();
        nullItem.add(null);
        respond(nullItem);
        assertUnavailable(provider.probeScrape(target), "INVALID_SAMPLE");
    }

    @Test void upstreamStatusesAndMessagesNeverExposeProviderDetails() {
        for (var status : PrometheusApiClient.Status.values()) {
            if (status == PrometheusApiClient.Status.OK) continue;
            when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(
                    PrometheusApiClient.Response.of(status, "https://private-provider/?token=secret-canary"));
            var result = provider.probeScrape(target);
            assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
            assertThat(result.observed()).isNull();
            assertThat(result.toString()).doesNotContain("private-provider", "secret-canary");
        }
    }

    @Test void configuredCredentialFailureNeverStartsTransport() {
        when(credentials.getMetricsCredentials("metrics-ref")).thenThrow(new IllegalStateException("credential-private-canary"));
        assertUnavailable(provider.probeScrape(target), "UNAUTHORIZED");
        verifyNoInteractions(api);
    }

    @Test void expiredOrInterruptedBudgetStartsNoLookupOrTransport() {
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofMillis(1), clock::get);
        clock.set(Duration.ofMillis(1).toNanos());
        assertUnavailable(provider.probeScrape(target, budget), "OPERATION_BUDGET_EXCEEDED");
        try {
            Thread.currentThread().interrupt();
            assertUnavailable(provider.probeScrape(target), "OPERATION_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
        verifyNoInteractions(resolver, credentials, api);
    }

    @Test void successfulButLateReplyCannotEscapeTheBudget() {
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofSeconds(1), clock::get);
        when(api.query(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            clock.set(Duration.ofSeconds(1).toNanos());
            return new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok", List.of(series("one", 1d)));
        });
        assertUnavailable(provider.probeScrape(target, budget), "OPERATION_BUDGET_EXCEEDED");
    }

    @Test void unconfiguredAndUnsupportedPathsMakeNoRequest() {
        assertThat(provider.probeScrape(null).availability()).isEqualTo(MetricAvailability.NOT_CONFIGURED);
        when(resolver.resolve(target)).thenReturn(Optional.empty());
        assertThat(provider.probeScrape(target).availability()).isEqualTo(MetricAvailability.NOT_CONFIGURED);
        verifyNoInteractions(api, credentials);
        MetricsProvider extension = mock(MetricsProvider.class, CALLS_REAL_METHODS);
        assertUnavailable(extension.probeScrape(target), "SCRAPE_PROBE_UNSUPPORTED");
    }

    @Test void strictQueryEscapesRegisteredSelectorValuesWithoutBroadeningScope() {
        var context = new MetricsQueryContext("actual-target", "ns", MetricsScope.NAMESPACE,
                Map.of("job", "\"} or vector(1) or {", "target_id", "tag-must-not-win"), HttpMetricScope.ALL);
        assertThat(MetricsQueryBuilder.scrapeUp(context)).contains("target_id=\"actual-target\"",
                "job=\"\\\"} or vector(1) or {\"", "namespace=\"ns\"").doesNotContain("tag-must-not-win");
    }

    @Test void observationRecordWithholdsMalformedCountsAndArbitraryReasonText() {
        for (ScrapeObservation result : List.of(
                new ScrapeObservation(MetricAvailability.AVAILABLE, 1, 1, 1, evaluated, null),
                new ScrapeObservation(MetricAvailability.AVAILABLE, 0, 0, 0, evaluated, null),
                new ScrapeObservation(MetricAvailability.AVAILABLE, 1, 1, 0, null, null),
                ScrapeObservation.unavailable("raw-private-canary"))) {
            assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
            assertThat(result.observed()).isNull();
            assertThat(result.successful()).isNull();
            assertThat(result.failed()).isNull();
            assertThat(result.toString()).doesNotContain("raw-private-canary");
        }
    }

    @Test void observationRecordRejectsFutureEvaluationEvenFromCustomProviders() {
        var result = new ScrapeObservation(MetricAvailability.AVAILABLE, 1, 1, 0,
                Instant.now().plusSeconds(60), "private-custom-provider-canary");
        assertUnavailable(result, "INVALID_SAMPLE");
        assertThat(result.toString()).doesNotContain("private-custom-provider-canary");
    }

    private void respond(MetricSeries... series) { respond(List.of(series)); }
    private void respond(List<MetricSeries> series) {
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(
                new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ignored", series));
    }
    private MetricSeries series(String instance, Double value) { return series(instance, value, evaluated, labels(instance)); }
    private MetricSeries series(String instance, Double value, Instant timestamp, Map<String, String> labels) {
        return new MetricSeries("up", labels, List.of(new MetricSample(timestamp, value, labels)));
    }
    private Map<String, String> labels(String instance) {
        return Map.of("__name__", "up", "instance", instance, "namespace", "workloads", "target_id", "scrape-a",
                "job", "shared-job", "service", "kc-service", "pod", "kc-pod");
    }
    private static void assertUnavailable(ScrapeObservation result, String reason) {
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.observed()).isNull();
        assertThat(result.successful()).isNull();
        assertThat(result.failed()).isNull();
        assertThat(result.evaluatedAt()).isNull();
        assertThat(result.reason()).isEqualTo(reason);
    }
}
