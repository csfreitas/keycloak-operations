package io.github.keycloakmcp.target;

/**
 * Keycloak Admin API connection settings for a target.
 * Holds a credential reference only — never secrets.
 * {@code managementUrl} is optional (management/health endpoint base URL).
 */
public record KeycloakTargetConfiguration(
        String url,
        String authRealm,
        String clientId,
        String credentialRef,
        String managementUrl) {

    public KeycloakTargetConfiguration(String url, String authRealm, String clientId, String credentialRef) {
        this(url, authRealm, clientId, credentialRef, null);
    }
}
