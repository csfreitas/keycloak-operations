package io.github.keycloakmcp.observability.metrics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

/**
 * No-op metrics provider for environments without an external metrics backend.
 * Typed so it is not an ambiguous {@link MetricsProvider} bean.
 */
@ApplicationScoped
@Typed(NoOpMetricsProvider.class)
public class NoOpMetricsProvider implements MetricsProvider {

    @Override
    public boolean supported() {
        return false;
    }

    @Override
    public MetricsResult query(MetricsQuery query) {
        return MetricsResult.unsupported(query.targetId(), query.semantic(), "metrics provider disabled");
    }
}
