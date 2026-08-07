package io.github.keycloakmcp.adapter.keycloak;

public record KeycloakCapabilities(
        String version,
        boolean organizations,
        boolean fineGrainedAdminPermissionsV2,
        boolean adminApiV2,
        boolean workflows,
        boolean scim) {
}
