package io.github.keycloakmcp.domain.inventory;

/**
 * PodDisruptionBudget configuration for a Keycloak workload.
 */
public record PdbInfo(
        boolean present,
        /** minAvailable value as string (may be a number or percentage); null if not set. */
        String minAvailable,
        /** maxUnavailable value as string; null if not set. */
        String maxUnavailable) {

    public static PdbInfo absent() {
        return new PdbInfo(false, null, null);
    }
}
