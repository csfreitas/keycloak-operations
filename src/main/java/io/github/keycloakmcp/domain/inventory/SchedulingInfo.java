package io.github.keycloakmcp.domain.inventory;

/**
 * Pod scheduling constraints for the Keycloak workload.
 */
public record SchedulingInfo(
        /** Whether a topology spread constraint with topologyKey=zone is configured. */
        boolean zoneSpreadPresent,
        /** Whether a topology spread constraint with topologyKey=hostname is configured. */
        boolean hostnameSpreadPresent) {
}
