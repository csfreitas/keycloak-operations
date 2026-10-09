package io.github.keycloakmcp.domain.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;
import io.github.keycloakmcp.discovery.DetectionConfidence;

/**
 * Shared completeness contract for the implemented infrastructure inventory.
 * Completeness describes collected evidence, not healthy configuration, runtime
 * reachability, atomicity or visibility of every resource in the environment.
 */
public final class InfrastructureCoverage {

    private InfrastructureCoverage() { }

    public static boolean isComplete(InfrastructureInventory inventory) {
        if (inventory == null) return false;
        ClusterInfo cluster = inventory.cluster();
        KeycloakWorkloadInfo workload = inventory.keycloak();
        List<PodInventoryItem> pods = inventory.pods();
        TopologyInfo topology = inventory.topology();
        boolean podsKnown = pods != null && pods.stream().noneMatch(java.util.Objects::isNull);
        boolean zonesKnown = podsKnown && topology != null && topologyMatches(pods, topology.podsByZone(), true)
                && topology.zoneCount() == topology.podsByZone().size();
        boolean nodesKnown = podsKnown && topology != null && topologyMatches(pods, topology.podsByNode(), false);
        boolean workloadKnown = workload != null && workload.deploymentMethod() != null
                && workload.deploymentMethod() != DeploymentMethod.UNKNOWN
                && present(workload.name()) && present(workload.namespace());
        HpaInfo hpa = inventory.hpa();
        return inventory.warnings() != null && inventory.warnings().isEmpty()
                && discoveryComplete(inventory)
                && present(inventory.runtime()) && !"UNKNOWN".equals(inventory.runtime())
                && cluster != null && present(cluster.distribution()) && present(cluster.version())
                && cluster.nodeCount() >= 0 && cluster.zoneCount() >= 0
                && workloadKnown && present(workload.uid()) && present(workload.kind()) && present(workload.apiVersion())
                && workload.desiredReplicas() >= 0 && workload.readyReplicas() >= 0
                && workload.currentReplicas() >= 0 && workload.availableReplicas() >= 0
                && podsKnown && pods.stream().allMatch(p -> p.restartCount() >= 0) && zonesKnown && nodesKnown
                && inventory.scheduling() != null && hpa != null
                && (!hpa.present() || hpa.minReplicas() >= 0 && hpa.maxReplicas() >= 0)
                && inventory.pdb() != null && inventory.resources() != null && inventory.probes() != null
                && inventory.networking() != null && inventory.networking().complete();
    }

    private static boolean discoveryComplete(InfrastructureInventory inventory) {
        var discovery = inventory.discovery();
        if (discovery == null || !present(inventory.targetId())
                || !inventory.targetId().equals(discovery.targetId())
                || discovery.confidence() != DetectionConfidence.CONFIRMED
                || !present(discovery.clusterVersion())
                || discovery.runtime() == null || !discovery.runtime().name().equals(inventory.runtime())
                || inventory.keycloak() == null || !present(discovery.namespace())
                || !discovery.namespace().equals(inventory.keycloak().namespace())) {
            return false;
        }
        var capabilities = discovery.apiCapabilities();
        if (capabilities == null || capabilities.routeV1() == ApiAvailability.UNKNOWN
                || capabilities.routeV1() == ApiAvailability.UNSUPPORTED_VERSION
                || capabilities.configV1() == ApiAvailability.UNKNOWN
                || capabilities.configV1() == ApiAvailability.UNSUPPORTED_VERSION) {
            return false;
        }
        return switch (discovery.runtime()) {
            case KUBERNETES -> capabilities.routeV1() == ApiAvailability.NOT_SERVED
                    && capabilities.configV1() == ApiAvailability.NOT_SERVED;
            case OPENSHIFT -> capabilities.configV1() == ApiAvailability.SERVED;
            default -> false;
        };
    }

    private static boolean topologyMatches(List<PodInventoryItem> pods, Map<String, Integer> buckets, boolean zones) {
        if (buckets == null) return false;
        Map<String, Integer> observed = new LinkedHashMap<>();
        for (PodInventoryItem pod : pods) {
            String bucket = zones ? pod.zone() : pod.nodeName();
            if (!present(bucket)) return false;
            observed.merge(bucket, 1, Integer::sum);
        }
        return observed.equals(buckets);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
