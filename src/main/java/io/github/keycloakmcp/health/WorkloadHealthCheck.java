package io.github.keycloakmcp.health;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.KeycloakWorkloadInfo;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkloadHealthCheck implements HealthCheck {

    private final InventoryService inventoryService;

    @Inject
    public WorkloadHealthCheck(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public String name() {
        return "infrastructure.workload";
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
        try {
            InfrastructureInventory inventory = inventoryService.collect(target.id().value());
            KeycloakWorkloadInfo workload = inventory.keycloak();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("deploymentMethod", workload.deploymentMethod() == null ? null : workload.deploymentMethod().name());
            details.put("name", workload.name());
            details.put("namespace", workload.namespace());
            details.put("desiredReplicas", workload.desiredReplicas());
            details.put("readyReplicas", workload.readyReplicas());
            details.put("availableReplicas", workload.availableReplicas());

            HealthStatus status = HealthStatus.HEALTHY;
            String message = "Workload healthy";
            if (workload.desiredReplicas() > 0 && workload.readyReplicas() < workload.desiredReplicas()) {
                status = HealthStatus.WARNING;
                message = "Ready replicas below desired";
            }
            if (workload.desiredReplicas() > 0 && workload.readyReplicas() == 0) {
                status = HealthStatus.CRITICAL;
                message = "No ready replicas";
            }
            return HealthComponentResult.of(name(), status, message, details, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.CRITICAL,
                    e.getMessage() == null ? "Workload inventory failed" : e.getMessage(),
                    Map.of(),
                    System.currentTimeMillis() - start);
        }
    }
}
