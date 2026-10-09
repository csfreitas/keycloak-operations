package io.github.keycloakmcp.service.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.KubernetesInstallationBinding;
import jakarta.enterprise.context.ApplicationScoped;

/** Namespace-scoped candidate metadata. Candidates are not proof of Keycloak product identity. */
@ApplicationScoped
public class InstallationCandidateCollector {
    private static final int LIMIT = 100;

    public List<KubernetesInstallationBinding> discover(ClusterClient cluster, String namespace) {
        try {
            requireScope(cluster, namespace);
            var client = cluster.kubernetes();
            List<KubernetesInstallationBinding> result = new ArrayList<>();
            Set<String> identities = new HashSet<>(), uids = new HashSet<>();
            add(result, BoundedKubernetesReader.list(client, BoundedKubernetesReader.DEPLOYMENTS, namespace, LIMIT), identities, uids);
            add(result, BoundedKubernetesReader.list(client, BoundedKubernetesReader.STATEFUL_SETS, namespace, LIMIT), identities, uids);
            for (var group : BoundedKubernetesReader.apiGroups(client)) {
                if (!"k8s.keycloak.org".equals(group.getName())) continue;
                var preferred = group.getPreferredVersion();
                if (preferred == null || preferred.getGroupVersion() == null) throw new IllegalStateException();
                String api = preferred.getGroupVersion();
                new KubernetesInstallationBinding(api, "Keycloak", "validation", "validation");
                add(result, BoundedKubernetesReader.list(client, BoundedKubernetesReader.keycloaks(api), namespace, LIMIT), identities, uids);
            }
            InstallationServiceCollector.checkpoint();
            return result.stream().sorted(Comparator.comparing(KubernetesInstallationBinding::kind)
                    .thenComparing(KubernetesInstallationBinding::name)).toList();
        } catch (CollectionBudget.Aborted e) {
            if (CollectionBudget.current() != null) throw e;
            throw unavailable();
        } catch (RuntimeException e) {
            throw unavailable();
        }
    }

    private static McpException unavailable() {
        return McpException.evidenceCollectionFailed("Candidate discovery unavailable or incomplete; check scope, API permissions and the 100-candidate limit", null);
    }

    public boolean stillExists(ClusterClient cluster, String namespace, KubernetesInstallationBinding binding) {
        try {
            requireScope(cluster, namespace);
            if (binding == null) return false;
            HasMetadata resource;
            var client = cluster.kubernetes();
            if ("Deployment".equals(binding.kind())) resource = BoundedKubernetesReader.get(client, BoundedKubernetesReader.DEPLOYMENTS, namespace, binding.name());
            else if ("StatefulSet".equals(binding.kind())) resource = BoundedKubernetesReader.get(client, BoundedKubernetesReader.STATEFUL_SETS, namespace, binding.name());
            else resource = BoundedKubernetesReader.get(client, BoundedKubernetesReader.keycloaks(binding.apiVersion()), namespace, binding.name());
            InstallationServiceCollector.checkpoint();
            return resource != null && namespace.equals(resource.getMetadata().getNamespace()) && binding.equals(binding(resource));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void add(List<KubernetesInstallationBinding> out, List<? extends HasMetadata> resources,
            Set<String> identities, Set<String> uids) {
        for (var resource : resources) {
            InstallationServiceCollector.checkpoint();
            var candidate = binding(resource);
            if (!identities.add(candidate.apiVersion() + "/" + candidate.kind() + "/" + candidate.name())
                    || !uids.add(candidate.uid()) || out.size() >= LIMIT) throw new IllegalStateException();
            out.add(candidate);
        }
    }

    private static void requireScope(ClusterClient cluster, String namespace) {
        InstallationServiceCollector.checkpoint();
        if (cluster == null || namespace == null || namespace.isBlank() || !namespace.equals(cluster.namespace())) throw new IllegalStateException();
    }

    private static KubernetesInstallationBinding binding(HasMetadata resource) {
        return new KubernetesInstallationBinding(resource.getApiVersion(), resource.getKind(), resource.getMetadata().getName(), resource.getMetadata().getUid());
    }
}
