package io.github.keycloakmcp.domain.inventory;

/**
 * Sanitized Keycloak/RHBK workload summary from the cluster.
 */
public record KeycloakWorkloadInfo(
        DeploymentMethod deploymentMethod,
        String namespace,
        String name,
        int desiredReplicas,
        int readyReplicas,
        int currentReplicas,
        int availableReplicas,
        String apiVersion,
        String kind,
        String uid) {

    public KeycloakWorkloadInfo(DeploymentMethod method, String namespace, String name, int desired, int ready, int current, int available) {
        this(method, namespace, name, desired, ready, current, available, null, null, null);
    }

    public static KeycloakWorkloadInfo unknown(String namespace) {
        return new KeycloakWorkloadInfo(DeploymentMethod.UNKNOWN, namespace, null, -1, -1, -1, -1);
    }
}
