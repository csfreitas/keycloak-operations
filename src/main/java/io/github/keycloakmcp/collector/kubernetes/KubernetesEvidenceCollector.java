package io.github.keycloakmcp.collector.kubernetes;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Collects Kubernetes inventory evidence for targets configured as KUBERNETES.
 * Uses {@link InventoryService} (target-bound clients) — never a global Fabric8 client.
 */
@ApplicationScoped
public class KubernetesEvidenceCollector implements EvidenceCollector {

    private final InventoryService inventoryService;

    @Inject
    public KubernetesEvidenceCollector(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public String source() {
        return "kubernetes";
    }

    @Override
    public List<Evidence> collect(Target target) {
        if (target == null || target.infrastructureTypeOrNone() != InfrastructureType.KUBERNETES) {
            return List.of();
        }
        try {
            InfrastructureInventory inventory = inventoryService.collect(target.id().value());
            return inventoryService.toEvidence(inventory);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
