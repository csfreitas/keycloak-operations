package io.github.keycloakmcp.service.platform;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.inventory.CollectionWarning;
import io.github.keycloakmcp.domain.inventory.CollectionWarning.WarningCode;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;

/** Shared internal association only; raw Services must never become an API/report DTO. */
final class InstallationServiceCollector {
    static final int LIMIT = 500;

    record Selection(Map<String, Service> services, List<Service> namespaceServices, boolean associationComplete) { }

    Selection collect(ClusterClient cluster, KeycloakWorkloadInfo root, List<CollectionWarning> warnings) {
        checkpoint();
        var k8s = cluster.kubernetes();
        String namespace = root.namespace();
        var deployments = checkedResources(BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.DEPLOYMENTS, namespace), namespace);
        checkpoint();
        var statefulSets = checkedResources(BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.STATEFUL_SETS, namespace), namespace);
        var templates = new LinkedHashMap<String, Map<String, String>>();
        deployments.forEach(d -> addTemplate(templates, d.getMetadata().getUid(), labels(d.getSpec() == null ? null : d.getSpec().getTemplate())));
        statefulSets.forEach(s -> addTemplate(templates, s.getMetadata().getUid(), labels(s.getSpec() == null ? null : s.getSpec().getTemplate())));
        boolean rootPresent = deployments.stream().anyMatch(d -> identity(d, root))
                || statefulSets.stream().anyMatch(s -> identity(s, root));
        var services = new LinkedHashMap<String, Service>();
        if (!rootPresent || templates.get(root.uid()) == null) {
            warning(warnings, WarningCode.BINDING_MISMATCH);
            return new Selection(services, List.of(), false);
        }
        checkpoint();
        var pods = checkedResources(BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.PODS, namespace), namespace);
        checkpoint();
        var replicaSets = checkedResources(BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.REPLICA_SETS, namespace), namespace);
        var ownedRs = replicaSets.stream().filter(rs -> ownedBy(rs, root.uid(), root.kind(), root.apiVersion()))
                .map(rs -> rs.getMetadata().getUid()).toList();
        checkpoint();
        List<Service> namespaceServices = checkedResources(BoundedKubernetesReader.list(k8s, BoundedKubernetesReader.SERVICES, namespace), namespace);
        boolean associationComplete = true;
        for (Service service : namespaceServices) {
            checkpoint();
            if (service.getSpec() == null) {
                warning(warnings, WarningCode.NOT_SUPPORTED);
                associationComplete = false;
                continue;
            }
            var selector = service.getSpec().getSelector();
            if (!validLabels(selector)) throw incomplete();
            if (selector == null || selector.isEmpty() || "ExternalName".equals(service.getSpec().getType())) {
                warning(warnings, WarningCode.NOT_SUPPORTED);
                // Networking still reports unsupported namespace resources. They must not
                // invalidate an independently proven selector-backed association unless they
                // themselves claim that selector. Keep every Service in namespaceServices so
                // a ServiceMonitor selecting a foreign/unsupported backend remains ambiguous.
                if (selector != null && !selector.isEmpty() && matches(selector, templates.get(root.uid()))) {
                    associationComplete = false;
                }
                continue;
            }
            if (!matches(selector, templates.get(root.uid()))) continue;
            boolean sharedTemplate = templates.entrySet().stream().anyMatch(e ->
                    !e.getKey().equals(root.uid()) && matches(selector, e.getValue()));
            boolean foreignPod = pods.stream().filter(p -> matches(selector, p.getMetadata().getLabels()))
                    .anyMatch(p -> !ownedBy(p, root.uid(), root.kind(), root.apiVersion())
                            && ownedRs.stream().noneMatch(uid -> ownedBy(p, uid, "ReplicaSet", "apps/v1")));
            if (sharedTemplate || foreignPod) {
                warning(warnings, WarningCode.AMBIGUOUS_RESOURCE);
                associationComplete = false;
                continue;
            }
            services.put(service.getMetadata().getName(), service);
        }
        return new Selection(services, List.copyOf(namespaceServices), associationComplete);
    }

    static void checkpoint() {
        CollectionBudget.checkpointCurrent();
    }

    private static <T extends HasMetadata> List<T> checkedResources(List<T> resources, String namespace) {
        checkpoint();
        if (namespace == null || namespace.isBlank() || resources == null || resources.size() > LIMIT) throw incomplete();
        Set<String> names = new HashSet<>(), uids = new HashSet<>();
        for (T resource : resources) {
            checkpoint();
            if (resource == null || resource.getMetadata() == null
                    || !valid(resource.getMetadata().getUid(), 128) || !valid(resource.getMetadata().getName(), 253)
                    || !namespace.equals(resource.getMetadata().getNamespace())
                    || !validLabels(resource.getMetadata().getLabels())
                    || !names.add(resource.getMetadata().getName()) || !uids.add(resource.getMetadata().getUid())) {
                throw incomplete();
            }
        }
        return resources;
    }

    private static void addTemplate(Map<String, Map<String, String>> templates, String uid, Map<String, String> labels) {
        if (labels == null || !validLabels(labels) || templates.putIfAbsent(uid, labels) != null) throw incomplete();
    }

    private static Map<String, String> labels(PodTemplateSpec template) {
        if (template == null || template.getMetadata() == null) return null;
        return template.getMetadata().getLabels() == null ? Map.of() : template.getMetadata().getLabels();
    }

    private static boolean matches(Map<String, String> selector, Map<String, String> labels) {
        return labels != null && selector.entrySet().stream().allMatch(e -> Objects.equals(e.getValue(), labels.get(e.getKey())));
    }

    private static boolean identity(HasMetadata resource, KeycloakWorkloadInfo root) {
        return Objects.equals(root.uid(), resource.getMetadata().getUid()) && Objects.equals(root.name(), resource.getMetadata().getName())
                && Objects.equals(root.kind(), resource.getKind()) && Objects.equals(root.apiVersion(), resource.getApiVersion());
    }

    private static boolean ownedBy(HasMetadata resource, String uid, String kind, String apiVersion) {
        return resource.getMetadata().getOwnerReferences() != null && resource.getMetadata().getOwnerReferences().stream()
                .anyMatch(o -> o != null && uid.equals(o.getUid()) && kind.equals(o.getKind()) && apiVersion.equals(o.getApiVersion())
                        && Boolean.TRUE.equals(o.getController()));
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean valid(String value, int max) { return present(value) && value.length() <= max; }
    private static boolean validLabels(Map<String, String> labels) {
        return labels == null || labels.size() <= 256 && labels.entrySet().stream()
                .allMatch(e -> valid(e.getKey(), 317) && e.getValue() != null && e.getValue().length() <= 63);
    }
    private static IllegalStateException incomplete() { return new IllegalStateException("Incomplete service association scope"); }
    private static void warning(List<CollectionWarning> warnings, WarningCode code) {
        var warning = new CollectionWarning(code, "networking", null);
        if (!warnings.contains(warning)) warnings.add(warning);
    }
}
