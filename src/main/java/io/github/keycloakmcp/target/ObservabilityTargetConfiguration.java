package io.github.keycloakmcp.target;

/**
 * Optional observability settings for a target.
 * Metric/tracing types are opaque strings until collectors are wired.
 */
public record ObservabilityTargetConfiguration(
        String metricsType,
        String tracingType) {
}
