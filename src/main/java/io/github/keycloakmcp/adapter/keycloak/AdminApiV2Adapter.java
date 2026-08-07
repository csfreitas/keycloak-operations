package io.github.keycloakmcp.adapter.keycloak;

import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Optional future path for Keycloak Admin API v2.
 * <p>
 * Intentionally unused as the primary integration path in 0.1.0. The stable Admin API
 * ({@link StableAdminApiAdapter}) remains the supported adapter for all MCP tools.
 */
@ApplicationScoped
public class AdminApiV2Adapter {

    private static final String MESSAGE =
            "Admin API v2 is not used as the primary integration path in keycloak-operations-mcp 0.1.0. "
                    + "Use the stable Admin API adapter instead.";

    public Object getServerInfo() {
        throw unsupported();
    }

    public Object listRealms() {
        throw unsupported();
    }

    public Object getRealm(String realm) {
        throw unsupported();
    }

    public Object listClients(String realm) {
        throw unsupported();
    }

    public Object getClient(String realm, String id) {
        throw unsupported();
    }

    public Object searchUsers(String realm, String search) {
        throw unsupported();
    }

    public Object getUser(String realm, String id) {
        throw unsupported();
    }

    public Object listGroups(String realm) {
        throw unsupported();
    }

    public Object getGroup(String realm, String id) {
        throw unsupported();
    }

    public Object listRealmRoles(String realm) {
        throw unsupported();
    }

    public Object getRealmRole(String realm, String name) {
        throw unsupported();
    }

    private static McpException unsupported() {
        return McpException.unsupportedCapability(MESSAGE);
    }
}
