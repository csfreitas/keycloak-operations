package io.github.keycloakmcp.domain.inventory;

/**
 * How the Keycloak/RHBK workload is deployed on the cluster.
 */
public enum DeploymentMethod {
    KEYCLOAK_OPERATOR,
    DEPLOYMENT,
    STATEFULSET,
    UNKNOWN
}
