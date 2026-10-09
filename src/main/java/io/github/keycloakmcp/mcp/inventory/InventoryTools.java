package io.github.keycloakmcp.mcp.inventory;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.mcp.McpToolErrorProjector;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ReadMetadataProjection;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

public class InventoryTools {

    private static final String TOOL_NAME = "keycloak_get_inventory";
    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown. "
                    + "Never pass cluster URLs, tokens, or kubeconfig paths.";

    @Inject
    InventoryService inventoryService;

    @Inject
    ReadMetadataProjection readProjection;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Inject
    McpToolErrorProjector errorProjector;

    @Tool(
            name = TOOL_NAME,
            description = "Returns a sanitized infrastructure and Keycloak workload "
                    + "inventory for a previously registered target. "
                    + "This tool is read-only. "
                    + "It never accepts arbitrary cluster URLs or credentials.")
    public InfrastructureInventory keycloakGetInventory(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(TOOL_NAME);
            InfrastructureInventory inventory = inventoryService.collect(targetId);
            InfrastructureInventory projected = readProjection.inventory(inventory);
            success = true;
            return projected;
        } catch (Exception e) {
            throw errorProjector.project(e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(TOOL_NAME, duration, success);
            auditService.logToolInvocation(TOOL_NAME, targetId, null, duration, success);
        }
    }
}
