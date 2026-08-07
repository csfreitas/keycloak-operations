package io.github.keycloakmcp.observability.metrics;

import java.util.Objects;

/**
 * Builds controlled PromQL for semantic metrics. Never accepts raw PromQL from callers.
 */
public final class MetricsQueryBuilder {

    private MetricsQueryBuilder() {
    }

    public static String build(SemanticMetric metric, MetricWindow window, MetricsQueryContext ctx) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(ctx, "ctx");
        String w = window.label();
        String sel = ctx.selectorClause();
        String uriFilter = uriFilter(ctx.httpScope());

        return switch (metric) {
            case HTTP_REQUEST_RATE ->
                    "sum(rate(http_server_requests_seconds_count{" + sel + uriFilter + "}[" + w + "]))";
            case HTTP_ERROR_RATE ->
                    "100 * sum(rate(http_server_requests_seconds_count{" + sel
                            + uriFilter + ",outcome=\"SERVER_ERROR\"}[" + w + "]))"
                            + " / clamp_min(sum(rate(http_server_requests_seconds_count{"
                            + sel + uriFilter + "}[" + w + "])), 1e-9)";
            case HTTP_AVERAGE_LATENCY ->
                    "(sum(rate(http_server_requests_seconds_sum{" + sel + uriFilter + "}[" + w + "]))"
                            + " / clamp_min(sum(rate(http_server_requests_seconds_count{"
                            + sel + uriFilter + "}[" + w + "])), 1e-9)) * 1000";
            case HTTP_P50_LATENCY -> quantile(0.50, sel, uriFilter, w);
            case HTTP_P95_LATENCY -> quantile(0.95, sel, uriFilter, w);
            case HTTP_P99_LATENCY -> quantile(0.99, sel, uriFilter, w);
            case HTTP_ACTIVE_REQUESTS ->
                    "sum(http_server_active_requests{" + sel + "})";
            case DB_POOL_AVAILABLE -> "sum(agroal_available_count{" + sel + "})";
            case DB_POOL_ACTIVE -> "sum(agroal_active_count{" + sel + "})";
            case DB_POOL_AWAITING -> "sum(agroal_awaiting_count{" + sel + "})";
            case DB_POOL_UTILIZATION ->
                    "sum(agroal_active_count{" + sel + "})"
                            + " / clamp_min(sum(agroal_active_count{" + sel + "})"
                            + " + sum(agroal_available_count{" + sel + "}), 1e-9)";
            case JVM_HEAP_USED ->
                    "sum(jvm_memory_used_bytes{" + sel + ",area=\"heap\"})";
            case JVM_HEAP_COMMITTED ->
                    "sum(jvm_memory_committed_bytes{" + sel + ",area=\"heap\"})";
            case JVM_HEAP_MAX ->
                    "sum(jvm_memory_max_bytes{" + sel + ",area=\"heap\"})";
            case JVM_HEAP_UTILIZATION ->
                    "sum(jvm_memory_used_bytes{" + sel + ",area=\"heap\"})"
                            + " / clamp_min(sum(jvm_memory_max_bytes{" + sel + ",area=\"heap\"}), 1e-9)";
            case JVM_GC_PAUSE ->
                    "max(jvm_gc_pause_seconds_max{" + sel + "}) * 1000";
            case PROCESS_CPU -> "avg(process_cpu_usage{" + sel + "})";
            case KEYCLOAK_CLUSTER_SIZE -> "max(vendor_cluster_size{" + sel + "})";
            case LOGIN_RATE ->
                    "sum(rate(keycloak_user_events_total{" + sel + ",event=\"login\"}[" + w + "]))";
            case LOGIN_ERROR_RATE ->
                    "sum(rate(keycloak_user_events_total{" + sel + ",event=\"login_error\"}[" + w + "]))";
            case LOGOUT_RATE ->
                    "sum(rate(keycloak_user_events_total{" + sel + ",event=\"logout\"}[" + w + "]))";
            case TOKEN_REFRESH_RATE ->
                    "sum(rate(keycloak_user_events_total{" + sel + ",event=\"refresh_token\"}[" + w + "]))";
            case CACHE_HIT_RATIO ->
                    "sum(rate(cache_gets_total{" + sel + ",result=\"hit\"}[" + w + "]))"
                            + " / clamp_min(sum(rate(cache_gets_total{" + sel + ",result=~\"hit|miss\"}["
                            + w + "])), 1e-9)";
            case RUNTIME_CPU_USAGE ->
                    "sum(rate(container_cpu_usage_seconds_total{" + sel + "}[" + w + "]))";
            case RUNTIME_MEMORY_WORKING_SET ->
                    "sum(container_memory_working_set_bytes{" + sel + "})";
        };
    }

    private static String quantile(double q, String sel, String uriFilter, String w) {
        return "histogram_quantile(" + q + ", sum by (le) (rate(http_server_requests_seconds_bucket{"
                + sel + uriFilter + "}[" + w + "]))) * 1000";
    }

    private static String uriFilter(HttpMetricScope scope) {
        if (scope == null || scope == HttpMetricScope.ALL) {
            return "";
        }
        return switch (scope) {
            case TOKEN -> ",uri=~\".*/token\"";
            case LOGIN -> ",uri=~\".*/login.*\"";
            case AUTHENTICATION -> ",uri=~\".*/(login|token|auth).*\"";
            default -> "";
        };
    }
}
