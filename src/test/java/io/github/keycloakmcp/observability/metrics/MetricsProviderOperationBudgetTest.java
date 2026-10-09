package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.*;

class MetricsProviderOperationBudgetTest {
    private final PrometheusApiClient api = mock(PrometheusApiClient.class);
    private final MetricsEndpointResolver resolver = mock(MetricsEndpointResolver.class);
    private final CredentialProvider credentials = mock(CredentialProvider.class);
    private final MetricsConfig config = mock(MetricsConfig.class);
    private final AtomicLong clock = new AtomicLong();
    private final Target target = target("target-a", "metrics-a");
    private final MetricsCredentials auth = MetricsCredentials.bearer("fixture-a", null, false);
    private PrometheusMetricsProvider provider;

    @BeforeEach void setup() {
        when(config.operationTimeoutMs()).thenReturn(30000);
        when(config.maxRange()).thenReturn("24h");
        when(config.maxSeries()).thenReturn(10);
        when(config.maxPoints()).thenReturn(10);
        when(config.staleAfter()).thenReturn("5m");
        when(resolver.resolve(any())).thenReturn(Optional.of("http://127.0.0.1:9090"));
        when(resolver.queryContext(any())).thenAnswer(inv -> MetricsQueryContext.fromTarget(inv.getArgument(0)));
        when(credentials.getMetricsCredentials("metrics-a")).thenReturn(auth);
        when(api.query(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> success());
        provider = new PrometheusMetricsProvider(api, resolver, credentials, config);
    }

    @Test void exhaustedOperationDoesNoResolutionCredentialsOrTransport() {
        MetricsOperationBudget budget = budget();
        expire();
        assertThat(provider.status(target, budget)).isEqualTo(MetricsProviderStatus.DEGRADED);
        assertAborted(provider.query(target, SemanticMetric.JVM_HEAP_USED, MetricWindow.W_5M, budget));
        assertAborted(provider.probeSeries(target, "jvm_memory_used_bytes", budget));
        assertThat(provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M, budget).reason())
                .isEqualTo(MetricsOperationBudget.REASON_EXCEEDED);
        assertThat(provider.queryCategory(target, MetricCategory.HTTP, MetricWindow.W_5M, budget))
                .hasSize(MetricsCatalog.forCategory(MetricCategory.HTTP).size()).allSatisfy(this::assertAborted);
        verifyNoInteractions(api, credentials, resolver);
    }

    @Test void interruptedOperationKeepsInterruptFlagAndDoesNoWork() {
        MetricsOperationBudget budget = budget();
        try {
            Thread.currentThread().interrupt();
            var result = provider.query(target, SemanticMetric.JVM_HEAP_USED, MetricWindow.W_5M, budget);
            assertThat(result.reason()).isEqualTo(MetricsOperationBudget.REASON_INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(provider.status(target, budget)).isEqualTo(MetricsProviderStatus.DEGRADED);
            verifyNoInteractions(api, credentials, resolver);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void credentialResolutionThatExhaustsBudgetCannotStartTransport() {
        when(credentials.getMetricsCredentials("metrics-a")).thenAnswer(inv -> { expire(); return auth; });
        assertAborted(provider.query(target, SemanticMetric.JVM_HEAP_USED, MetricWindow.W_5M, budget()));
        verifyNoInteractions(api);
    }

    @Test void expiredEndpointResolutionCannotStartCredentialResolution() {
        when(resolver.resolve(target)).thenAnswer(inv -> { expire(); return Optional.of("http://127.0.0.1:9090"); });
        assertAborted(provider.query(target, SemanticMetric.JVM_HEAP_USED, MetricWindow.W_5M, budget()));
        verifyNoInteractions(credentials, api);
    }

    @Test void lateInstantAndProbeSuccessAreDiscarded() {
        when(api.query(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> { expire(); return success(); });
        assertAborted(provider.query(target, SemanticMetric.JVM_HEAP_USED, MetricWindow.W_5M, budget()));
        clock.set(0);
        assertAborted(provider.probeSeries(target, "jvm_memory_used_bytes", budget()));
    }

    @Test void lateReachabilitySuccessIsDegraded() {
        when(api.query(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> { expire(); return success(); });
        assertThat(provider.status(target, budget())).isEqualTo(MetricsProviderStatus.DEGRADED);
    }

    @Test void lateRangeSuccessDoesNotSupplyCurrentOrTemporalNumbers() {
        when(api.queryRange(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> { expire(); return success(); });
        var result = provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M, budget());
        assertThat(result.reason()).isEqualTo(MetricsOperationBudget.REASON_EXCEEDED);
        assertThat(result.current()).isNull();
        assertThat(result.average()).isNull();
        assertThat(result.max()).isNull();
        verify(api, never()).query(any(), any(), any(), any(), any(), any());
    }

    @Test void categoryPreservesCompletedResultAndStopsResolvingAfterExhaustion() {
        AtomicInteger calls = new AtomicInteger();
        MetricsOperationBudget budget = budget();
        when(api.query(any(), any(), any(), any(), any(), same(budget))).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 2) expire();
            return success();
        });
        var results = provider.queryCategory(target, MetricCategory.HTTP, MetricWindow.W_5M, budget);
        assertThat(results).hasSize(MetricsCatalog.forCategory(MetricCategory.HTTP).size());
        assertThat(results.getFirst().usableForFindings()).isTrue();
        assertThat(results.subList(1, results.size())).allSatisfy(this::assertAborted);
        verify(api, times(2)).query(any(), any(), same(auth), any(), any(), same(budget));
        verify(credentials, times(2)).getMetricsCredentials("metrics-a");
    }

    @Test void explicitCredentialFailureCannotDowngradeToAnonymousAcrossProviderOperations() {
        when(credentials.getMetricsCredentials("metrics-a")).thenThrow(new IllegalStateException("private-credential-canary"));
        assertUnauthorizedOperations();
        verifyNoInteractions(api);
    }

    @Test void nullEmptyOrInvalidConfiguredAuthCannotStartTransport() {
        for (MetricsCredentials invalid : new MetricsCredentials[]{null, MetricsCredentials.none(),
                MetricsCredentials.bearer(" ", null, false), MetricsCredentials.bearer("secret\r\ncanary", null, false),
                MetricsCredentials.bearer("contains space", null, false),
                new MetricsCredentials(null, "user", null, null, false),
                new MetricsCredentials(null, "user", " ", null, false),
                new MetricsCredentials(null, "user:other", "pass", null, false),
                new MetricsCredentials(null, "user", "pass\ncanary", null, false)}) {
            when(credentials.getMetricsCredentials("metrics-a")).thenReturn(invalid);
            assertUnauthorizedOperations();
        }
        verifyNoInteractions(api);
    }

    @Test void explicitAnonymousConfigurationRemainsSupportedWithoutCredentialLookup() {
        Target anonymous = target("anonymous", null);
        assertThat(provider.query(anonymous, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M).usableForFindings()).isTrue();
        verify(credentials, never()).getMetricsCredentials(any());
        verify(api).query(any(), contains("target_id=\"anonymous\""), eq(MetricsCredentials.none()), any(), any(), any());
    }

    @Test void validBasicCredentialsArePassedOnlyToConfiguredTransport() {
        MetricsCredentials basic = new MetricsCredentials(null, "fixture-user", "fixture-pass", null, false);
        when(credentials.getMetricsCredentials("metrics-a")).thenReturn(basic);
        assertThat(provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M).usableForFindings()).isTrue();
        verify(api).query(any(), contains("target_id=\"target-a\""), same(basic), any(), any(), any());
    }

    @Test void abortedProviderReasonIsAllowlistedNotCopied() throws Exception {
        when(api.query(any(), any(), any(), any(), any(), any())).thenReturn(
                PrometheusApiClient.Response.of(PrometheusApiClient.Status.OPERATION_ABORTED, "private-provider-canary"));
        var result = provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        assertAborted(result);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(result))
                .doesNotContain("private-provider-canary", "fixture-a", "metrics-a");
    }

    @Test void expiredAOperationDoesNotConsumeBOperationOrCredentials() {
        MetricsOperationBudget expiredA = budget();
        expire();
        assertAborted(provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M, expiredA));
        Target other = target("target-b", "metrics-b");
        MetricsCredentials otherAuth = MetricsCredentials.bearer("fixture-b", null, false);
        when(credentials.getMetricsCredentials("metrics-b")).thenReturn(otherAuth);
        var result = provider.query(other, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M, budget());
        assertThat(result.targetId()).isEqualTo("target-b");
        assertThat(result.usableForFindings()).isTrue();
        verify(credentials, never()).getMetricsCredentials("metrics-a");
        verify(api).query(any(), contains("target_id=\"target-b\""), same(otherAuth), any(), any(), any());
    }

    @Test void legacyProviderGuardsDiscardLateResultAndDoNotInvokeLaterCategoryQueries() {
        AtomicInteger calls = new AtomicInteger();
        MetricsProvider legacy = new MetricsProvider() {
            public MetricsProviderStatus status(Target ignored) { expire(); return MetricsProviderStatus.AVAILABLE; }
            public SemanticMetricResult query(Target ignored, SemanticMetric metric, MetricWindow window) {
                calls.incrementAndGet(); expire();
                return SemanticMetricResult.available("target-a", metric, window, 1d, "count", "legacy", 1, Instant.now(), List.of());
            }
            public List<SemanticMetricResult> queryCategory(Target ignored, MetricCategory category, MetricWindow window) {
                throw new AssertionError("Budget-aware category must not call the unguarded legacy category");
            }
            public boolean supported(Target ignored) { return true; }
        };
        assertThat(legacy.queryCategory(target, MetricCategory.HTTP, MetricWindow.W_5M, budget()))
                .allSatisfy(this::assertAborted);
        assertThat(calls).hasValue(1);
        clock.set(0);
        assertThat(legacy.status(target, budget())).isEqualTo(MetricsProviderStatus.DEGRADED);
    }

    private void assertUnauthorizedOperations() {
        assertThat(provider.status(target)).isEqualTo(MetricsProviderStatus.UNAUTHORIZED);
        assertThat(provider.query(target, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M).reason()).isEqualTo("Unauthorized");
        assertThat(provider.probeSeries(target, "jvm_memory_used_bytes").reason()).isEqualTo("Unauthorized");
        assertThat(provider.queryRange(target, SemanticMetric.DB_POOL_AWAITING, MetricWindow.W_5M).reason()).isEqualTo("Unauthorized");
    }

    private void assertAborted(SemanticMetricResult result) {
        assertThat(result.reason()).isEqualTo(MetricsOperationBudget.REASON_EXCEEDED);
        assertThat(result.value()).isNull();
        assertThat(result.usableForFindings()).isFalse();
    }

    private MetricsOperationBudget budget() { return new MetricsOperationBudget(Duration.ofSeconds(1), clock::get); }
    private void expire() { clock.addAndGet(Duration.ofSeconds(2).toNanos()); }
    private static PrometheusApiClient.Response success() {
        return new PrometheusApiClient.Response(PrometheusApiClient.Status.OK, "ok", List.of(
                new MetricSeries("aggregate", Map.of(), List.of(new MetricSample(Instant.now(), 1d, Map.of())))));
    }
    private static Target target(String id, String credentialRef) {
        return new Target(TargetId.of(id), id, TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://127.0.0.1:8080", "master", "client", "keycloak-" + id, null),
                null, new ObservabilityTargetConfiguration("PROMETHEUS", null, "http://127.0.0.1:9090", credentialRef, null, "NAMESPACE"),
                Map.of("target_id", id));
    }
}
