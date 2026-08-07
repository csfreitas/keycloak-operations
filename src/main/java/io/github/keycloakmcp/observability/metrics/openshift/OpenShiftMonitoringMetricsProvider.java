package io.github.keycloakmcp.observability.metrics.openshift;

import java.util.Locale;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.observability.metrics.AbstractPrometheusMetricsProvider;
import io.github.keycloakmcp.observability.metrics.MetricsEndpointResolver;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

/**
 * OpenShift Monitoring (Thanos Querier) metrics provider.
 * Prefers namespace scope and always applies a namespace selector unless CLUSTER scope is set.
 */
@ApplicationScoped
@Typed(OpenShiftMonitoringMetricsProvider.class)
public class OpenShiftMonitoringMetricsProvider extends AbstractPrometheusMetricsProvider {

    private static final Logger LOG = Logger.getLogger(OpenShiftMonitoringMetricsProvider.class);

    @Inject
    public OpenShiftMonitoringMetricsProvider(
            PrometheusApiClient apiClient,
            MetricsEndpointResolver endpointResolver,
            CredentialProvider credentialProvider,
            MetricsConfig metricsConfig) {
        super(LOG, apiClient, endpointResolver, credentialProvider, metricsConfig, "openshift-monitoring");
    }

    /** CDI proxy constructor. */
    protected OpenShiftMonitoringMetricsProvider() {
        this(null, null, null, null);
    }

    @Override
    protected boolean matchesType(Target target) {
        if (target == null || target.observability() == null || target.observability().metricsType() == null) {
            return false;
        }
        return "OPENSHIFT_MONITORING".equals(target.observability().metricsType().trim().toUpperCase(Locale.ROOT));
    }
}
