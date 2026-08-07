package io.github.keycloakmcp.domain.role;

public record RoleDetails(
        String id,
        String name,
        String description,
        boolean composite,
        boolean clientRole,
        String containerId) {
}
