package io.github.keycloakmcp.adapter.keycloak;

import java.util.List;
import java.util.Objects;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpError;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class StableAdminApiAdapter {

    private final KeycloakClientFactory clientFactory;
    private final McpMetrics metrics;

    @Inject
    public StableAdminApiAdapter(KeycloakClientFactory clientFactory, McpMetrics metrics) {
        this.clientFactory = clientFactory;
        this.metrics = metrics;
    }

    public ServerInfoRepresentation getServerInfo(Target target) {
        return execute(target, "getServerInfo", null, () -> client(target).serverInfo().getInfo());
    }

    public List<RealmRepresentation> listRealms(Target target) {
        return execute(target, "listRealms", null, () -> client(target).realms().findAll());
    }

    public RealmRepresentation getRealm(Target target, String realm) {
        requireNonBlank(realm, "realm");
        return execute(target, "getRealm", realm, () -> {
            try {
                return realmResource(target, realm).toRepresentation();
            } catch (NotFoundException e) {
                throw McpException.realmNotFound(realm);
            }
        });
    }

    public List<ClientRepresentation> listClients(Target target, String realm, boolean brief) {
        requireNonBlank(realm, "realm");
        return execute(target, "listClients", realm, () -> {
            ensureRealmExists(target, realm);
            return realmResource(target, realm).clients().findAll(brief);
        });
    }

    public ClientRepresentation getClient(Target target, String realm, String id) {
        requireNonBlank(realm, "realm");
        requireNonBlank(id, "id");
        return execute(target, "getClient", realm, () -> {
            try {
                return realmResource(target, realm).clients().get(id).toRepresentation();
            } catch (NotFoundException e) {
                throw McpException.clientNotFound(id);
            }
        });
    }

    public ClientRepresentation findClientByClientId(Target target, String realm, String clientId) {
        requireNonBlank(realm, "realm");
        requireNonBlank(clientId, "clientId");
        return execute(target, "findClientByClientId", realm, () -> {
            ensureRealmExists(target, realm);
            List<ClientRepresentation> matches = realmResource(target, realm).clients().findByClientId(clientId);
            if (matches == null || matches.isEmpty()) {
                throw McpException.clientNotFound(clientId);
            }
            ClientRepresentation brief = matches.get(0);
            if (brief.getId() == null) {
                return brief;
            }
            try {
                return realmResource(target, realm).clients().get(brief.getId()).toRepresentation();
            } catch (NotFoundException e) {
                throw McpException.clientNotFound(clientId);
            }
        });
    }

    /**
     * Controlled client update used by Change Management apply.
     * Callers must only pass representations prepared from allowlisted semantic ops.
     */
    public void updateClient(Target target, String realm, ClientRepresentation representation) {
        requireNonBlank(realm, "realm");
        Objects.requireNonNull(representation, "representation");
        requireNonBlank(representation.getId(), "representation.id");
        execute(target, "updateClient", realm, () -> {
            try {
                realmResource(target, realm).clients().get(representation.getId()).update(representation);
                return Boolean.TRUE;
            } catch (NotFoundException e) {
                throw McpException.clientNotFound(representation.getClientId() != null
                        ? representation.getClientId()
                        : representation.getId());
            }
        });
    }

    public List<UserRepresentation> searchUsers(Target target, String realm, String search, Integer first, Integer max) {
        requireNonBlank(realm, "realm");
        return execute(target, "searchUsers", realm, () -> {
            ensureRealmExists(target, realm);
            int firstResult = first == null ? 0 : Math.max(0, first);
            int maxResults = max == null ? 50 : Math.max(1, max);
            return realmResource(target, realm).users().search(search, firstResult, maxResults);
        });
    }

    public UserRepresentation getUser(Target target, String realm, String id) {
        requireNonBlank(realm, "realm");
        requireNonBlank(id, "id");
        return execute(target, "getUser", realm, () -> {
            try {
                return realmResource(target, realm).users().get(id).toRepresentation();
            } catch (NotFoundException e) {
                throw McpException.userNotFound(id);
            }
        });
    }

    public List<GroupRepresentation> listGroups(Target target, String realm, boolean brief, Integer first, Integer max) {
        requireNonBlank(realm, "realm");
        return execute(target, "listGroups", realm, () -> {
            ensureRealmExists(target, realm);
            int firstResult = first == null ? 0 : Math.max(0, first);
            int maxResults = max == null ? 100 : Math.max(1, max);
            return realmResource(target, realm).groups().groups(null, firstResult, maxResults, brief);
        });
    }

    public GroupRepresentation getGroup(Target target, String realm, String id) {
        requireNonBlank(realm, "realm");
        requireNonBlank(id, "id");
        return execute(target, "getGroup", realm, () -> {
            try {
                return realmResource(target, realm).groups().group(id).toRepresentation();
            } catch (NotFoundException e) {
                throw McpException.groupNotFound(id);
            }
        });
    }

    public List<RoleRepresentation> listRealmRoles(Target target, String realm, boolean brief, Integer first, Integer max) {
        requireNonBlank(realm, "realm");
        return execute(target, "listRealmRoles", realm, () -> {
            ensureRealmExists(target, realm);
            int firstResult = first == null ? 0 : Math.max(0, first);
            int maxResults = max == null ? 100 : Math.max(1, max);
            return realmResource(target, realm).roles().list(firstResult, maxResults, brief);
        });
    }

    public RoleRepresentation getRealmRole(Target target, String realm, String name) {
        requireNonBlank(realm, "realm");
        requireNonBlank(name, "name");
        return execute(target, "getRealmRole", realm, () -> {
            try {
                return realmResource(target, realm).roles().get(name).toRepresentation();
            } catch (NotFoundException e) {
                throw McpException.roleNotFound(name);
            }
        });
    }

    private Keycloak client(Target target) {
        return clientFactory.getClient(target);
    }

    private RealmResource realmResource(Target target, String realm) {
        return client(target).realm(realm);
    }

    private void ensureRealmExists(Target target, String realm) {
        try {
            realmResource(target, realm).toRepresentation();
        } catch (NotFoundException e) {
            throw McpException.realmNotFound(realm);
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw McpException.invalidArgument(name + " must not be blank");
        }
    }

    private <T> T execute(Target target, String operation, String realm, SupplierWithException<T> supplier) {
        Objects.requireNonNull(target, "target");
        metrics.recordKeycloakAdminRequest(target.id().value(), operation);
        try {
            return supplier.get();
        } catch (McpException e) {
            throw e;
        } catch (NotFoundException e) {
            throw mapNotFound(operation, realm, e);
        } catch (NotAuthorizedException e) {
            throw McpException.authenticationFailed("Authentication with Keycloak Admin API failed", e);
        } catch (WebApplicationException e) {
            int status = e.getResponse() == null ? -1 : e.getResponse().getStatus();
            if (status == 401) {
                throw McpException.authenticationFailed("Authentication with Keycloak Admin API failed", e);
            }
            if (status == 403) {
                throw McpException.authorizationFailed("Not authorized to perform " + operation
                        + (realm == null ? "" : " in realm " + realm));
            }
            if (status == 404) {
                throw mapNotFound(operation, realm, e);
            }
            throw McpException.keycloakUnavailable(
                    "Keycloak Admin API request failed for " + operation + " (HTTP " + status + ")", e);
        } catch (ProcessingException e) {
            throw McpException.keycloakUnavailable("Keycloak Admin API is unavailable for " + operation, e);
        } catch (RuntimeException e) {
            throw McpException.keycloakUnavailable("Keycloak Admin API error during " + operation, e);
        }
    }

    private static McpException mapNotFound(String operation, String realm, Exception cause) {
        String message = "Resource not found for operation " + operation
                + (realm == null ? "" : " in realm " + realm);
        ErrorCode code = switch (operation) {
            case "getRealm", "listClients", "searchUsers", "listGroups", "listRealmRoles" -> ErrorCode.REALM_NOT_FOUND;
            case "getClient", "findClientByClientId" -> ErrorCode.CLIENT_NOT_FOUND;
            case "getUser" -> ErrorCode.USER_NOT_FOUND;
            case "getGroup" -> ErrorCode.GROUP_NOT_FOUND;
            case "getRealmRole" -> ErrorCode.ROLE_NOT_FOUND;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        return new McpException(McpError.of(code, message), cause);
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
