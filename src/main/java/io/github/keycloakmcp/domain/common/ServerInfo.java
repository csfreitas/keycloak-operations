package io.github.keycloakmcp.domain.common;

import io.github.keycloakmcp.adapter.keycloak.KeycloakCapabilities;

public record ServerInfo(
        Product product,
        String version,
        String serverUrl,
        String authRealm,
        KeycloakCapabilities capabilities) {

    public enum Product {
        KEYCLOAK,
        RHBK,
        UNKNOWN
    }
}
