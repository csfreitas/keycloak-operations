package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.keycloakmcp.config.MetricsConfig;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.observability.metrics.prometheus.PrometheusApiClient;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.ObservabilityTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

/**
 * Verifies metrics HTTP auth uses only the target's credentialRef.
 * Tokens are compared in assertions but never logged.
 */
class MetricsCredentialIsolationTest {

    private HttpServer server;
    private String endpoint;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", this::handle);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void targetRequestsUseOnlyOwnCredential() {
        MetricsConfig config = mock(MetricsConfig.class);
        when(config.maxRange()).thenReturn("24h");
        when(config.maxSeries()).thenReturn(500);
        when(config.maxPoints()).thenReturn(1000);
        when(config.staleAfter()).thenReturn("5m");
        when(config.connectTimeoutMs()).thenReturn(3000);
        when(config.readTimeoutMs()).thenReturn(5000);

        CredentialProvider credentials = mock(CredentialProvider.class);
        MetricsCredentials credA = MetricsCredentials.bearer("token-A-isolation-sentinel", null, false);
        MetricsCredentials credB = MetricsCredentials.bearer("token-B-isolation-sentinel", null, false);
        when(credentials.getMetricsCredentials("credentials-a")).thenReturn(credA);
        when(credentials.getMetricsCredentials("credentials-b")).thenReturn(credB);

        MetricsEndpointResolver resolver = mock(MetricsEndpointResolver.class);
        Target targetA = target("target-a", "credentials-a");
        Target targetB = target("target-b", "credentials-b");
        when(resolver.resolve(any())).thenReturn(Optional.of(endpoint));
        when(resolver.queryContext(eq(targetA))).thenReturn(MetricsQueryContext.fromTarget(targetA));
        when(resolver.queryContext(eq(targetB))).thenReturn(MetricsQueryContext.fromTarget(targetB));

        PrometheusMetricsProvider provider = new PrometheusMetricsProvider(
                new PrometheusApiClient(new ObjectMapper()),
                resolver,
                credentials,
                config);

        lastAuth.set(null);
        provider.query(targetA, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        String authA = lastAuth.get();
        assertThat(authA).contains("token-A-isolation-sentinel");
        assertThat(authA).doesNotContain("token-B-isolation-sentinel");

        lastAuth.set(null);
        provider.query(targetB, SemanticMetric.DB_POOL_ACTIVE, MetricWindow.W_5M);
        String authB = lastAuth.get();
        assertThat(authB).contains("token-B-isolation-sentinel");
        assertThat(authB).doesNotContain("token-A-isolation-sentinel");
        assertThat(authA).isNotEqualTo(authB);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
        byte[] body = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private Target target(String id, String credentialRef) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "cli", "kc-" + id, null),
                null,
                new ObservabilityTargetConfiguration(
                        "PROMETHEUS", null, endpoint, credentialRef, null, "NAMESPACE"),
                java.util.Map.of("target_id", id));
    }
}
