package io.github.keycloakmcp.mcp.server;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.mcp.McpToolErrorProjector;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.ServerInfoService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

public class ServerInfoTools {

    private static final String TOOL_NAME = "keycloak_server_info";
    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    ServerInfoService serverInfoService;

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
            description = "Get Keycloak/RHBK server product, version, and capability information for a target")
    public ServerInfo keycloakServerInfo(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(TOOL_NAME);
            ServerInfo info = serverInfoService.getServerInfo(targetId);
            success = true;
            return info;
        } catch (Exception e) {
            throw errorProjector.project(e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(TOOL_NAME, duration, success);
            auditService.logToolInvocation(TOOL_NAME, targetId, null, duration, success);
        }
    }
}
