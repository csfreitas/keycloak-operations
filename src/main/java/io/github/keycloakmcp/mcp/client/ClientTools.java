package io.github.keycloakmcp.mcp.client;

import java.util.List;

import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.client.ClientDetails;
import io.github.keycloakmcp.domain.client.ClientSummary;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.ClientService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

public class ClientTools {

    private static final String TARGET_ID_HINT =
            "Identifies a previously registered Keycloak/RHBK environment. "
                    + "Use keycloak_list_targets when the target is unknown.";

    @Inject
    ClientService clientService;

    @Inject
    AuditService auditService;

    @Inject
    McpMetrics metrics;

    @Inject
    ToolAuthorization toolAuthorization;

    @Tool(
            name = "keycloak_list_clients",
            description = "List clients in a realm on a registered target (never includes secrets)")
    public List<ClientSummary> keycloakListClients(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm) {
        return invoke("keycloak_list_clients", targetId, realm, () -> clientService.listClients(targetId, realm));
    }

    @Tool(
            name = "keycloak_get_client",
            description = "Get client details by clientId on a registered target (never includes secrets)")
    public ClientDetails keycloakGetClient(
            @ToolArg(description = TARGET_ID_HINT) String targetId,
            @ToolArg(description = "Realm name") String realm,
            @ToolArg(description = "OAuth/OIDC clientId") String clientId) {
        return invoke(
                "keycloak_get_client",
                targetId,
                realm,
                () -> clientService.getClient(targetId, realm, clientId));
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
