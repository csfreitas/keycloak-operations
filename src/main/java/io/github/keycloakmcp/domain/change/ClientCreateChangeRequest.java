package io.github.keycloakmcp.domain.change;

/**
 * Typed, secret-free request for creating an OpenID Connect client.
 * Optional Boolean values use conservative defaults in the lifecycle service.
 */
public record ClientCreateChangeRequest(
        String targetId,
        String realm,
        String clientId,
        String name,
        String description,
        Boolean enabled,
        Boolean publicClient,
        Boolean standardFlowEnabled,
        Boolean directAccessGrantsEnabled,
        Boolean serviceAccountsEnabled,
        String actor,
        String idempotencyKey) {
}
