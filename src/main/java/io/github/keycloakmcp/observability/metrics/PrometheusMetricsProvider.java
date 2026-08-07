package io.github.keycloakmcp.observability.metrics;

import java.util.Locale;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

/**
 * Prometheus / Thanos-backed metrics provider.
 * Uses only internally constructed queries — never accepts raw PromQL.
 */
@ApplicationScoped
@Typed(PrometheusMetricsProvider.class)
public class PrometheusMetricsProvider extends AbstractPrometheusMetricsProvider {

    private static final Logger LOG = Logger.getLogger(PrometheusMetricsProvider.class);

    @Inject
    public PrometheusMetricsProvider(
            PrometheusApiClient apiClient,
            MetricsEndpointResolver endpointResolver,
            CredentialProvider credentialProvider,
            MetricsConfig metricsConfig) {
        super(LOG, apiClient, endpointResolver, credentialProvider, metricsConfig, "prometheus");
    }

    /** CDI proxy constructor. */
    protected PrometheusMetricsProvider() {
        this(null, null, null, null);
    }

    @Override
    protected boolean matchesType(Target target) {
        if (target == null || target.observability() == null) {
            // Lab fallback: allow when platform.metrics.prometheus.endpoint is set
            return true;
        }
        String type = target.observability().metricsType();
        if (type == null || type.isBlank()) {
            return true;
        }
        String t = type.trim().toUpperCase(Locale.ROOT);
        return "PROMETHEUS".equals(t) || "THANOS".equals(t);
    }
}
