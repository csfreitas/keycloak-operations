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
        int availableReplicas) {

    public static KeycloakWorkloadInfo unknown(String namespace) {
        return new KeycloakWorkloadInfo(DeploymentMethod.UNKNOWN, namespace, null, -1, -1, -1, -1);
    }
}
