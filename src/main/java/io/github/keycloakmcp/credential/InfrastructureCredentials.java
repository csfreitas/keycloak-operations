package io.github.keycloakmcp.credential;

/**
 * Placeholder for OpenShift/Kubernetes/VM credentials (token or kubeconfig ref).
 */
public record InfrastructureCredentials(String tokenOrKubeConfigRef) {
}
