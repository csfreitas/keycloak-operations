package io.github.keycloakmcp.health;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.InfrastructureCoverage;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InfrastructureApiHealthCheck implements HealthCheck {

    private final InventoryService inventoryService;
    private final InfrastructureClientFactory clientFactory;

    @Inject
    public InfrastructureApiHealthCheck(
            InventoryService inventoryService, InfrastructureClientFactory clientFactory) {
        this.inventoryService = inventoryService;
        this.clientFactory = clientFactory;
    }

    @Override
    public String name() {
        return "infrastructure.api";
    }

    @Override
    public HealthComponentResult check(Target target) {
        long start = System.currentTimeMillis();
        if (!target.hasInfrastructure()) {
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.UNKNOWN,
                    "No infrastructure configuration",
                    Map.of("configured", false, "reasonCode", "NOT_CONFIGURED", "collectionComplete", false),
                    System.currentTimeMillis() - start);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", target.infrastructureTypeOrNone().name());
        try {
            boolean clientPresent = clientFactory.resolve(target).isPresent();
            details.put("clientResolved", clientPresent);
            if (!clientPresent) {
                return unknown(start, details, "CLIENT_UNAVAILABLE", "Infrastructure client unavailable; health is inconclusive");
            }
            InfrastructureInventory inventory = inventoryService.collect(target.id().value());
            if (inventory == null) {
                return unknown(start, details, "EVIDENCE_UNAVAILABLE", "Infrastructure evidence unavailable; health is inconclusive");
            }
            if (!target.id().value().equals(inventory.targetId())) {
                return unknown(start, details, "EVIDENCE_SCOPE_MISMATCH", "Infrastructure evidence scope could not be verified");
            }
            if (inventory.runtime() != null) details.put("runtime", inventory.runtime());
            int warningCount = inventory.warnings() == null ? 0 : inventory.warnings().size();
            details.put("warningCount", warningCount);
            boolean complete = InfrastructureCoverage.isComplete(inventory);
            details.put("collectionComplete", complete);
            if (!complete) {
                return unknown(start, details, "EVIDENCE_INCOMPLETE", "Infrastructure collection is incomplete; health is inconclusive");
            }
            return HealthComponentResult.of(name(), HealthStatus.HEALTHY, "Infrastructure API reachable", details,
                    System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            return unknown(start, details, "CHECK_FAILED", "Infrastructure collection failed; health is inconclusive");
        }
    }

    private HealthComponentResult unknown(long start, Map<String, Object> details, String reason, String message) {
        details.put("reasonCode", reason);
        details.put("collectionComplete", false);
        return HealthComponentResult.of(name(), HealthStatus.UNKNOWN, message, details, System.currentTimeMillis() - start);
    }
}
