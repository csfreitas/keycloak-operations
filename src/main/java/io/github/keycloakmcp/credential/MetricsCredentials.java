package io.github.keycloakmcp.credential;

/**
 * Credentials for Prometheus / OpenShift Monitoring queries.
 * Secrets are never logged.
 */
public record MetricsCredentials(
        String bearerToken,
        String username,
        String password,
        String caCertData,
        boolean trustInsecure) {

    public static MetricsCredentials none() {
        return new MetricsCredentials(null, null, null, null, false);
    }

    public static MetricsCredentials bearer(String token, String caCertData, boolean trustInsecure) {
        return new MetricsCredentials(token, null, null, caCertData, trustInsecure);
    }

    public boolean hasBearer() {
        return bearerToken != null && !bearerToken.isBlank();
    }

    public boolean hasBasic() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
