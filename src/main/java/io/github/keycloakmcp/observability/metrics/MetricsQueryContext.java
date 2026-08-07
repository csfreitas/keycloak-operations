package io.github.keycloakmcp.observability.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;

/**
 * Context for building target-scoped PromQL. Selectors are mandatory for isolation.
 */
public record MetricsQueryContext(
        String targetId,
        String namespace,
        MetricsScope scope,
        Map<String, String> mandatoryLabels,
        HttpMetricScope httpScope) {

    public MetricsQueryContext {
        Objects.requireNonNull(targetId, "targetId");
        scope = scope == null ? MetricsScope.NAMESPACE : scope;
        mandatoryLabels = mandatoryLabels == null ? Map.of() : Map.copyOf(mandatoryLabels);
        httpScope = httpScope == null ? HttpMetricScope.ALL : httpScope;
    }

    public static MetricsQueryContext fromTarget(Target target) {
        if (target == null) {
            return new MetricsQueryContext("unknown", null, MetricsScope.NAMESPACE, Map.of(), HttpMetricScope.ALL);
        }
        ObservabilityTargetConfiguration obs = target.observability();
        String namespace = null;
        MetricsScope scope = MetricsScope.NAMESPACE;
        if (obs != null) {
            namespace = obs.namespace();
            scope = obs.metricsScope();
            if ((namespace == null || namespace.isBlank())
                    && target.hasInfrastructure()
                    && target.infrastructure().namespace() != null) {
                namespace = target.infrastructure().namespace();
            }
        } else if (target.hasInfrastructure()) {
            namespace = target.infrastructure().namespace();
        }
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        if (target.tags() != null && !target.tags().isEmpty()) {
            for (String key : List.of("target_id", "job", "service", "workload", "pod", "pod_name")) {
                String v = target.tags().get(key);
                if (v != null && !v.isBlank()) {
                    labels.put(key, v);
                }
            }
        }
        // Prefer explicit target_id label for isolation; fall back to TargetId when tags omit it.
        labels.putIfAbsent("target_id", target.id().value());
        return new MetricsQueryContext(target.id().value(), namespace, scope, Map.copyOf(labels), HttpMetricScope.ALL);
    }

    public String selectorClause() {
        List<String> parts = new ArrayList<>();
        if (scope == MetricsScope.NAMESPACE && namespace != null && !namespace.isBlank()) {
            parts.add(PromQlEscaper.labelEq("namespace", namespace));
        }
        for (Map.Entry<String, String> e : mandatoryLabels.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && !e.getValue().isBlank()) {
                parts.add(PromQlEscaper.labelEq(e.getKey(), e.getValue()));
            }
        }
        // Namespace alone is insufficient when multiple targets share a namespace.
        if (!hasTargetDiscriminator()) {
            parts.add(PromQlEscaper.labelEq("target_id", targetId));
        }
        return String.join(",", parts);
    }

    /**
     * True when mandatory labels already discriminate a single workload/target.
     */
    public boolean hasTargetDiscriminator() {
        for (String key : List.of("target_id", "service", "job", "workload", "pod", "pod_name")) {
            String v = mandatoryLabels.get(key);
            if (v != null && !v.isBlank()) {
                return true;
            }
        }
        return false;
    }

    public String withSelector(String metricName) {
        return metricName + "{" + selectorClause() + "}";
    }
}
