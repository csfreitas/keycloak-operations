package io.github.keycloakmcp.credential;

import java.util.Map;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves credentials from {@code mcp.credentials.*}.
 * Secrets are never written to logs.
 */
@ApplicationScoped
public class ConfigCredentialProvider implements CredentialProvider {

    private static final Logger LOG = Logger.getLogger(ConfigCredentialProvider.class);

    private final McpRuntimeConfig runtimeConfig;

    @Inject
    public ConfigCredentialProvider(McpRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public KeycloakCredentials getKeycloakCredentials(String credentialRef) {
        McpRuntimeConfig.CredentialEntry entry = requireEntry(credentialRef);
        String secret = entry.clientSecret()
                .filter(s -> s != null && !s.isBlank())
                .orElseThrow(() -> McpException.authenticationFailed(
                        "Credential '" + credentialRef + "' has no client-secret configured"));
        // clientId is taken from the target keycloak block; secret is bound to the ref
        return new KeycloakCredentials(null, secret);
    }

    @Override
    public InfrastructureCredentials getInfrastructureCredentials(String credentialRef) {
        requireEntry(credentialRef);
        LOG.debugf("Infrastructure credentials requested for ref=%s (placeholder)", credentialRef);
        // Future: map token / kubeconfig from CredentialEntry extensions
        throw McpException.unsupportedCapability(
                "Infrastructure credentials are not yet supported for credential-ref '" + credentialRef + "'");
    }

    private McpRuntimeConfig.CredentialEntry requireEntry(String credentialRef) {
        if (credentialRef == null || credentialRef.isBlank()) {
            throw McpException.invalidArgument("credentialRef must not be blank");
        }
        Map<String, McpRuntimeConfig.CredentialEntry> credentials = runtimeConfig.credentials();
        if (credentials == null || !credentials.containsKey(credentialRef)) {
            throw McpException.authenticationFailed(
                    "Unknown credential-ref '" + credentialRef + "'");
        }
        return credentials.get(credentialRef);
    }
}
