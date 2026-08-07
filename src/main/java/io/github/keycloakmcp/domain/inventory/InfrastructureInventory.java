package io.github.keycloakmcp.domain.inventory;

import java.time.Instant;
import java.util.List;

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
        NetworkingInfo networking,
        List<CollectionWarning> warnings,
        Instant collectedAt) {
}
