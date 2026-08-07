package io.github.keycloakmcp.domain.platform;

/**
 * A detected difference between two environment snapshots.
 */
public record EnvironmentChange(
        String path,
        String changeType,
        Object before,
        Object after) {
}
