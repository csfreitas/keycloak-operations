package io.github.keycloakmcp.observability.metrics;

import java.util.ArrayList;
import java.util.List;

import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

/**
 * No-op metrics provider for targets without a metrics backend.
 */
@ApplicationScoped
@Typed(NoOpMetricsProvider.class)
public class NoOpMetricsProvider implements MetricsProvider {

    @Override
    public MetricsProviderStatus status(Target target) {
        return MetricsProviderStatus.NOT_CONFIGURED;
    }

    @Override
    public SemanticMetricResult query(Target target, SemanticMetric metric, MetricWindow window) {
        String id = target == null ? null : target.id().value();
        return SemanticMetricResult.notConfigured(id, metric, window);
    }

    @Override
    public List<SemanticMetricResult> queryCategory(Target target, MetricCategory category, MetricWindow window) {
        List<SemanticMetricResult> out = new ArrayList<>();
        for (SemanticMetric metric : MetricsCatalog.forCategory(category)) {
            out.add(query(target, metric, window));
        }
        return List.copyOf(out);
    }

    @Override
    public boolean supported(Target target) {
        return false;
    }
}
