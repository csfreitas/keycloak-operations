package io.github.keycloakmcp.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.keycloakmcp.config.DiscoveryConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EnvironmentDiscovery {

    private static final Logger LOG = Logger.getLogger(EnvironmentDiscovery.class);

    private final DiscoveryConfig discoveryConfig;

    @Inject
    public EnvironmentDiscovery(DiscoveryConfig discoveryConfig) {
        this.discoveryConfig = discoveryConfig;
    }

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
                    List.copyOf(evidence));
        } catch (RuntimeException e) {
            LOG.debugf(e, "OpenShift discovery failed");
            return null;
        }
    }

    private EnvironmentInfo tryKubernetes() {
        List<String> evidence = new ArrayList<>();
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            // Prefer classifying as Kubernetes only when OpenShift-specific APIs are absent
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
                        List.copyOf(evidence));
            }

            // Touch the API server for real evidence
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
                    List.copyOf(evidence));
        } catch (RuntimeException e) {
            LOG.debugf(e, "Kubernetes discovery failed");
            return null;
        }
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
