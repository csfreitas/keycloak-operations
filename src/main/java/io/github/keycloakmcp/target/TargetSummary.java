package io.github.keycloakmcp.target;

/**
 * Safe MCP DTO for listing targets — no URLs, secrets, or credential references.
 */
public record TargetSummary(
        String id,
        String displayName,
        String product,
        String environment,
        boolean enabled) {
}
