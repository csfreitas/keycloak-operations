package io.github.keycloakmcp.observability.metrics;

/**
 * Semantic metrics request. Callers never supply PromQL — only a fixed semantic category.
 */
public record MetricsQuery(String targetId, Semantic semantic) {

    public enum Semantic {
        REQUESTS,
        LATENCY,
        JVM,
        DATABASE_POOL,
        RESOURCES
    }
}
