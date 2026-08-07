package io.github.keycloakmcp.observability.metrics;

import java.util.List;

import io.github.keycloakmcp.target.Target;

/**
 * Target-scoped metrics backend. Callers never supply PromQL — only semantics.
 */
public interface MetricsProvider {

    MetricsProviderStatus status(Target target);

    SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window);

    List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window);

    boolean supported(Target target);

    /**
     * Controlled series-presence probe (count of a known metric family). Never accepts caller PromQL.
     */
    default SemanticMetricResult probeSeries(Target target, String controlledMetricFamily) {
        return SemanticMetricResult.notAvailable(
                target == null ? null : target.id().value(), null, MetricWindow.defaultWindow(),
                null, "Series probe unsupported");
    }

    /**
     * Range analysis for metrics that benefit from temporal aggregates (e.g. DB awaiting).
     */
    default RangeMetricSummary queryRange(Target target, SemanticMetric metric, MetricWindow window) {
        return RangeMetricSummary.fromInstant(query(target, metric, window));
    }
}
