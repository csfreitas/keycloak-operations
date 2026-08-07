package io.github.keycloakmcp.adapter.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.InfrastructureCredentials;
import io.github.keycloakmcp.credential.InfrastructureCredentials.AuthMode;
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
 *   <li>IN_CLUSTER – service account automatically detected via {@link Config#autoConfigure()}</li>
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
            credentials = InfrastructureCredentials.inCluster();
        }

        String namespace = infraConfig != null ? infraConfig.namespace() : null;
        String fingerprint = fingerprint(credentials, namespace);
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

            DefaultClusterClient client = buildClient(credentials, namespace, type);
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
            InfrastructureCredentials credentials, String namespace, InfrastructureType typeHint) {

        Config config = buildFabric8Config(credentials, namespace);
        KubernetesClient k8sClient = new KubernetesClientBuilder().withConfig(config).build();
        DefaultClusterClient client = new DefaultClusterClient(k8sClient,
                namespace != null && !namespace.isBlank() ? namespace : k8sClient.getNamespace());
        client.setTypeHint(typeHint);
        return client;
    }

    private Config buildFabric8Config(InfrastructureCredentials credentials, String namespace) {
        AuthMode mode = credentials.authMode();
        if (mode == AuthMode.KUBECONFIG) {
            // Load kubeconfig from path without mutating global System properties
            // (would break multi-target concurrency).
            try {
                String contents = java.nio.file.Files.readString(
                        java.nio.file.Path.of(credentials.kubeconfigPath()),
                        StandardCharsets.UTF_8);
                Config loaded = Config.fromKubeconfig(contents);
                ConfigBuilder builder = new ConfigBuilder(loaded);
                if (namespace != null && !namespace.isBlank()) {
                    builder.withNamespace(namespace);
                }
                if (credentials.trustInsecure()) {
                    builder.withTrustCerts(true);
                }
                return builder.build();
            } catch (java.io.IOException e) {
                throw io.github.keycloakmcp.domain.error.McpException.authenticationFailed(
                        "Unable to read kubeconfig at configured path");
            }
        }

        if (mode == AuthMode.TOKEN) {
            ConfigBuilder builder = new ConfigBuilder();
            if (credentials.apiServerUrl() != null && !credentials.apiServerUrl().isBlank()) {
                builder.withMasterUrl(credentials.apiServerUrl());
            }
            builder.withOauthToken(credentials.token());
            builder.withTrustCerts(credentials.trustInsecure());
            if (credentials.caCertData() != null && !credentials.caCertData().isBlank()) {
                // caCertData is base64-encoded PEM; fabric8 wants raw PEM bytes as base64
                builder.withCaCertData(credentials.caCertData());
            }
            if (namespace != null && !namespace.isBlank()) {
                builder.withNamespace(namespace);
            }
            return builder.build();
        }

        // IN_CLUSTER: auto-configure from service account / KUBECONFIG env var
        Config base = Config.autoConfigure(null);
        ConfigBuilder builder = new ConfigBuilder(base);
        if (namespace != null && !namespace.isBlank()) {
            builder.withNamespace(namespace);
        }
        return builder.build();
    }

    private static String fingerprint(InfrastructureCredentials credentials, String namespace) {
        String tokenPart = credentials.token() != null ? sha256(credentials.token()) : "no-token";
        String urlPart = credentials.apiServerUrl() != null ? credentials.apiServerUrl() : "in-cluster";
        String kubeconfigPart = credentials.kubeconfigPath() != null ? credentials.kubeconfigPath() : "no-kubeconfig";
        String nsPart = namespace != null ? namespace : "default";
        return sha256(urlPart + "|" + tokenPart + "|" + kubeconfigPart + "|" + nsPart
                + "|" + credentials.trustInsecure() + "|" + credentials.authMode());
    }

    private static void closeQuietly(ClusterClient client) {
        if (client == null) return;
        try {
            client.close();
        } catch (RuntimeException e) {
            LOG.debugf(e, "Error closing cluster client");
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
