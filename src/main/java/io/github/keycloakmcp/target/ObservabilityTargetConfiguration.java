package io.github.keycloakmcp.target;

import io.github.keycloakmcp.observability.metrics.MetricsScope;

/**
 * Optional observability settings for a target.
 * Endpoint URL and credentials are server-configured — never accepted from LLM clients.
 */
public record ObservabilityTargetConfiguration(
        String metricsType,
        String tracingType,
        String endpointUrl,
        String credentialRef,
        String namespace,
        String scope) {

    public ObservabilityTargetConfiguration(String metricsType, String tracingType) {
        this(metricsType, tracingType, null, null, null, MetricsScope.NAMESPACE.name());
    }

    public boolean hasMetrics() {
        if (metricsType == null || metricsType.isBlank()) {
            return false;
        }
        String t = metricsType.trim().toUpperCase();
        if ("NONE".equals(t)) {
            return false;
        }
        if ("OPENSHIFT_MONITORING".equals(t)) {
            return true; // endpoint may be in-cluster default
        }
        return endpointUrl != null && !endpointUrl.isBlank();
    }

    public MetricsScope metricsScope() {
        if (scope == null || scope.isBlank()) {
            return MetricsScope.NAMESPACE;
        }
        try {
            return MetricsScope.valueOf(scope.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MetricsScope.NAMESPACE;
        }
    }
}
