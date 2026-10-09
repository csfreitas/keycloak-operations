package io.github.keycloakmcp.adapter.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.InfrastructureCredentials;
import io.github.keycloakmcp.target.InfrastructureTargetConfiguration;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Creates and caches per-target Kubernetes/OpenShift clients.
 * <p>
 * <b>Auth modes (in priority order):</b>
 * <ol>
 *   <li>KUBECONFIG – path to a kubeconfig file</li>
 *   <li>TOKEN – bearer token + API server URL</li>
 *   <li>IN_CLUSTER – explicitly enabled mounted pod service account, without ambient configuration</li>
 * </ol>
 * <p>
 * Clients are cached by target ID + fingerprint ({@code url, namespace, sha256(token), trustInsecure}).
 * When configuration changes, the old client is closed and replaced.
 * Secrets (token) are never logged.
 */
@ApplicationScoped
public class InfrastructureClientFactory {

    private static final Logger LOG = Logger.getLogger(InfrastructureClientFactory.class);

    private final CredentialProvider credentialProvider;
    private final ConcurrentHashMap<String, CachedClient> cache = new ConcurrentHashMap<>();

    @Inject
    public InfrastructureClientFactory(CredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * Resolves a {@link ClusterClient} for the given target.
     *
     * @return empty when infrastructure type is NONE or absent
     */
    public Optional<ClusterClient> resolve(Target target) {
        if (target == null || !target.hasInfrastructure()) {
            return Optional.empty();
        }
        InfrastructureType type = target.infrastructureTypeOrNone();
        if (type == InfrastructureType.NONE || type == InfrastructureType.VM) {
            return Optional.empty();
        }

        InfrastructureTargetConfiguration infraConfig = target.infrastructure();
        String credentialRef = infraConfig != null ? infraConfig.credentialRef() : null;

        InfrastructureCredentials credentials;
        if (credentialRef != null && !credentialRef.isBlank()) {
            credentials = credentialProvider.getInfrastructureCredentials(credentialRef);
        } else {
            return Optional.empty();
        }

        String namespace = infraConfig != null ? infraConfig.namespace() : null;
        Config resolvedConfig = ExplicitInfrastructureConfig.resolve(credentials, namespace);
        // Evidence collection has explicit response deadlines; do not silently multiply requests.
        resolvedConfig.setRequestRetryBackoffLimit(0);
        String fingerprint = fingerprint(resolvedConfig, credentialRef, target);
        String cacheKey = target.id().value();

        CachedClient existing = cache.get(cacheKey);
        if (existing != null && existing.fingerprint().equals(fingerprint)) {
            return Optional.of(existing.client());
        }

        synchronized (cache) {
            existing = cache.get(cacheKey);
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return Optional.of(existing.client());
            }
            if (existing != null) {
                closeQuietly(existing.client());
                cache.remove(cacheKey);
            }

            LOG.debugf("Building infrastructure client for target=%s type=%s authMode=%s",
                    cacheKey, type, credentials.authMode());

            DefaultClusterClient client = buildClient(resolvedConfig, namespace, type);
            cache.put(cacheKey, new CachedClient(client, fingerprint));
            return Optional.of(client);
        }
    }

    @PreDestroy
    void shutdown() {
        Iterator<Map.Entry<String, CachedClient>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            closeQuietly(it.next().getValue().client());
            it.remove();
        }
    }

    private DefaultClusterClient buildClient(
            Config config, String namespace, InfrastructureType typeHint) {

        KubernetesClient k8sClient = new KubernetesClientBuilder().withConfig(config)
                .withHttpClientFactory(new RedirectRejectingHttpClientFactory()).build();
        DefaultClusterClient client = new DefaultClusterClient(k8sClient,
                namespace != null && !namespace.isBlank() ? namespace : k8sClient.getNamespace());
        client.setTypeHint(typeHint);
        return client;
    }

    private static String fingerprint(Config config, String credentialRef, Target target) {
        // Resolved content makes CA, token and explicit kubeconfig rotation invalidate the cache.
        // Hash the complete resolved configuration in memory, including proxy/impersonation/TLS.
        // Neither this serialization nor its digest is logged, returned or persisted.
        return sha256(credentialRef + "|" + target.infrastructureTypeOrNone().name() + "|"
                + target.infrastructure().clusterId() + "|"
                + io.fabric8.kubernetes.client.utils.Serialization.asJson(config));
    }

    private static void closeQuietly(ClusterClient client) {
        if (client == null) return;
        try {
            client.close();
        } catch (RuntimeException e) {
            LOG.debug("Error closing cluster client");
        }
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

    private record CachedClient(DefaultClusterClient client, String fingerprint) {
    }
}
