package io.github.keycloakmcp.health;

import java.util.Set;

import io.github.keycloakmcp.domain.inventory.DeploymentMethod;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;

/** Evidence prerequisites only; never copy provider warning messages into health results. */
final class InventoryHealthEvidence {
    private InventoryHealthEvidence() {}

    static boolean unavailable(InfrastructureInventory inventory, String... resources) {
        if (inventory == null) return true;
        Set<String> relevant = Set.of(resources);
        return inventory.warnings() != null && inventory.warnings().stream().anyMatch(w ->
                w == null || w.resource() == null || relevant.contains(w.resource()));
    }

    static boolean workloadUnavailable(KeycloakWorkloadInfo workload) {
        return workload == null || workload.name() == null || workload.name().isBlank()
                || workload.namespace() == null || workload.namespace().isBlank()
                || workload.deploymentMethod() == null || workload.deploymentMethod() == DeploymentMethod.UNKNOWN
                || workload.desiredReplicas() < 0 || workload.readyReplicas() < 0
                || workload.availableReplicas() < 0;
    }
}
