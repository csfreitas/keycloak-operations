package io.github.keycloakmcp.credential;

/**
 * Resolved infrastructure credentials for Kubernetes/OpenShift API access.
 * <p>
 * Secrets (token, caCertData) are never logged.
 * The {@link AuthMode} controls which credentials are used by {@code InfrastructureClientFactory}.
 */
public record InfrastructureCredentials(
        String token,
        String apiServerUrl,
        String caCertData,
        boolean trustInsecure,
        String kubeconfigPath,
        AuthMode authMode) {

    public enum AuthMode {
        /** Pod service account mounted at /var/run/secrets/kubernetes.io/serviceaccount. */
        IN_CLUSTER,
        /** Bearer token + API server URL. */
        TOKEN,
        /** Path to a kubeconfig file. */
        KUBECONFIG
    }

    /** Convenience factory for in-cluster auth (no external credentials needed). */
    public static InfrastructureCredentials inCluster() {
        return new InfrastructureCredentials(null, null, null, false, null, AuthMode.IN_CLUSTER);
    }

    /** Convenience factory for token-based auth. */
    public static InfrastructureCredentials token(
            String token, String apiServerUrl, String caCertData, boolean trustInsecure) {
        return new InfrastructureCredentials(token, apiServerUrl, caCertData, trustInsecure, null, AuthMode.TOKEN);
    }

    /** Convenience factory for kubeconfig-file auth. */
    public static InfrastructureCredentials kubeconfig(String kubeconfigPath) {
        return new InfrastructureCredentials(null, null, null, false, kubeconfigPath, AuthMode.KUBECONFIG);
    }
}
