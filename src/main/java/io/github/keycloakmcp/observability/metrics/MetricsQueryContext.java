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
        Map<String, String> labels = Map.of();
        if (target.tags() != null && !target.tags().isEmpty()) {
            String job = target.tags().get("job");
            String service = target.tags().get("service");
            if (job != null || service != null) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                if (job != null && !job.isBlank()) {
                    m.put("job", job);
                }
                if (service != null && !service.isBlank()) {
                    m.put("service", service);
                }
                labels = Map.copyOf(m);
            }
        }
        return new MetricsQueryContext(target.id().value(), namespace, scope, labels, HttpMetricScope.ALL);
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
        if (parts.isEmpty()) {
            // Still isolate by job/instance label from target id when nothing else known
            parts.add(PromQlEscaper.labelEq("target_id", targetId));
        }
        return String.join(",", parts);
    }

    public String withSelector(String metricName) {
        return metricName + "{" + selectorClause() + "}";
    }
}
