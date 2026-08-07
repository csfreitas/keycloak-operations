package io.github.keycloakmcp.mcp.realm;

import java.util.List;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.realm.RealmDetails;
import io.github.keycloakmcp.domain.realm.RealmSummary;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.RealmService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

public class RealmTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    RealmService realmService;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(name = "keycloak_list_realms", description = "List realms for a registered Keycloak/RHBK target")
    public List<RealmSummary> keycloakListRealms(
            @ToolArg(description = TARGET_ID_HINT) String targetId) {
        return invoke("keycloak_list_realms", targetId, null, () -> realmService.listRealms(targetId));
    }

    @Tool(name = "keycloak_get_realm", description = "Get detailed realm configuration for a registered target")
    public RealmDetails keycloakGetRealm(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm) {
        return invoke("keycloak_get_realm", targetId, realm, () -> realmService.getRealm(targetId, realm));
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
