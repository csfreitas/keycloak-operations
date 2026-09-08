package io.github.keycloakmcp.domain.change;

/** Typed request for enabling or disabling an existing client. */
public record ClientEnabledChangeRequest(
        String targetId,
        String realm,
        String clientId,
        boolean enabled,
        String actor,
        String idempotencyKey) {
}
