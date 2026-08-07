package io.github.keycloakmcp.observability.metrics;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves metrics endpoint URL for a target.
 * Prefer target observability endpoint; fall back to platform lab defaults.
 */
@ApplicationScoped
public class MetricsEndpointResolver {

    static final String OPENSHIFT_THANOS_DEFAULT =
            "https://thanos-querier.openshift-monitoring.svc:9091";

    private final Optional<String> prometheusEndpoint;
    private final Optional<String> openshiftEndpoint;

    @Inject
    public MetricsEndpointResolver(
            @ConfigProperty(name = "platform.metrics.prometheus.endpoint") Optional<String> prometheusEndpoint,
            @ConfigProperty(name = "platform.metrics.openshift.endpoint") Optional<String> openshiftEndpoint) {
        this.prometheusEndpoint = blankFilter(prometheusEndpoint);
        this.openshiftEndpoint = blankFilter(openshiftEndpoint);
    }

    public Optional<String> resolve(Target target) {
        if (target == null) {
            return Optional.empty();
        }
        ObservabilityTargetConfiguration obs = target.observability();
        if (obs != null && obs.endpointUrl() != null && !obs.endpointUrl().isBlank()) {
            return Optional.of(obs.endpointUrl().trim());
        }
        String type = metricsType(obs);
        if (type == null || "NONE".equals(type)) {
            return Optional.empty();
        }
        if ("OPENSHIFT_MONITORING".equals(type)) {
            return openshiftEndpoint.or(() -> Optional.of(OPENSHIFT_THANOS_DEFAULT));
        }
        // PROMETHEUS | THANOS | unknown configured type
        return prometheusEndpoint;
    }

    public String metricsType(Target target) {
        return metricsType(target == null ? null : target.observability());
    }

    private static String metricsType(ObservabilityTargetConfiguration obs) {
        if (obs == null || obs.metricsType() == null || obs.metricsType().isBlank()) {
            return null;
        }
        return obs.metricsType().trim().toUpperCase(Locale.ROOT);
    }

    public MetricsQueryContext queryContext(Target target) {
        ObservabilityTargetConfiguration obs = target.observability();
        MetricsScope scope = obs != null ? obs.metricsScope() : MetricsScope.NAMESPACE;
        String namespace = null;
        if (obs != null && obs.namespace() != null && !obs.namespace().isBlank()) {
            namespace = obs.namespace().trim();
        } else if (target.infrastructure() != null
                && target.infrastructure().namespace() != null
                && !target.infrastructure().namespace().isBlank()) {
            namespace = target.infrastructure().namespace().trim();
        }

        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, String> tags = target.tags();
        if (tags != null) {
            put(labels, "job", first(tags, "job", "metrics-job"));
            put(labels, "service", first(tags, "service", "metrics-service"));
            put(labels, "pod", first(tags, "pod", "metrics-pod"));
        }
        return new MetricsQueryContext(target.id().value(), namespace, scope, labels, HttpMetricScope.ALL);
    }

    private static String first(Map<String, String> tags, String... keys) {
        for (String key : keys) {
            String v = tags.get(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static void put(Map<String, String> labels, String key, String value) {
        if (value != null && !value.isBlank()) {
            labels.put(key, value);
        }
    }

    private static Optional<String> blankFilter(Optional<String> value) {
        return value == null ? Optional.empty() : value.filter(s -> s != null && !s.isBlank()).map(String::trim);
    }
}
