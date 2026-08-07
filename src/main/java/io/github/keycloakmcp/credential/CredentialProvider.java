package io.github.keycloakmcp.credential;

public interface CredentialProvider {

    KeycloakCredentials getKeycloakCredentials(String credentialRef);

    InfrastructureCredentials getInfrastructureCredentials(String credentialRef);

    MetricsCredentials getMetricsCredentials(String credentialRef);
}
