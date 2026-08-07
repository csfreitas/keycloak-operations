package io.github.keycloakmcp.target;

import io.github.keycloakmcp.adapter.keycloak.KeycloakCapabilities;

/**
 * Capabilities discovered for a specific target.
 */
public record TargetCapabilities(String targetId, KeycloakCapabilities capabilities) {
}
