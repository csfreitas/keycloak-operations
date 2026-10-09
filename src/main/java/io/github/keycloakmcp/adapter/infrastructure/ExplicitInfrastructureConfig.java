package io.github.keycloakmcp.adapter.infrastructure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.github.keycloakmcp.credential.InfrastructureCredentials;
import io.github.keycloakmcp.domain.error.McpException;

/** Builds target-bound configuration without consulting ambient kubeconfig or system properties. */
final class ExplicitInfrastructureConfig {
    private ExplicitInfrastructureConfig() { }

    static Config resolve(InfrastructureCredentials credentials, String namespace) {
        if (credentials == null || credentials.authMode() == null || blank(namespace)) throw invalid();
        Config config;
        switch (credentials.authMode()) {
            case TOKEN -> {
                if (blank(credentials.token()) || blank(credentials.apiServerUrl())) throw invalid();
                config = Config.empty();
                config.setMasterUrl(validServer(credentials.apiServerUrl()));
                config.setOauthToken(credentials.token());
                config.setCaCertData(credentials.caCertData());
                config.setTrustCerts(credentials.trustInsecure());
            }
            case KUBECONFIG -> {
                try {
                    String contents = readBounded(Path.of(credentials.kubeconfigPath()));
                    var parsed = KubeConfigUtils.parseConfigFromString(contents);
                    if (blank(parsed.getCurrentContext()) || parsed.getContexts() == null || parsed.getClusters() == null
                            || parsed.getUsers() == null) throw invalid();
                    // Reject executable/dynamic credential providers before Fabric8 merges the file.
                    for (var user : parsed.getUsers()) {
                        if (user.getUser() == null || user.getUser().getExec() != null
                                || user.getUser().getAuthProvider() != null) throw invalid();
                    }
                    var contexts = parsed.getContexts().stream()
                            .filter(c -> parsed.getCurrentContext().equals(c.getName())).toList();
                    if (contexts.size() != 1 || contexts.getFirst().getContext() == null) throw invalid();
                    var context = contexts.getFirst().getContext();
                    var clusters = parsed.getClusters().stream().filter(c -> c.getName().equals(context.getCluster())).toList();
                    var users = parsed.getUsers().stream().filter(u -> u.getName().equals(context.getUser())).toList();
                    if (clusters.size() != 1 || users.size() != 1 || clusters.getFirst().getCluster() == null) throw invalid();
                    var cluster = clusters.getFirst().getCluster();
                    var user = users.getFirst().getUser();
                    validServer(cluster.getServer());
                    // Inline credentials only: no relative paths or hidden filesystem dependencies.
                    if (!blank(cluster.getCertificateAuthority()) || !blank(user.getClientCertificate())
                            || !blank(user.getClientKey()) || !blank(user.getUsername()) || !blank(user.getPassword())) throw invalid();
                    config = Config.fromKubeconfig(contents);
                    config.setOauthToken(config.getAutoOAuthToken());
                    config.setAutoOAuthToken(null);
                    config.setCurrentContext(null);
                    config.setContexts(java.util.List.of());
                    if (blank(config.getOauthToken()) && blank(config.getClientCertData()) && blank(config.getClientCertFile())) throw invalid();
                } catch (RuntimeException e) {
                    throw invalid(); // Parser exceptions may contain raw credential material.
                }
            }
            case IN_CLUSTER -> config = inCluster(namespace,
                    Path.of("/var/run/secrets/kubernetes.io/serviceaccount"));
            default -> throw invalid();
        }
        if (!blank(namespace)) config.setNamespace(namespace);
        if (blank(config.getNamespace())) throw invalid();
        config.setAutoConfigure(false);
        return config;
    }

    static Config inCluster(String namespace, Path serviceAccountDirectory) {
        if (blank(namespace)) throw invalid();
        String token = readBounded(serviceAccountDirectory.resolve("token")).trim();
        String ca = readBounded(serviceAccountDirectory.resolve("ca.crt"));
        if (blank(token) || blank(ca)) throw invalid();
        Config config = Config.empty();
        config.setMasterUrl("https://kubernetes.default.svc");
        config.setNamespace(namespace);
        config.setOauthToken(token);
        config.setCaCertData(Base64.getEncoder().encodeToString(ca.getBytes(StandardCharsets.UTF_8)));
        config.setTrustCerts(false);
        config.setAutoConfigure(false);
        return config;
    }

    static String readBounded(Path path) {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) throw invalid();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (java.io.IOException | IllegalArgumentException e) { throw invalid(); }
    }

    private static String validServer(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && loopback)) throw invalid();
            return raw;
        } catch (RuntimeException e) { throw invalid(); }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static McpException invalid() {
        return McpException.authenticationFailed("Explicit infrastructure credentials, endpoint and scope are required; ambient and executable authentication are disabled");
    }
}
