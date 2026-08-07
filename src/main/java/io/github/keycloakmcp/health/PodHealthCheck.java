package io.github.keycloakmcp.health;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.config.HealthConfig;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.PodInventoryItem;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PodHealthCheck implements HealthCheck {

    private final InventoryService inventoryService;
    private final HealthConfig healthConfig;

    @Inject
    public PodHealthCheck(InventoryService inventoryService, HealthConfig healthConfig) {
        this.inventoryService = inventoryService;
        this.healthConfig = healthConfig;
    }

    @Override
    public String name() {
        return "infrastructure.pods";
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
        int restartThreshold = Math.max(0, healthConfig.pods().restartWarningThreshold());
        try {
            InfrastructureInventory inventory = inventoryService.collect(target.id().value());
            List<PodInventoryItem> pods = inventory.pods() == null ? List.of() : inventory.pods();
            int desired = inventory.keycloak() == null ? -1 : inventory.keycloak().desiredReplicas();
            int ready = 0;
            int highRestarts = 0;
            int oom = 0;
            List<String> problemPods = new ArrayList<>();

            for (PodInventoryItem pod : pods) {
                if (pod.ready()) {
                    ready++;
                }
                if (pod.restartCount() >= restartThreshold && restartThreshold > 0) {
                    highRestarts++;
                    problemPods.add(pod.name() + ":restarts=" + pod.restartCount());
                }
                if (pod.oomKilled()) {
                    oom++;
                    problemPods.add(pod.name() + ":oom");
                }
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("podCount", pods.size());
            details.put("readyPods", ready);
            details.put("desiredReplicas", desired);
            details.put("restartWarningThreshold", restartThreshold);
            details.put("highRestartPods", highRestarts);
            details.put("oomKilledPods", oom);
            if (!problemPods.isEmpty()) {
                details.put("problems", List.copyOf(problemPods));
            }

            HealthStatus status = HealthStatus.HEALTHY;
            String message = "Pods healthy";
            if (desired > 0 && ready < desired) {
                status = HealthStatus.WARNING;
                message = "Ready pods below desired replicas";
            }
            if (highRestarts > 0) {
                status = worse(status, HealthStatus.WARNING);
                message = "Pods exceeding restart threshold";
            }
            if (oom > 0 || (desired > 0 && ready == 0 && !pods.isEmpty())) {
                status = HealthStatus.CRITICAL;
                message = oom > 0 ? "OOMKilled pods detected" : "No ready pods";
            }
            if (pods.isEmpty() && desired > 0) {
                status = HealthStatus.CRITICAL;
                message = "No pods found for Keycloak workload";
            }

            return HealthComponentResult.of(name(), status, message, details, System.currentTimeMillis() - start);
        } catch (RuntimeException e) {
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.CRITICAL,
                    e.getMessage() == null ? "Pod inventory failed" : e.getMessage(),
                    Map.of(),
                    System.currentTimeMillis() - start);
        }
    }

    private static HealthStatus worse(HealthStatus a, HealthStatus b) {
        if (a == HealthStatus.CRITICAL || b == HealthStatus.CRITICAL) {
            return HealthStatus.CRITICAL;
        }
        if (a == HealthStatus.WARNING || b == HealthStatus.WARNING) {
            return HealthStatus.WARNING;
        }
        return a;
    }
}
