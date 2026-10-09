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

import io.github.keycloakmcp.collection.CollectionBudget;
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
    // Scoped reads never mutate the transport used by ordinary operations/controlled writes.
    private final ConcurrentHashMap<String, CachedClient> collectionCache = new ConcurrentHashMap<>();

    @Inject
    public KeycloakClientFactory(CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    public Keycloak getClient(Target target) {
        Objects.requireNonNull(target, "target");
        boolean collection = CollectionBudget.current() != null;
        checkpoint(target, collection);
        if (collection) CollectionDiagnosticPolicy.check();
        ConcurrentHashMap<String, CachedClient> selectedCache = collection ? collectionCache : cache;
        KeycloakTargetConfiguration kc = target.keycloak();
        if (kc == null) {
            throw McpException.invalidArgument("Target '" + target.id().value() + "' has no keycloak configuration");
        }
        KeycloakCredentials credentials = credentialProvider.getKeycloakCredentials(kc.credentialRef());
        checkpoint(target, collection);
        String clientId = kc.clientId();
        String clientSecret = credentials == null ? null : credentials.clientSecret();
        if (clientSecret == null || clientSecret.isBlank()) {
            throw McpException.authenticationFailed(
                    "Missing client secret for credential-ref of target '" + target.id().value() + "'");
        }

        String fingerprint = fingerprint(kc.url(), kc.authRealm(), clientId, clientSecret);
        String cacheKey = target.id().value();

        checkpoint(target, collection);
        CachedClient existing = selectedCache.get(cacheKey);
        if (existing != null && existing.fingerprint().equals(fingerprint)) {
            return existing.client();
        }

        synchronized (selectedCache) {
            checkpoint(target, collection);
            existing = selectedCache.get(cacheKey);
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing.client();
            }
            if (existing != null) {
                closeQuietly(existing.client());
                selectedCache.remove(cacheKey);
            }
            if (collection) LOG.debugf("Creating scoped Keycloak collection client for target=%s", cacheKey);
            else LOG.debugf("Creating Keycloak admin client for target=%s realm=%s", cacheKey, kc.authRealm());
            KeycloakBuilder builder = KeycloakBuilder.builder()
                    .serverUrl(kc.url())
                    .realm(kc.authRealm())
                    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                    .clientId(clientId)
                    .clientSecret(clientSecret);
            if (collection) builder.resteasyClient(CollectionAdminTransport.create(cacheKey));
            Keycloak client = builder.build();
            try { checkpoint(target, collection); }
            catch (RuntimeException aborted) { closeQuietly(client); throw aborted; }
            selectedCache.put(cacheKey, new CachedClient(client, fingerprint));
            return client;
        }
    }

    @PreDestroy
    void shutdown() {
        closeCache(cache);
        closeCache(collectionCache);
    }

    private static void checkpoint(Target target, boolean collection) {
        if (collection) CollectionBudget.checkpoint(target.id().value());
    }

    private static void closeCache(ConcurrentHashMap<String, CachedClient> clients) {
        Iterator<Map.Entry<String, CachedClient>> it = clients.entrySet().iterator();
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
            LOG.debug("Error closing Keycloak admin client");
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
