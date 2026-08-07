package io.github.keycloakmcp.observability.metrics;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Prometheus-backed metrics provider. Uses only internally constructed queries —
 * never accepts raw PromQL from API parameters.
 * <p>
 * When no endpoint is configured, returns unsupported/empty results gracefully.
 */
@ApplicationScoped
public class PrometheusMetricsProvider implements MetricsProvider {

    private static final Logger LOG = Logger.getLogger(PrometheusMetricsProvider.class);

    private final Optional<String> endpoint;

    @Inject
    public PrometheusMetricsProvider(
            @ConfigProperty(name = "platform.metrics.prometheus.endpoint") Optional<String> endpoint) {
        this.endpoint = endpoint.filter(s -> s != null && !s.isBlank());
    }

    @Override
    public boolean supported() {
        return endpoint.isPresent();
    }

    @Override
    public MetricsResult query(MetricsQuery query) {
        if (!supported()) {
            return MetricsResult.unsupported(
                    query.targetId(),
                    query.semantic(),
                    "Prometheus endpoint not configured (platform.metrics.prometheus.endpoint)");
        }
        // Fixed semantic → PromQL mapping only; never take PromQL from callers.
        String fixedQuery = toFixedPromQl(query.semantic());
        LOG.debugf("Would query Prometheus semantic=%s fixedQuery=%s (fetch not wired yet)",
                query.semantic(), fixedQuery);
        return MetricsResult.empty(query.targetId(), query.semantic());
    }

    private static String toFixedPromQl(MetricsQuery.Semantic semantic) {
        return switch (semantic) {
            case REQUESTS -> "sum(rate(http_server_requests_seconds_count[5m]))";
            case LATENCY -> "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))";
            case JVM -> "jvm_memory_used_bytes";
            case DATABASE_POOL -> "agroal_active_count";
            case RESOURCES -> "process_cpu_usage";
        };
    }
}
