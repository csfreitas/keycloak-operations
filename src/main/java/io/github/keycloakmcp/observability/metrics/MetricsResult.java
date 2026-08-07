package io.github.keycloakmcp.observability.metrics;

import java.util.List;
import java.util.Map;

public record MetricsResult(
        String targetId,
        MetricsQuery.Semantic semantic,
        boolean supported,
        String message,
        List<Map<String, Object>> series) {

    public static MetricsResult unsupported(String targetId, MetricsQuery.Semantic semantic, String message) {
        return new MetricsResult(targetId, semantic, false, message, List.of());
    }

    public static MetricsResult empty(String targetId, MetricsQuery.Semantic semantic) {
        return new MetricsResult(targetId, semantic, true, "no data", List.of());
    }
}
