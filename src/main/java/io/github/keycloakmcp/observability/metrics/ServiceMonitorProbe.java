package io.github.keycloakmcp.observability.metrics;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.github.keycloakmcp.collection.CollectionBudget;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Bounded installation configuration association and independently observed scrape status. */
@ApplicationScoped
public class ServiceMonitorProbe {
    private static final int MONITOR_LIMIT = 100;
    private static final Pattern DURATION = Pattern.compile(
            "(?:([0-9]{1,9})y)?(?:([0-9]{1,9})w)?(?:([0-9]{1,9})d)?(?:([0-9]{1,9})h)?"
                    + "(?:([0-9]{1,9})m)?(?:([0-9]{1,9})s)?(?:([0-9]{1,9})ms)?");
    private static final long[] UNIT_MILLIS = {31_536_000_000L, 604_800_000L, 86_400_000L, 3_600_000L, 60_000L, 1_000L, 1L};

    public record Result(ScrapeReadiness readiness, Boolean serviceMonitorPresent,
            String interval, String scrapeTimeout, String detail) { }

    private record Configuration(String interval, String timeout) { }
    private enum NamespaceSelection { CURRENT, EXCLUDED, BROAD }

    private final InfrastructureClientFactory infrastructureClientFactory;
    private final MetricsProviderFactory metricsProviderFactory;
    private final InventoryService inventoryService;
    @ConfigProperty(name = "collection.operation-timeout-ms", defaultValue = "30000")
    long collectionTimeoutMs = 30000;

    @Inject
    public ServiceMonitorProbe(InfrastructureClientFactory infrastructureClientFactory,
            MetricsProviderFactory metricsProviderFactory, InventoryService inventoryService) {
        this.infrastructureClientFactory = infrastructureClientFactory;
        this.metricsProviderFactory = metricsProviderFactory;
        this.inventoryService = inventoryService;
    }

    public Result probe(Target target) {
        if (target == null || !target.hasInfrastructure()
                || (target.infrastructureTypeOrNone() != InfrastructureType.OPENSHIFT
                    && target.infrastructureTypeOrNone() != InfrastructureType.KUBERNETES)
                || target.infrastructure().installation() == null) {
            return unknown("No explicit cluster installation binding");
        }
        try (var scope = CollectionBudget.open(target.id().value(), collectionTimeoutMs)) {
            return probeScoped(target);
        }
    }

    private Result probeScoped(Target target) {
        Configuration configuration;
        try {
            checkpoint();
            var client = infrastructureClientFactory.resolve(target);
            checkpoint();
            if (client.isEmpty()) return unknown("No cluster client");
            ClusterClient cluster = client.get();
            String namespace = target.infrastructure().namespace();
            if (namespace == null || namespace.isBlank() || !namespace.equals(cluster.namespace())) {
                return unknown("Cluster namespace does not match the installation");
            }
            var association = inventoryService.associatedServices(target, cluster);
            checkpoint();
            if (association == null || !association.complete() || !namespace.equals(association.namespace())
                    || association.services().isEmpty()) {
                return unknown("Installation service association unavailable");
            }
            validateServices(association, namespace);
            var monitors = BoundedKubernetesReader.list(cluster.kubernetes(),
                    BoundedKubernetesReader.SERVICE_MONITORS, namespace, MONITOR_LIMIT);
            checkpoint();
            configuration = null;
            for (var monitor : monitors) {
                checkpoint();
                Configuration candidate = associate(monitor, association, namespace);
                if (candidate != null) {
                    require(configuration == null);
                    configuration = candidate;
                }
            }
            checkpoint();
            if (configuration == null) {
                return new Result(ScrapeReadiness.SERVICEMONITOR_MISSING, false, null, null,
                        "No ServiceMonitor associated with the installation in its namespace");
            }
        } catch (KubernetesClientException e) {
            if (e.getCode() == 401 || e.getCode() == 403) {
                return new Result(ScrapeReadiness.PERMISSION_DENIED, null, null, null,
                        "ServiceMonitor association access denied");
            }
            return unknown("ServiceMonitor association unavailable");
        } catch (RuntimeException e) {
            // Backend messages/resources may contain credentials, URLs or untrusted metadata.
            return unknown("ServiceMonitor association incomplete, unsupported or ambiguous");
        }

        try {
            checkpoint();
            MetricsProvider provider = metricsProviderFactory.forTarget(target);
            checkpoint();
            if (!provider.supported(target)) {
                checkpoint();
                return result(ScrapeReadiness.METRICS_DISABLED, configuration, "Metrics provider not configured");
            }
            checkpoint();
            ScrapeObservation observation = provider.probeScrape(target);
            checkpoint();
            if (observation == null || observation.availability() != MetricAvailability.AVAILABLE) {
                return result(ScrapeReadiness.UNKNOWN, configuration, "Matching scrape observations unavailable");
            }
            if (observation.failed() > 0) {
                return result(ScrapeReadiness.SCRAPE_TARGET_DOWN, configuration,
                        "At least one observed matching scrape failed");
            }
            return result(ScrapeReadiness.SCRAPE_HEALTHY, configuration,
                    "All observed matching scrapes succeeded; expected coverage is not established");
        } catch (RuntimeException e) {
            return result(ScrapeReadiness.UNKNOWN, configuration, "Matching scrape observations unavailable");
        }
    }

    private static void validateServices(InventoryService.ServiceAssociation association, String namespace) {
        require(association.namespaceServices().size() <= 500 && association.services().size() <= 500);
        Set<String> all = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Service service : association.namespaceServices()) {
            require(service != null && service.getMetadata() != null
                    && namespace.equals(service.getMetadata().getNamespace())
                    && nonBlank(service.getMetadata().getUid()) && nonBlank(service.getMetadata().getName())
                    && all.add(service.getMetadata().getUid()) && names.add(service.getMetadata().getName()));
        }
        Set<String> own = new HashSet<>();
        for (Service service : association.services()) {
            require(service != null && service.getMetadata() != null
                    && all.contains(service.getMetadata().getUid()) && own.add(service.getMetadata().getUid()));
        }
    }

    private static Configuration associate(GenericKubernetesResource monitor,
            InventoryService.ServiceAssociation association, String namespace) {
        Map<?, ?> spec = map(monitor.getAdditionalProperties().get("spec"));
        NamespaceSelection selection = namespaceSelection(spec, namespace);
        if (selection == NamespaceSelection.EXCLUDED) return null;
        Map<?, ?> selector = map(spec.get("selector"));
        validateSelector(selector);
        Set<String> own = new HashSet<>();
        association.services().forEach(service -> own.add(service.getMetadata().getUid()));
        List<Service> matching = association.namespaceServices().stream()
                .filter(service -> matches(selector, service.getMetadata().getLabels())).toList();
        if (matching.stream().noneMatch(service -> own.contains(service.getMetadata().getUid()))) return null;
        // Broad/shared selectors cannot establish exclusive attribution to this installation.
        require(selection == NamespaceSelection.CURRENT && matching.size() == 1);
        Service service = matching.getFirst();
        require(spec.get("endpoints") instanceof List<?>);
        List<?> endpoints = (List<?>) spec.get("endpoints");
        require(endpoints.size() == 1);
        Map<?, ?> endpoint = map(endpoints.getFirst());
        require(endpoint.get("port") instanceof String p && nonBlank(p) && p.length() <= 63);
        String port = (String) endpoint.get("port");
        require(service.getSpec() != null && service.getSpec().getPorts() != null
                && service.getSpec().getPorts().size() <= 100);
        long matches = service.getSpec().getPorts().stream().filter(p -> p != null && port.equals(p.getName())
                && p.getPort() != null && p.getPort() > 0 && p.getPort() <= 65535
                && (p.getProtocol() == null || "TCP".equals(p.getProtocol()))).count();
        require(matches == 1);
        // Relabeling can rewrite destination/identity, invalidating this static attribution proof.
        if (endpoint.containsKey("relabelings")) {
            require(endpoint.get("relabelings") instanceof List<?> rules && rules.isEmpty());
        }
        String interval = duration(endpoint, "interval");
        String timeout = duration(endpoint, "scrapeTimeout");
        if (interval != null && timeout != null) require(durationMillis(timeout) <= durationMillis(interval));
        return new Configuration(interval, timeout);
    }

    private static NamespaceSelection namespaceSelection(Map<?, ?> spec, String namespace) {
        if (!spec.containsKey("namespaceSelector")) return NamespaceSelection.CURRENT;
        Map<?, ?> selector = map(spec.get("namespaceSelector"));
        require(selector.keySet().stream().allMatch(key -> "any".equals(key) || "matchNames".equals(key)));
        if (selector.containsKey("any")) require(selector.get("any") instanceof Boolean);
        boolean any = Boolean.TRUE.equals(selector.get("any"));
        List<?> names = List.of();
        if (selector.containsKey("matchNames")) {
            require(selector.get("matchNames") instanceof List<?>);
            names = (List<?>) selector.get("matchNames");
            require(names.size() <= 100 && names.stream().allMatch(n -> n instanceof String s && nonBlank(s) && s.length() <= 63));
            require(new HashSet<>(names).size() == names.size());
        }
        if (any) return NamespaceSelection.BROAD;
        if (names.isEmpty()) return NamespaceSelection.CURRENT;
        if (!names.contains(namespace)) return NamespaceSelection.EXCLUDED;
        return names.size() == 1 ? NamespaceSelection.CURRENT : NamespaceSelection.BROAD;
    }

    private static void validateSelector(Map<?, ?> selector) {
        require(selector.keySet().stream().allMatch(key -> "matchLabels".equals(key) || "matchExpressions".equals(key)));
        if (selector.containsKey("matchLabels")) {
            Map<?, ?> labels = map(selector.get("matchLabels"));
            require(labels.size() <= 64);
            labels.forEach((key, value) -> require(labelKey(key) && labelValue(value)));
        }
        if (selector.containsKey("matchExpressions")) {
            require(selector.get("matchExpressions") instanceof List<?>);
            List<?> expressions = (List<?>) selector.get("matchExpressions");
            require(expressions.size() <= 64);
            for (Object item : expressions) {
                Map<?, ?> expression = map(item);
                require(expression.keySet().stream().allMatch(key -> Set.of("key", "operator", "values").contains(key)));
                require(labelKey(expression.get("key")) && expression.get("operator") instanceof String);
                String operator = (String) expression.get("operator");
                List<?> values = List.of();
                if (expression.containsKey("values")) {
                    require(expression.get("values") instanceof List<?>);
                    values = (List<?>) expression.get("values");
                }
                require(values.size() <= 64 && values.stream().allMatch(ServiceMonitorProbe::labelValue));
                switch (operator) {
                    case "In", "NotIn" -> require(!values.isEmpty());
                    case "Exists", "DoesNotExist" -> require(values.isEmpty());
                    default -> throw new IllegalArgumentException("Unsupported selector");
                }
            }
        }
    }

    private static boolean matches(Map<?, ?> selector, Map<String, String> serviceLabels) {
        Map<String, String> labels = serviceLabels == null ? Map.of() : serviceLabels;
        require(labels.size() <= 256);
        labels.forEach((key, value) -> require(labelKey(key) && labelValue(value)));
        if (selector.get("matchLabels") instanceof Map<?, ?> required) {
            for (var entry : required.entrySet()) {
                if (!entry.getValue().equals(labels.get(entry.getKey()))) return false;
            }
        }
        if (selector.get("matchExpressions") instanceof List<?> expressions) {
            for (Object item : expressions) {
                Map<?, ?> expression = (Map<?, ?>) item;
                Object key = expression.get("key");
                List<?> values = expression.get("values") instanceof List<?> list ? list : List.of();
                boolean match = switch ((String) expression.get("operator")) {
                    case "In" -> labels.containsKey(key) && values.contains(labels.get(key));
                    case "NotIn" -> !labels.containsKey(key) || !values.contains(labels.get(key));
                    case "Exists" -> labels.containsKey(key);
                    case "DoesNotExist" -> !labels.containsKey(key);
                    default -> false;
                };
                if (!match) return false;
            }
        }
        return true;
    }

    private static String duration(Map<?, ?> endpoint, String key) {
        if (!endpoint.containsKey(key)) return null;
        require(endpoint.get(key) instanceof String);
        String value = (String) endpoint.get(key);
        require(!value.isEmpty() && value.length() <= 64);
        durationMillis(value);
        return value;
    }

    private static long durationMillis(String value) {
        Matcher match = DURATION.matcher(value);
        require(match.matches());
        long total = 0;
        for (int i = 0; i < UNIT_MILLIS.length; i++) {
            if (match.group(i + 1) != null) {
                total = Math.addExact(total, Math.multiplyExact(Long.parseLong(match.group(i + 1)), UNIT_MILLIS[i]));
            }
        }
        require(total > 0 && total <= Long.MAX_VALUE / 1_000_000L);
        return total;
    }

    private static boolean labelKey(Object value) {
        if (!(value instanceof String s) || s.isBlank() || s.length() > 317) return false;
        String[] parts = s.split("/", -1);
        if (parts.length > 2 || !labelName(parts[parts.length - 1])) return false;
        if (parts.length == 1) return true;
        if (parts[0].isEmpty() || parts[0].length() > 253) return false;
        for (String segment : parts[0].split("\\.", -1)) {
            if (segment.length() > 63 || !segment.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) return false;
        }
        return true;
    }

    private static boolean labelValue(Object value) {
        return value instanceof String s && (s.isEmpty() || labelName(s));
    }

    private static boolean labelName(String value) {
        return value.length() <= 63 && value.matches("[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?");
    }

    private static Map<?, ?> map(Object value) {
        require(value instanceof Map<?, ?>);
        return (Map<?, ?>) value;
    }

    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Incomplete or ambiguous configuration");
    }
    private static void checkpoint() { CollectionBudget.checkpointCurrent(); }
    private static Result unknown(String detail) { return new Result(ScrapeReadiness.UNKNOWN, null, null, null, detail); }
    private static Result result(ScrapeReadiness readiness, Configuration configuration, String detail) {
        return new Result(readiness, true, configuration.interval(), configuration.timeout(), detail);
    }
}
