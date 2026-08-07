package io.github.keycloakmcp.health;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.keycloakmcp.adapter.infrastructure.InfrastructureClientFactory;
import io.github.keycloakmcp.domain.inventory.CollectionWarning;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
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
                    Map.of("configured", false),
                    System.currentTimeMillis() - start);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", target.infrastructureTypeOrNone().name());
        boolean clientPresent = clientFactory.resolve(target).isPresent();
        details.put("clientResolved", clientPresent);
        if (!clientPresent) {
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.CRITICAL,
                    "Infrastructure client unavailable",
                    details,
                    System.currentTimeMillis() - start);
        }

        try {
            InfrastructureInventory inventory = inventoryService.collect(target.id().value());
            details.put("runtime", inventory.runtime());
            int warningCount = inventory.warnings() == null ? 0 : inventory.warnings().size();
            details.put("warningCount", warningCount);
            boolean notConfigured = inventory.warnings() != null
                    && inventory.warnings().stream()
                            .anyMatch(w -> w.code() == CollectionWarning.WarningCode.NOT_CONFIGURED);
            if (notConfigured) {
                return HealthComponentResult.of(
                        name(),
                        HealthStatus.UNKNOWN,
                        "Infrastructure not fully configured",
                        details,
                        System.currentTimeMillis() - start);
            }
            HealthStatus status = warningCount > 0 ? HealthStatus.WARNING : HealthStatus.HEALTHY;
            String message = warningCount > 0
                    ? "Infrastructure API reachable with collection warnings"
                    : "Infrastructure API reachable";
            return HealthComponentResult.of(name(), status, message, details, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            details.put("error", e.getMessage());
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.CRITICAL,
                    e.getMessage() == null ? "Infrastructure API unreachable" : e.getMessage(),
                    details,
                    System.currentTimeMillis() - start);
        }
    }
}
