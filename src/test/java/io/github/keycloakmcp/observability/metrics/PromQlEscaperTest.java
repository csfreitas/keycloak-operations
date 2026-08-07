package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PromQlEscaperTest {

    @Test
    void escapesBackslashQuoteAndNewlines() {
        assertThat(PromQlEscaper.labelValue("a\\b\"c\nd\re\tf"))
                .isEqualTo("a\\\\b\\\"c\\nd\\re\\tf");
    }

    @Test
    void escapesInjectionPayloadBreakingLabel() {
        String payload = "\"} or vector(1)";
        String escaped = PromQlEscaper.labelValue(payload);
        assertThat(escaped).isEqualTo("\\\"} or vector(1)");
        String matcher = PromQlEscaper.labelEq("namespace", payload);
        assertThat(matcher).isEqualTo("namespace=\"\\\"} or vector(1)\"");
        assertThat(matcher).startsWith("namespace=\"");
        assertThat(matcher).endsWith("\"");
        assertThat(matcher.indexOf(" or vector")).isGreaterThan(matcher.indexOf('\\'));
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(PromQlEscaper.labelValue(null)).isEmpty();
    }

    @Test
    void rejectsInvalidLabelNames() {
        assertThatThrownBy(() -> PromQlEscaper.labelEq("bad-label", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PromQlEscaper.labelEq("", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapesStructuredInjectionPayload() {
        String payload = "\"} or vector(1) or {\"";
        String matcher = PromQlEscaper.labelEq("realm", payload);
        assertThat(matcher).isEqualTo("realm=\"\\\"} or vector(1) or {\\\"\"");

        String query = MetricsQueryBuilder.build(
                SemanticMetric.HTTP_REQUEST_RATE,
                MetricWindow.W_5M,
                new MetricsQueryContext(
                        "t1",
                        "ns",
                        MetricsScope.NAMESPACE,
                        Map.of("service", payload),
                        HttpMetricScope.ALL));
        assertThat(query).startsWith("sum(rate(http_server_requests_seconds_count{");
        assertThat(query).contains("service=\"\\\"} or vector(1) or {\\\"\"");
        // Structural PromQL must still end the selector before the range
        assertThat(query).contains("}[5m]))");
    }
}
