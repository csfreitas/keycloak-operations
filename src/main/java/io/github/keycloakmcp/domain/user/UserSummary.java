package io.github.keycloakmcp.domain.user;

public record UserSummary(
        String id,
        String username,
        String firstName,
        String lastName,
        String email,
        boolean enabled) {
}
