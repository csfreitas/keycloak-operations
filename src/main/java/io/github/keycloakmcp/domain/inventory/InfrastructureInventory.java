package io.github.keycloakmcp.domain.inventory;

import java.time.Instant;
import java.util.List;
import io.github.keycloakmcp.discovery.EnvironmentInfo;

/**
 * Structured, sanitized infrastructure inventory for one Target.
 * <p>
 * Never contains Secret contents, env vars with credentials, or raw Kubernetes objects.
 */
public record InfrastructureInventory(
        String targetId,
        String runtime,
        ClusterInfo cluster,
        KeycloakWorkloadInfo keycloak,
        List<PodInventoryItem> pods,
        TopologyInfo topology,
        SchedulingInfo scheduling,
        HpaInfo hpa,
        PdbInfo pdb,
        ResourceConfig resources,
        ProbeInfo probes,
        NetworkingInfo networking,
        List<CollectionWarning> warnings,
        Instant collectedAt,
        EnvironmentInfo discovery) {

    /** Historical inventories did not retain discovery coverage. */
    public InfrastructureInventory(String targetId, String runtime, ClusterInfo cluster, KeycloakWorkloadInfo keycloak,
            List<PodInventoryItem> pods, TopologyInfo topology, SchedulingInfo scheduling, HpaInfo hpa, PdbInfo pdb,
            ResourceConfig resources, ProbeInfo probes, NetworkingInfo networking, List<CollectionWarning> warnings,
            Instant collectedAt) {
        this(targetId, runtime, cluster, keycloak, pods, topology, scheduling, hpa, pdb, resources, probes,
                networking, warnings, collectedAt, null);
    }
}
