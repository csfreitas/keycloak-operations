package io.github.keycloakmcp.mcp.group;

import java.util.List;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.group.GroupDetails;
import io.github.keycloakmcp.domain.group.GroupSummary;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.GroupService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

public class GroupTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    GroupService groupService;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(name = "keycloak_list_groups", description = "List groups in a realm on a registered target")
    public List<GroupSummary> keycloakListGroups(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm,
            @ToolArg(description = "Pagination offset", defaultValue = "0") Integer first,
            @ToolArg(description = "Maximum results", defaultValue = "100") Integer max) {
        return invoke(
                "keycloak_list_groups",
                targetId,
                realm,
                () -> groupService.listGroups(targetId, realm, first, max));
    }

    @Tool(name = "keycloak_get_group", description = "Get group details by group id on a registered target")
    public GroupDetails keycloakGetGroup(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm,
            @ToolArg(description = "Group id") String groupId) {
        return invoke("keycloak_get_group", targetId, realm, () -> groupService.getGroup(targetId, realm, groupId));
    }

    private <T> T invoke(String toolName, String targetId, String realm, java.util.concurrent.Callable<T> action) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            toolAuthorization.assertReadOnlyOperation(toolName);
            T result = action.call();
            success = true;
            return result;
        } catch (McpException e) {
            throw new ToolCallException(e.getError().code() + ": " + e.getMessage());
        } catch (ToolCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolCallException("INTERNAL_ERROR: " + e.getMessage());
        } finally {
            long duration = System.currentTimeMillis() - start;
            metrics.recordToolInvocation(toolName, duration, success);
            auditService.logToolInvocation(toolName, targetId, realm, duration, success);
        }
    }
}
