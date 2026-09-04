package io.github.keycloakmcp.domain.change;

/**
 * Typed semantic request for changing a client's authentication and OAuth/OIDC flow settings.
 * Null fields are omitted/unchanged. PKCE accepts {@code S256} or {@code NONE}.
 */
public record ClientSecurityChangeRequest(
        String targetId,
        String realm,
        String clientId,
        String pkceCodeChallengeMethod,
        Boolean standardFlowEnabled,
        Boolean implicitFlowEnabled,
        Boolean directAccessGrantsEnabled,
        Boolean serviceAccountsEnabled,
        Boolean publicClient,
        String actor,
        String idempotencyKey) {
}
