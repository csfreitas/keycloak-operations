package io.github.keycloakmcp.mcp.user;

import java.util.List;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.user.UserDetails;
import io.github.keycloakmcp.domain.user.UserSummary;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.UserService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

public class UserTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    UserService userService;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(name = "keycloak_search_users", description = "Search users in a realm on a registered target")
    public List<UserSummary> keycloakSearchUsers(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm,
            @ToolArg(description = "Search string (username/email/name)") String search,
            @ToolArg(description = "Pagination offset", defaultValue = "0") Integer first,
            @ToolArg(description = "Maximum results", defaultValue = "50") Integer max) {
        return invoke(
                "keycloak_search_users",
                targetId,
                realm,
                () -> userService.searchUsers(targetId, realm, search, first, max));
    }

    @Tool(name = "keycloak_get_user", description = "Get user details by user id on a registered target")
    public UserDetails keycloakGetUser(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm,
            @ToolArg(description = "User id") String userId) {
        return invoke("keycloak_get_user", targetId, realm, () -> userService.getUser(targetId, realm, userId));
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
