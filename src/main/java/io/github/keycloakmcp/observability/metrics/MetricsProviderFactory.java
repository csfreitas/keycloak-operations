package io.github.keycloakmcp.observability.metrics;

import java.util.Locale;
import java.util.Optional;

import io.github.keycloakmcp.observability.metrics.openshift.OpenShiftMonitoringMetricsProvider;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Selects the appropriate {@link MetricsProvider} for a target.
 */
@ApplicationScoped
public class MetricsProviderFactory {

    private final PrometheusMetricsProvider prometheus;
    private final OpenShiftMonitoringMetricsProvider openshift;
    private final NoOpMetricsProvider noOp;
    private final MetricsEndpointResolver endpointResolver;

    @Inject
    public MetricsProviderFactory(
            PrometheusMetricsProvider prometheus,
            OpenShiftMonitoringMetricsProvider openshift,
            NoOpMetricsProvider noOp,
            MetricsEndpointResolver endpointResolver) {
        this.prometheus = prometheus;
        this.openshift = openshift;
        this.noOp = noOp;
        this.endpointResolver = endpointResolver;
    }

    public MetricsProvider forTarget(Target target) {
        return resolve(target);
    }

    public MetricsProvider resolve(Target target) {
        if (target == null) {
            return noOp;
        }
        String type = typeOf(target);
        if (type == null || "NONE".equals(type)) {
            // Lab fallback: prometheus endpoint without explicit type
            if (endpointResolver.resolve(target).isPresent()) {
                return prometheus;
            }
            return noOp;
        }
        return switch (type) {
            case "OPENSHIFT_MONITORING" -> openshift;
            case "PROMETHEUS", "THANOS" -> prometheus;
            default -> {
                Optional<String> endpoint = endpointResolver.resolve(target);
                yield endpoint.isPresent() ? prometheus : noOp;
            }
        };
    }

    private static String typeOf(Target target) {
        ObservabilityTargetConfiguration obs = target.observability();
        if (obs == null || obs.metricsType() == null || obs.metricsType().isBlank()) {
            return null;
        }
        return obs.metricsType().trim().toUpperCase(Locale.ROOT);
    }
}
