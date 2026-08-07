package io.github.keycloakmcp.mcp.assessment;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.discovery.EnvironmentDiscovery;
import io.github.keycloakmcp.discovery.EnvironmentInfo;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

public class AssessmentTools {

    private static final String TOOL_NAME = "keycloak_discover_environment";
    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown. "
                    + "Infrastructure discovery uses the target binding when available; "
                    + "global discovery flags apply as fallback in 0.1.x.";

    @Inject
    EnvironmentDiscovery environmentDiscovery;

    @Inject
    TargetResolver targetResolver;

    @Inject
    TargetAuthorizationService targetAuthorization;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(
            name = TOOL_NAME,
            description = "Discover runtime environment for a registered target "
                    + "(OpenShift/Kubernetes/VM/unknown) using read-only probes")
    public EnvironmentInfo keycloakDiscoverEnvironment(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(TOOL_NAME);
            var target = targetResolver.require(targetId);
            targetAuthorization.assertAllowed(target, TargetPermission.READ);
            // 0.1.x: global discovery flags; target infrastructure binding prepared for 0.2.0
            EnvironmentInfo info = environmentDiscovery.discover();
            success = true;
            return info;
        } catch (McpException e) {
            throw new ToolCallException(e.getError().code() + ": " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(TOOL_NAME, duration, success);
            auditService.logToolInvocation(TOOL_NAME, targetId, null, duration, success);
        }
    }
}
