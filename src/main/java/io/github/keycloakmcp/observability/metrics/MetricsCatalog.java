package io.github.keycloakmcp.observability.metrics;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps semantic metrics to category / stability / histogram requirements.
 */
public final class MetricsCatalog {

    public record Entry(
            SemanticMetric metric,
            MetricCategory category,
            MetricStability stability,
            boolean requiresHistogram,
            String unit) {
    }

    private static final Map<SemanticMetric, Entry> ENTRIES = build();

    private MetricsCatalog() {
    }

    public static Entry entry(SemanticMetric metric) {
        return ENTRIES.get(metric);
    }

    public static List<SemanticMetric> forCategory(MetricCategory category) {
        return ENTRIES.values().stream()
                .filter(e -> e.category() == category)
                .map(Entry::metric)
                .toList();
    }

    public static Set<SemanticMetric> all() {
        return EnumSet.allOf(SemanticMetric.class);
    }

    private static Map<SemanticMetric, Entry> build() {
        Map<SemanticMetric, Entry> m = new EnumMap<>(SemanticMetric.class);
        put(m, SemanticMetric.HTTP_REQUEST_RATE, MetricCategory.HTTP, MetricStability.STABLE, false, "rps");
        put(m, SemanticMetric.HTTP_ERROR_RATE, MetricCategory.HTTP, MetricStability.STABLE, false, "percent");
        put(m, SemanticMetric.HTTP_AVERAGE_LATENCY, MetricCategory.HTTP, MetricStability.STABLE, false, "ms");
        put(m, SemanticMetric.HTTP_P50_LATENCY, MetricCategory.HTTP, MetricStability.STABLE, true, "ms");
        put(m, SemanticMetric.HTTP_P95_LATENCY, MetricCategory.HTTP, MetricStability.STABLE, true, "ms");
        put(m, SemanticMetric.HTTP_P99_LATENCY, MetricCategory.HTTP, MetricStability.STABLE, true, "ms");
        put(m, SemanticMetric.HTTP_ACTIVE_REQUESTS, MetricCategory.HTTP, MetricStability.OPTIONAL, false, "count");
        put(m, SemanticMetric.DB_POOL_AVAILABLE, MetricCategory.DATABASE, MetricStability.STABLE, false, "count");
        put(m, SemanticMetric.DB_POOL_ACTIVE, MetricCategory.DATABASE, MetricStability.STABLE, false, "count");
        put(m, SemanticMetric.DB_POOL_AWAITING, MetricCategory.DATABASE, MetricStability.STABLE, false, "count");
        put(m, SemanticMetric.DB_POOL_UTILIZATION, MetricCategory.DATABASE, MetricStability.STABLE, false, "ratio");
        put(m, SemanticMetric.JVM_HEAP_USED, MetricCategory.JVM, MetricStability.STABLE, false, "bytes");
        put(m, SemanticMetric.JVM_HEAP_COMMITTED, MetricCategory.JVM, MetricStability.STABLE, false, "bytes");
        put(m, SemanticMetric.JVM_HEAP_MAX, MetricCategory.JVM, MetricStability.STABLE, false, "bytes");
        put(m, SemanticMetric.JVM_HEAP_UTILIZATION, MetricCategory.JVM, MetricStability.STABLE, false, "ratio");
        put(m, SemanticMetric.JVM_GC_PAUSE, MetricCategory.JVM, MetricStability.OPTIONAL, false, "ms");
        put(m, SemanticMetric.PROCESS_CPU, MetricCategory.RUNTIME, MetricStability.OPTIONAL, false, "ratio");
        put(m, SemanticMetric.KEYCLOAK_CLUSTER_SIZE, MetricCategory.CLUSTER, MetricStability.OPTIONAL, false, "count");
        put(m, SemanticMetric.LOGIN_RATE, MetricCategory.AUTHENTICATION, MetricStability.OPTIONAL, false, "rps");
        put(m, SemanticMetric.LOGIN_ERROR_RATE, MetricCategory.AUTHENTICATION, MetricStability.OPTIONAL, false, "rps");
        put(m, SemanticMetric.LOGOUT_RATE, MetricCategory.AUTHENTICATION, MetricStability.OPTIONAL, false, "rps");
        put(m, SemanticMetric.TOKEN_REFRESH_RATE, MetricCategory.AUTHENTICATION, MetricStability.OPTIONAL, false, "rps");
        put(m, SemanticMetric.CACHE_HIT_RATIO, MetricCategory.CACHE, MetricStability.OPTIONAL, false, "ratio");
        put(m, SemanticMetric.RUNTIME_CPU_USAGE, MetricCategory.RUNTIME, MetricStability.TROUBLESHOOTING, false, "cores");
        put(m, SemanticMetric.RUNTIME_MEMORY_WORKING_SET, MetricCategory.RUNTIME, MetricStability.TROUBLESHOOTING, false, "bytes");
        return Map.copyOf(m);
    }

    private static void put(
            Map<SemanticMetric, Entry> m,
            SemanticMetric metric,
            MetricCategory category,
            MetricStability stability,
            boolean requiresHistogram,
            String unit) {
        m.put(metric, new Entry(metric, category, stability, requiresHistogram, unit));
    }
}
