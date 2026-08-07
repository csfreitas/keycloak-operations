package io.github.keycloakmcp.domain.role;

public record RoleSummary(
        String id,
        String name,
        String description,
        boolean composite,
        boolean clientRole,
        String containerId) {
}
