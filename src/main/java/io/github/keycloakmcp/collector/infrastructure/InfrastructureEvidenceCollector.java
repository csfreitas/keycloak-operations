package io.github.keycloakmcp.collector.infrastructure;

import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Canonical infrastructure evidence collector — delegates to {@link InventoryService}
 * (target-bound clients). Does not construct Fabric8 clients.
 */
@ApplicationScoped
public class InfrastructureEvidenceCollector implements EvidenceCollector {

    private final InventoryService inventoryService;

    @Inject
    public InfrastructureEvidenceCollector(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public String source() {
        return "infrastructure";
    }

    @Override
    public List<Evidence> collect(Target target) {
        if (target == null || !target.hasInfrastructure()) {
            return List.of();
        }
        InfrastructureInventory inventory = inventoryService.collect(target.id().value());
        return inventoryService.toEvidence(inventory);
    }
}
