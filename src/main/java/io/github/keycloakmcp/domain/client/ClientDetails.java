package io.github.keycloakmcp.domain.client;

import java.util.List;

public record ClientDetails(
        String id,
        String clientId,
        String name,
        boolean enabled,
        boolean publicClient,
        boolean serviceAccountsEnabled,
        boolean standardFlowEnabled,
        boolean directAccessGrantsEnabled,
        String protocol,
        String rootUrl,
        String baseUrl,
        List<String> redirectUris,
        List<String> webOrigins,
        boolean bearerOnly,
        int confidentialPort,
        boolean fullScopeAllowed,
        String description) {
}
