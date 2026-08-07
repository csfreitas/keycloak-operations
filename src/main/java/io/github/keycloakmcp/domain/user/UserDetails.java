package io.github.keycloakmcp.domain.user;

import java.util.List;

public record UserDetails(
        String id,
        String username,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        boolean emailVerified,
        String federationLink,
        List<String> requiredActions,
        Long createdTimestamp) {
}
