package io.github.keycloakmcp.domain.metrics;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.keycloakmcp.observability.metrics.MetricsProviderStatus;

/**
 * Compact status for REST/MCP — never includes credentials or PromQL.
 */
public record MetricsStatusView(
        String targetId,
        MetricsProviderStatus status,
        String metricsType,
        boolean configured,
        String message,
        Map<String, Object> details) {

    public MetricsStatusView {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static MetricsStatusView of(
            String targetId,
            MetricsProviderStatus status,
            String metricsType,
            boolean configured,
            String message) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("configured", configured);
        if (metricsType != null) {
            details.put("metricsType", metricsType);
        }
        return new MetricsStatusView(targetId, status, metricsType, configured, message, details);
    }
}
