package io.github.keycloakmcp.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.config.DiscoveryConfig;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EnvironmentDiscovery {

    private static final Logger LOG = Logger.getLogger(EnvironmentDiscovery.class);

    private final DiscoveryConfig discoveryConfig;
    private final InfrastructureClientFactory clientFactory;

    @Inject
    public EnvironmentDiscovery(DiscoveryConfig discoveryConfig, InfrastructureClientFactory clientFactory) {
        this.discoveryConfig = discoveryConfig;
        this.clientFactory = clientFactory;
    }

    /**
     * Target-aware discovery: uses the infrastructure client bound to the target.
     * Falls back to global discovery when the target has no configured infrastructure.
     */
    public EnvironmentInfo discover(Target target) {
        if (target == null || !target.hasInfrastructure()) {
            return discover();
        }
        InfrastructureType type = target.infrastructureTypeOrNone();
        if (type == InfrastructureType.NONE || type == InfrastructureType.VM) {
            return discover();
        }

        String targetId = target.id().value();
        Optional<ClusterClient> clientOpt = clientFactory.resolve(target);
        if (clientOpt.isEmpty()) {
            return unknownInfo(targetId, List.of("Infrastructure client unavailable for target " + targetId));
        }

        ClusterClient clusterClient = clientOpt.get();
        KubernetesClient k8s = clusterClient.kubernetes();
        List<String> evidence = new ArrayList<>();

        try {
            if (clusterClient.type() == InfrastructureType.OPENSHIFT) {
                return buildOpenShiftInfo(k8s, clusterClient.namespace(), targetId, evidence);
            } else {
                return buildKubernetesInfo(k8s, clusterClient.namespace(), targetId, evidence);
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Target-aware discovery failed for target=%s", targetId);
            evidence.add("Discovery probe failed: " + e.getMessage());
            return unknownInfo(targetId, List.copyOf(evidence));
        }
    }

    /**
     * Global discovery (no specific target).
     * Uses direct KubernetesClientBuilder (in-cluster or KUBECONFIG env var).
     *
     * @deprecated Prefer {@link #discover(Target)} for multi-target environments.
     */
    public EnvironmentInfo discover() {
        if (discoveryConfig.openshift().enabled()) {
            EnvironmentInfo openshift = tryOpenShift();
            if (openshift != null) {
                return openshift;
            }
        }

        if (discoveryConfig.kubernetes().enabled()) {
            EnvironmentInfo kubernetes = tryKubernetes();
            if (kubernetes != null) {
                return kubernetes;
            }
        }

        List<String> evidence = new ArrayList<>();
        if (!discoveryConfig.openshift().enabled() && !discoveryConfig.kubernetes().enabled()) {
            evidence.add("discovery.openshift.enabled=false");
            evidence.add("discovery.kubernetes.enabled=false");
            evidence.add("Cluster API probing is disabled; runtime cannot be confirmed");
        } else {
            evidence.add("Configured discovery probes did not return usable API evidence");
        }

        return new EnvironmentInfo(
                RuntimeType.UNKNOWN,
                DetectionConfidence.UNKNOWN,
                "unknown",
                null,
                List.copyOf(evidence));
    }

    private EnvironmentInfo tryOpenShift() {
        List<String> evidence = new ArrayList<>();
        try (OpenShiftClient client = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class)) {
            if (client == null) {
                evidence.add("OpenShift client adapt() returned null");
                LOG.debug("OpenShift discovery skipped: client unavailable");
                return null;
            }

            boolean hasRouteApi = hasApiGroup(client, "route.openshift.io");
            boolean hasConfigApi = hasApiGroup(client, "config.openshift.io");
            if (!hasRouteApi && !hasConfigApi) {
                evidence.add("OpenShift APIs not found (route.openshift.io / config.openshift.io)");
                LOG.debug("OpenShift discovery: API groups not present");
                return null;
            }

            if (hasRouteApi) {
                evidence.add("API group present: route.openshift.io");
            }
            if (hasConfigApi) {
                evidence.add("API group present: config.openshift.io");
            }

            String namespace = client.getNamespace();
            if (namespace != null && !namespace.isBlank()) {
                evidence.add("Current namespace: " + namespace);
            }

            String version = null;
            try {
                version = client.getKubernetesVersion() == null ? null : client.getKubernetesVersion().getGitVersion();
            } catch (RuntimeException e) {
                LOG.debugf(e, "Unable to read OpenShift/Kubernetes version");
            }
            if (version != null) {
                evidence.add("Cluster version: " + version);
            }

            return new EnvironmentInfo(
                    RuntimeType.OPENSHIFT,
                    DetectionConfidence.CONFIRMED,
                    "openshift",
                    namespace,
                    List.copyOf(evidence),
                    null,
                    version,
                    "openshift");
        } catch (RuntimeException e) {
            LOG.debugf(e, "OpenShift discovery failed");
            return null;
        }
    }

    private EnvironmentInfo tryKubernetes() {
        List<String> evidence = new ArrayList<>();
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            boolean hasRouteApi = hasApiGroup(client, "route.openshift.io");
            boolean hasConfigApi = hasApiGroup(client, "config.openshift.io");
            if (hasRouteApi || hasConfigApi) {
                evidence.add("OpenShift API groups detected while probing Kubernetes");
                if (hasRouteApi) {
                    evidence.add("API group present: route.openshift.io");
                }
                if (hasConfigApi) {
                    evidence.add("API group present: config.openshift.io");
                }
                String namespace = client.getNamespace();
                return new EnvironmentInfo(
                        RuntimeType.OPENSHIFT,
                        DetectionConfidence.CONFIRMED,
                        "openshift",
                        namespace,
                        List.copyOf(evidence),
                        null,
                        null,
                        "openshift");
            }

            var version = client.getKubernetesVersion();
            if (version == null || version.getGitVersion() == null) {
                evidence.add("Kubernetes API reachable but version payload missing");
                return new EnvironmentInfo(
                        RuntimeType.KUBERNETES,
                        DetectionConfidence.DETECTED,
                        "kubernetes",
                        client.getNamespace(),
                        List.copyOf(evidence));
            }

            evidence.add("Kubernetes API reachable");
            evidence.add("Cluster version: " + version.getGitVersion());
            String namespace = client.getNamespace();
            if (namespace != null && !namespace.isBlank()) {
                evidence.add("Current namespace: " + namespace);
            }

            return new EnvironmentInfo(
                    RuntimeType.KUBERNETES,
                    DetectionConfidence.CONFIRMED,
                    "kubernetes",
                    namespace,
                    List.copyOf(evidence),
                    null,
                    version.getGitVersion(),
                    "kubernetes");
        } catch (RuntimeException e) {
            LOG.debugf(e, "Kubernetes discovery failed");
            return null;
        }
    }

    private EnvironmentInfo buildOpenShiftInfo(
            KubernetesClient k8s, String namespace, String targetId, List<String> evidence) {

        boolean hasRouteApi = hasApiGroup(k8s, "route.openshift.io");
        boolean hasConfigApi = hasApiGroup(k8s, "config.openshift.io");

        if (hasRouteApi) {
            evidence.add("API group present: route.openshift.io");
        }
        if (hasConfigApi) {
            evidence.add("API group present: config.openshift.io");
        }

        if (!hasRouteApi && !hasConfigApi) {
            evidence.add("Target configured as OPENSHIFT but OpenShift API groups not found — treating as KUBERNETES");
            return buildKubernetesInfo(k8s, namespace, targetId, evidence);
        }

        String version = readVersion(k8s, evidence);
        if (namespace != null && !namespace.isBlank()) {
            evidence.add("Current namespace: " + namespace);
        }

        return new EnvironmentInfo(
                RuntimeType.OPENSHIFT,
                DetectionConfidence.CONFIRMED,
                "openshift",
                namespace,
                List.copyOf(evidence),
                targetId,
                version,
                "openshift");
    }

    private EnvironmentInfo buildKubernetesInfo(
            KubernetesClient k8s, String namespace, String targetId, List<String> evidence) {

        // Check if it is actually OpenShift despite being configured as KUBERNETES
        boolean hasRouteApi = hasApiGroup(k8s, "route.openshift.io");
        boolean hasConfigApi = hasApiGroup(k8s, "config.openshift.io");
        if (hasRouteApi || hasConfigApi) {
            evidence.add("OpenShift API groups detected; re-classifying as OPENSHIFT");
            return buildOpenShiftInfo(k8s, namespace, targetId, evidence);
        }

        String version = readVersion(k8s, evidence);
        if (namespace != null && !namespace.isBlank()) {
            evidence.add("Current namespace: " + namespace);
        }

        return new EnvironmentInfo(
                RuntimeType.KUBERNETES,
                DetectionConfidence.CONFIRMED,
                "kubernetes",
                namespace,
                List.copyOf(evidence),
                targetId,
                version,
                "kubernetes");
    }

    private String readVersion(KubernetesClient k8s, List<String> evidence) {
        try {
            var ver = k8s.getKubernetesVersion();
            if (ver != null && ver.getGitVersion() != null) {
                evidence.add("Cluster version: " + ver.getGitVersion());
                return ver.getGitVersion();
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Unable to read cluster version");
        }
        return null;
    }

    private static EnvironmentInfo unknownInfo(String targetId, List<String> evidence) {
        return new EnvironmentInfo(
                RuntimeType.UNKNOWN,
                DetectionConfidence.UNKNOWN,
                "unknown",
                null,
                evidence,
                targetId,
                null,
                null);
    }

    private static boolean hasApiGroup(KubernetesClient client, String apiGroup) {
        try {
            Set<String> groups = client.getApiGroups().getGroups().stream()
                    .map(g -> g.getName())
                    .collect(java.util.stream.Collectors.toSet());
            return groups.contains(apiGroup);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
