package io.github.keycloakmcp.domain.inventory;

/**
 * HorizontalPodAutoscaler configuration for a Keycloak workload.
 */
public record HpaInfo(
        boolean present,
        /** Minimum replicas; -1 if not set or HPA not found. */
        int minReplicas,
        /** Maximum replicas; -1 if not set or HPA not found. */
        int maxReplicas,
        /** Current number of desired replicas as reported by HPA; -1 if unavailable. */
        int currentReplicas) {

    public static HpaInfo absent() {
        return new HpaInfo(false, -1, -1, -1);
    }
}
