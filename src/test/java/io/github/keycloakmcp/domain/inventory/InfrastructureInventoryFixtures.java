package io.github.keycloakmcp.domain.inventory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.discovery.ClusterApiCapabilities;
import io.github.keycloakmcp.discovery.ClusterApiCapabilities.ApiAvailability;
import io.github.keycloakmcp.discovery.DetectionConfidence;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.discovery.RuntimeType;
import io.github.keycloakmcp.target.InfrastructureType;

/** Explicit observed fixtures shared by coverage, health and snapshot regressions. */
public final class InfrastructureInventoryFixtures {
    private InfrastructureInventoryFixtures() { }

    public static InfrastructureInventory complete(String targetId) {
        return observed(targetId, "KUBERNETES", new ClusterInfo("kubernetes", "v1.32.2", null, 1, 1), 1,
                List.of(new PodInventoryItem("sso-0", "node-a", "zone-a", true, 0, false)),
                new TopologyInfo(Map.of("zone-a", 1), Map.of("node-a", 1), 1), List.of());
    }

    public static InfrastructureInventory withoutRuntimeObservation(String targetId) {
        return observed(targetId, "UNKNOWN", new ClusterInfo(null, "v1.32.2", null, 1, 1), 1,
                complete(targetId).pods(), complete(targetId).topology(), List.of());
    }

    public static InfrastructureInventory withoutVersionObservation(String targetId) {
        return observed(targetId, "KUBERNETES", new ClusterInfo("kubernetes", null, null, 1, 1), 1,
                complete(targetId).pods(), complete(targetId).topology(), List.of());
    }

    public static InfrastructureInventory scaledToZero(String targetId) {
        return observed(targetId, "KUBERNETES", new ClusterInfo("kubernetes", "v1.32.2", null, 1, 1), 0,
                List.of(), new TopologyInfo(Map.of(), Map.of(), 0), List.of());
    }

    public static InfrastructureInventory observed(String targetId, String runtime, ClusterInfo cluster, int replicas,
            List<PodInventoryItem> pods, TopologyInfo topology, List<CollectionWarning> warnings) {
        return new InfrastructureInventory(targetId, runtime, cluster,
                new KeycloakWorkloadInfo(DeploymentMethod.DEPLOYMENT, "ns", "sso", replicas, replicas, replicas, replicas,
                        "apps/v1", "Deployment", "workload-uid"),
                pods, topology, new SchedulingInfo(false, false), HpaInfo.absent(), PdbInfo.absent(),
                new ResourceConfig(null, null, null, null), new ProbeInfo(false, false, false),
                new NetworkingInfo(false, null, false), warnings, Instant.EPOCH,
                new EnvironmentInfo(RuntimeType.valueOf(runtime),
                        "UNKNOWN".equals(runtime) ? DetectionConfidence.UNKNOWN : DetectionConfidence.CONFIRMED,
                        runtime.toLowerCase(java.util.Locale.ROOT), "ns", List.of(), targetId,
                        cluster == null ? null : cluster.version(), runtime.toLowerCase(java.util.Locale.ROOT),
                        InfrastructureType.KUBERNETES,
                        "KUBERNETES".equals(runtime)
                                ? new ClusterApiCapabilities(ApiAvailability.NOT_SERVED, ApiAvailability.NOT_SERVED)
                                : ClusterApiCapabilities.unknown()));
    }

    public static InfrastructureInventory withDiscovery(InfrastructureInventory inventory, EnvironmentInfo discovery) {
        return new InfrastructureInventory(inventory.targetId(), inventory.runtime(), inventory.cluster(), inventory.keycloak(),
                inventory.pods(), inventory.topology(), inventory.scheduling(), inventory.hpa(), inventory.pdb(),
                inventory.resources(), inventory.probes(), inventory.networking(), inventory.warnings(), inventory.collectedAt(), discovery);
    }
}
