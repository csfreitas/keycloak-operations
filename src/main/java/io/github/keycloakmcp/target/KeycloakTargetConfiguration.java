package io.github.keycloakmcp.target;

/**
 * Keycloak Admin API connection settings for a target.
 * Holds a credential reference only — never secrets.
 */
public record KeycloakTargetConfiguration(
        String url,
        String authRealm,
        String clientId,
        String credentialRef) {
}
