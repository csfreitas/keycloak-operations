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
}
