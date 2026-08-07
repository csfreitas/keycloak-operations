package io.github.keycloakmcp.domain.client;

public record ClientSummary(
        String id,
        String clientId,
        String name,
        boolean enabled,
        boolean publicClient,
        boolean serviceAccountsEnabled,
        boolean standardFlowEnabled,
        boolean directAccessGrantsEnabled) {
}
