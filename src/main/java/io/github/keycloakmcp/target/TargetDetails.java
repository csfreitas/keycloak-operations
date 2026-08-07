package io.github.keycloakmcp.target;

import java.util.Map;

/**
 * Safe MCP DTO for {@code get_target}.
 * Intentionally omits Keycloak URLs and credential references (SSRF / secret safety for LLMs).
 */
public record TargetDetails(
        String id,
        String displayName,
        String product,
        String environment,
        boolean enabled,
        boolean keycloakConfigured,
        boolean infrastructureConfigured,
        String infrastructureType,
        Map<String, String> tags) {
}
