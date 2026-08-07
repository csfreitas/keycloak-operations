package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MetricsQueryBuilderTest {

    @Test
    void includesMandatorySelectorsAndWindowInRate() {
        MetricsQueryContext ctx = new MetricsQueryContext(
                "lab-a",
                "rhbk",
                MetricsScope.NAMESPACE,
                Map.of("job", "keycloak"),
                HttpMetricScope.ALL);
        String q = MetricsQueryBuilder.build(SemanticMetric.HTTP_REQUEST_RATE, MetricWindow.W_5M, ctx);
        assertThat(q).contains("namespace=\"rhbk\"");
        assertThat(q).contains("job=\"keycloak\"");
        assertThat(q).contains("rate(");
        assertThat(q).contains("[5m]");
        assertThat(q).doesNotContain("vector(");
    }

    @Test
    void percentileUsesHistogramQuantileOnBucket() {
        MetricsQueryContext ctx = new MetricsQueryContext(
                "lab-a", "ns", MetricsScope.NAMESPACE, Map.of(), HttpMetricScope.ALL);
        String q = MetricsQueryBuilder.build(SemanticMetric.HTTP_P95_LATENCY, MetricWindow.W_15M, ctx);
        assertThat(q).startsWith("histogram_quantile(0.95");
        assertThat(q).contains("http_server_requests_seconds_bucket");
        assertThat(q).contains("[15m]");
        assertThat(q).doesNotContain("_count[");
    }

    @Test
    void escapesHostileNamespaceInSelector() {
        MetricsQueryContext ctx = new MetricsQueryContext(
                "t1",
                "\"} or vector(1)",
                MetricsScope.NAMESPACE,
                Map.of(),
                HttpMetricScope.ALL);
        String q = MetricsQueryBuilder.build(SemanticMetric.PROCESS_CPU, MetricWindow.W_1M, ctx);
        assertThat(q).contains("namespace=\"\\\"} or vector(1)\"");
        assertThat(q).doesNotContain("namespace=\"\"} or vector(1)\"");
    }

    @Test
    void clusterScopeOmitsNamespaceSelectorWhenLabelsPresent() {
        MetricsQueryContext ctx = new MetricsQueryContext(
                "t1",
                "rhbk",
                MetricsScope.CLUSTER,
                Map.of("job", "keycloak"),
                HttpMetricScope.ALL);
        String clause = ctx.selectorClause();
        assertThat(clause).doesNotContain("namespace=");
        assertThat(clause).contains("job=\"keycloak\"");
    }
}
