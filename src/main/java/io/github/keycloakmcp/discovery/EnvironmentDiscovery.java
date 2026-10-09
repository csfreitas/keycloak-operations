package io.github.keycloakmcp.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader;
import io.github.keycloakmcp.adapter.infrastructure.ClusterClient;
import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.config.DiscoveryConfig;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EnvironmentDiscovery {

    private static final Logger LOG = Logger.getLogger(EnvironmentDiscovery.class);

    private final InfrastructureClientFactory clientFactory;

    @ConfigProperty(name = "collection.operation-timeout-ms", defaultValue = "30000")
    long collectionTimeoutMs = CollectionBudget.DEFAULT_TIMEOUT_MS;

    @Inject
    public EnvironmentDiscovery(DiscoveryConfig discoveryConfig, InfrastructureClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * Target-aware discovery: uses the infrastructure client bound to the target.
     * Missing or unsupported infrastructure stays target-scoped UNKNOWN; never
     * probe an unrelated cluster from the process's ambient configuration.
     */
    public EnvironmentInfo discover(Target target) {
        EnvironmentInfo observed = discoverObserved(target);
        return new EnvironmentInfo(observed.runtime(), observed.confidence(), observed.platform(), observed.namespace(),
                observed.evidence(), observed.targetId(), observed.clusterVersion(), observed.clusterPlatform(),
                target == null ? null : target.infrastructureTypeOrNone(), observed.apiCapabilities());
    }

    private EnvironmentInfo discoverObserved(Target target) {
        if (target == null) {
            return unknownInfo(null, List.of("No registered target supplied; infrastructure discovery was not attempted"));
        }
        String targetId = target.id().value();
        InfrastructureType type = target.infrastructureTypeOrNone();
        if (type != InfrastructureType.OPENSHIFT && type != InfrastructureType.KUBERNETES) {
            return unknownInfo(targetId, List.of(type == InfrastructureType.NONE
                    ? "Target has no infrastructure binding; runtime cannot be confirmed"
                    : "Infrastructure collector is not implemented for the configured hosting type; runtime cannot be confirmed"));
        }
        String credentialRef = target.infrastructure().credentialRef();
        if (credentialRef == null || credentialRef.isBlank()) {
            return unknownInfo(targetId, List.of(
                    "Target has no explicit infrastructure credential reference; ambient cluster discovery is disabled"));
        }
        String namespace = target.infrastructure().namespace();
        String clusterId = target.infrastructure().clusterId();
        if (namespace == null || namespace.isBlank() || clusterId == null || clusterId.isBlank()) {
            return unknownInfo(targetId, List.of(
                    "Target has no explicit infrastructure connection and namespace; ambient cluster discovery is disabled"));
        }
        if (Thread.currentThread().isInterrupted()) {
            return unknownInfo(targetId, List.of("Infrastructure discovery was interrupted before collection"));
        }
        List<String> evidence = new ArrayList<>();
        try (var scope = CollectionBudget.open(targetId, collectionTimeoutMs)) {
            scope.budget().checkpoint();
            Optional<ClusterClient> clientOpt = clientFactory.resolve(target);
            scope.budget().checkpoint();
            if (clientOpt.isEmpty()) {
                return unknownInfo(targetId, List.of("Infrastructure client unavailable for the registered target"));
            }
            ClusterClient clusterClient = clientOpt.get();
            if (!namespace.equals(clusterClient.namespace())) {
                return unknownInfo(targetId, List.of("Infrastructure client scope does not match the registered target"));
            }
            KubernetesClient k8s = clusterClient.kubernetes();
            // Read one complete API-group snapshot. A configured type (or a failed probe)
            // is not an observed runtime and must not trigger optimistic classification.
            var groups = BoundedKubernetesReader.apiGroups(k8s);
            boolean hasRouteApi = groups.stream().anyMatch(group -> "route.openshift.io".equals(group.getName()));
            boolean hasConfigApi = groups.stream().anyMatch(group -> "config.openshift.io".equals(group.getName()));
            if (hasRouteApi) evidence.add("API group present: route.openshift.io");
            if (hasConfigApi) evidence.add("API group present: config.openshift.io");
            boolean isOpenShift = hasRouteApi || hasConfigApi;
            var capabilities = new ClusterApiCapabilities(
                    servedV1(groups, "route.openshift.io"), servedV1(groups, "config.openshift.io"));
            if (!isOpenShift) evidence.add("Complete API-group observation contains no OpenShift route or config API group");
            String version = readVersion(k8s, evidence);
            evidence.add("Current namespace: " + namespace);
            String platform = isOpenShift ? "openshift" : "kubernetes";
            return new EnvironmentInfo(isOpenShift ? RuntimeType.OPENSHIFT : RuntimeType.KUBERNETES,
                    DetectionConfidence.CONFIRMED, platform, namespace, List.copyOf(evidence), targetId,
                    version, platform, type, capabilities);
        } catch (CollectionBudget.Aborted e) {
            evidence.add(e.reason());
            return unknownInfo(targetId, List.copyOf(evidence));
        } catch (RuntimeException e) {
            LOG.debug("Target-scoped infrastructure discovery was unavailable");
            evidence.add("Infrastructure discovery unavailable; runtime could not be confirmed");
            return unknownInfo(targetId, List.copyOf(evidence));
        }
    }

    private static ClusterApiCapabilities.ApiAvailability servedV1(
            List<io.fabric8.kubernetes.api.model.APIGroup> groups, String name) {
        var group = groups.stream().filter(candidate -> name.equals(candidate.getName())).findFirst();
        if (group.isEmpty()) return ClusterApiCapabilities.ApiAvailability.NOT_SERVED;
        return group.get().getVersions().stream().anyMatch(version -> "v1".equals(version.getVersion()))
                ? ClusterApiCapabilities.ApiAvailability.SERVED : ClusterApiCapabilities.ApiAvailability.UNSUPPORTED_VERSION;
    }

    /**
     * Legacy source-compatible entry point; global probing is intentionally disabled.
     * Configure and authorize a registered target instead.
     */
    @Deprecated
    public EnvironmentInfo discover() {
        return unknownInfo(null, List.of("Global discovery is disabled; select a registered target"));
    }

    private String readVersion(KubernetesClient k8s, List<String> evidence) {
        try {
            String version = BoundedKubernetesReader.version(k8s);
            evidence.add("Cluster version: " + version);
            return version;
        } catch (CollectionBudget.Aborted e) {
            evidence.add(e.reason());
            evidence.add("Cluster version unavailable; observed runtime classification is retained");
        } catch (RuntimeException e) {
            LOG.debug("Cluster version observation was unavailable");
            evidence.add("Cluster version unavailable; observed runtime classification is retained");
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

}
