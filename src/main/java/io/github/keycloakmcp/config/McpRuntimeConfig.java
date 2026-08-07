package io.github.keycloakmcp.config;

import java.util.Map;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Multi-target MCP runtime configuration under the {@code mcp} prefix.
 * <p>
 * Enum-like fields are bound as {@link String} and parsed in
 * {@link io.github.keycloakmcp.target.ConfigurationTargetRegistry}
 * to keep ConfigMapping resilient across SmallRye versions.
 */
@ConfigMapping(prefix = "mcp")
public interface McpRuntimeConfig {

    @WithName("read-only")
    @WithDefault("true")
    boolean readOnly();

    /**
     * Target registry mode hint: configuration | database | composite.
     * Prefer {@link PlatformConfig#targetRegistry()} for platform wiring.
     */
    TargetSettings target();

    Map<String, TargetEntry> targets();

    Map<String, CredentialEntry> credentials();

    interface TargetSettings {
        @WithDefault("composite")
        String registry();
    }
    interface TargetEntry {

        @WithName("display-name")
        String displayName();

        /** KEYCLOAK or RHBK */
        String type();

        /** DEV, TEST, HML, STAGING, PRD, UNKNOWN */
        String environment();

        @WithDefault("true")
        boolean enabled();

        KeycloakEntry keycloak();

        Optional<InfrastructureEntry> infrastructure();

        Optional<ObservabilityEntry> observability();

        /** Optional tags; omit when unused (defaults to empty map). */
        Map<String, String> tags();
    }

    interface KeycloakEntry {

        String url();

        @WithName("auth-realm")
        @WithDefault("master")
        String authRealm();

        @WithName("client-id")
        String clientId();

        @WithName("credential-ref")
        String credentialRef();

        /**
         * Optional Keycloak management interface base URL (e.g. http://localhost:9000).
         * Used for /health probes; omit when not configured. Do not expose publicly.
         */
        @WithName("management-url")
        Optional<String> managementUrl();
    }

    interface InfrastructureEntry {

        /** OPENSHIFT, KUBERNETES, VM, NONE */
        String type();

        @WithName("cluster-id")
        Optional<String> clusterId();

        Optional<String> namespace();

        @WithName("credential-ref")
        Optional<String> credentialRef();
    }

    interface ObservabilityEntry {

        Optional<MetricsEntry> metrics();

        Optional<TracingEntry> tracing();

        interface MetricsEntry {
            Optional<String> type();
        }

        interface TracingEntry {
            Optional<String> type();
        }
    }

    interface CredentialEntry {

        @WithName("client-secret")
        Optional<String> clientSecret();

        /** Bearer / OAuth2 token for Kubernetes/OpenShift API access. */
        Optional<String> token();

        /** Kubernetes/OpenShift API server URL (e.g. https://api.cluster:6443). */
        @WithName("api-server-url")
        Optional<String> apiServerUrl();

        /** Base64-encoded PEM CA certificate for TLS verification. */
        @WithName("ca-cert-data")
        Optional<String> caCertData();

        /** Skip TLS verification — OFF by default; enable only in dev/test. */
        @WithName("trust-insecure")
        @WithDefault("false")
        boolean trustInsecure();

        /** Path to a kubeconfig file; takes precedence over token when set. */
        Optional<String> kubeconfig();
    }
}
