package io.github.keycloakmcp.domain.inventory;

/**
 * Probe configuration presence from the Keycloak container pod template.
 */
public record ProbeInfo(
        boolean readinessPresent,
        boolean livenessPresent,
        boolean startupPresent) {

    public static ProbeInfo unknown() {
        return new ProbeInfo(false, false, false);
    }
}
