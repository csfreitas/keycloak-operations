package io.github.keycloakmcp.credential;

/**
 * Keycloak Admin API client credentials resolved from a credential-ref.
 * Never log or serialize this into MCP tool responses.
 */
public record KeycloakCredentials(String clientId, String clientSecret) {
}
