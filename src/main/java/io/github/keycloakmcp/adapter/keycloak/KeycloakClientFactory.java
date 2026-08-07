package io.github.keycloakmcp.adapter.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.KeycloakCredentials;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Creates and caches per-target Keycloak Admin clients.
 * <p>
 * <b>Caching decision:</b> one {@link Keycloak} instance is retained per target id.
 * The cache entry is keyed by target id and fingerprinted by
 * {@code (url, authRealm, clientId, secretHash)}. When configuration or credentials
 * change (fingerprint mismatch), the previous client is closed and replaced.
 * This avoids reconnecting on every tool call while still reacting to rotated secrets.
 * Secrets are never logged.
 */
@ApplicationScoped
public class KeycloakClientFactory {

    private static final Logger LOG = Logger.getLogger(KeycloakClientFactory.class);

    private final CredentialProvider credentialProvider;
    private final ConcurrentHashMap<String, CachedClient> cache = new ConcurrentHashMap<>();

    @Inject
    public KeycloakClientFactory(CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    public Keycloak getClient(Target target) {
        Objects.requireNonNull(target, "target");
        KeycloakTargetConfiguration kc = target.keycloak();
        if (kc == null) {
            throw McpException.invalidArgument("Target '" + target.id().value() + "' has no keycloak configuration");
        }
        KeycloakCredentials credentials = credentialProvider.getKeycloakCredentials(kc.credentialRef());
        String clientId = kc.clientId();
        String clientSecret = credentials.clientSecret();
        if (clientSecret == null || clientSecret.isBlank()) {
            throw McpException.authenticationFailed(
                    "Missing client secret for credential-ref of target '" + target.id().value() + "'");
        }

        String fingerprint = fingerprint(kc.url(), kc.authRealm(), clientId, clientSecret);
        String cacheKey = target.id().value();

        CachedClient existing = cache.get(cacheKey);
        if (existing != null && existing.fingerprint().equals(fingerprint)) {
            return existing.client();
        }

        synchronized (cache) {
            existing = cache.get(cacheKey);
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing.client();
            }
            if (existing != null) {
                closeQuietly(existing.client());
                cache.remove(cacheKey);
            }
            LOG.debugf(
                    "Creating Keycloak admin client for target=%s realm=%s",
                    cacheKey,
                    kc.authRealm());
            Keycloak client = KeycloakBuilder.builder()
                    .serverUrl(kc.url())
                    .realm(kc.authRealm())
                    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .build();
            cache.put(cacheKey, new CachedClient(client, fingerprint));
            return client;
        }
    }

    @PreDestroy
    void shutdown() {
        Iterator<Map.Entry<String, CachedClient>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CachedClient> entry = it.next();
            closeQuietly(entry.getValue().client());
            it.remove();
        }
    }

    private static void closeQuietly(Keycloak client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (RuntimeException e) {
            LOG.debugf(e, "Error closing Keycloak admin client");
        }
    }

    private static String fingerprint(String url, String realm, String clientId, String secret) {
        return sha256(url + "|" + realm + "|" + clientId + "|" + secret);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record CachedClient(Keycloak client, String fingerprint) {
    }
}
