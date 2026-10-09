package io.github.keycloakmcp.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

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
import io.github.keycloakmcp.target.*;

/** Real loopback HTTP path, not a real Prometheus server or cluster acceptance. */
class ScrapeObservationHttpIsolationTest {
    private record Request(String query, String auth) {}
    private final ObjectMapper mapper = new ObjectMapper();
    private final CredentialProvider credentials = mock(CredentialProvider.class);
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String endpoint;
    private PrometheusMetricsProvider provider;
    private volatile String overrideTarget;
    private volatile boolean omitTarget;

    @BeforeEach void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", this::handle);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        var config = mock(MetricsConfig.class);
        when(config.operationTimeoutMs()).thenReturn(30000);
        when(config.maxSeries()).thenReturn(10);
        when(config.staleAfter()).thenReturn("5m");
        when(config.connectTimeoutMs()).thenReturn(1000);
        when(config.readTimeoutMs()).thenReturn(2000);
        when(credentials.getMetricsCredentials("metrics-a")).thenReturn(MetricsCredentials.bearer("fixture-a-sentinel", null, false));
        when(credentials.getMetricsCredentials("metrics-b")).thenReturn(MetricsCredentials.bearer("fixture-b-sentinel", null, false));
        provider = new PrometheusMetricsProvider(new PrometheusApiClient(mapper),
                new MetricsEndpointResolver(Optional.empty(), Optional.empty()), credentials, config);
    }

    @AfterEach void cleanup() { if (server != null) server.stop(0); }

    @Test void sameEndpointNamespaceAndJobStillUseDistinctTargetSelectorsAndCredentials() {
        var a = provider.probeScrape(target("a"));
        var b = provider.probeScrape(target("b"));
        assertThat(a.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        assertThat(a.failed()).isEqualTo(1);
        assertThat(a.successful()).isZero();
        assertThat(b.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        assertThat(b.failed()).isZero();
        assertThat(b.successful()).isEqualTo(1);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).query()).startsWith("up{").contains("target_id=\"a\"", "namespace=\"shared-ns\"", "job=\"shared-job\"")
                .doesNotContain("count(", "forged-tag");
        assertThat(requests.get(1).query()).contains("target_id=\"b\"", "namespace=\"shared-ns\"", "job=\"shared-job\"");
        assertThat(requests.get(0).auth()).isEqualTo("Bearer fixture-a-sentinel");
        assertThat(requests.get(1).auth()).isEqualTo("Bearer fixture-b-sentinel");
        assertThat(a.toString() + b).doesNotContain("sentinel", "internal-scrape-host", "shared-job", "http://");
    }

    @Test void foreignOrMissingTargetLabelsNeverBecomeAvailableEvenWithSuccessfulValues() {
        overrideTarget = "b";
        var foreign = provider.probeScrape(target("a"));
        assertThat(foreign.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(foreign.reason()).isEqualTo("SCOPE_MISMATCH");
        assertThat(foreign.successful()).isNull();
        omitTarget = true;
        var missing = provider.probeScrape(target("b"));
        assertThat(missing.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(missing.reason()).isEqualTo("SCOPE_MISMATCH");
        assertThat(missing.observed()).isNull();
    }

    @Test void credentialFailureAfterAnotherTargetsSuccessDoesNotReuseCredentialsOrSendAnonymousRequest() {
        assertThat(provider.probeScrape(target("b")).availability()).isEqualTo(MetricAvailability.AVAILABLE);
        when(credentials.getMetricsCredentials("metrics-a")).thenThrow(new IllegalStateException("private-credential-canary"));
        var result = provider.probeScrape(target("a"));
        assertThat(result.availability()).isEqualTo(MetricAvailability.NOT_AVAILABLE);
        assertThat(result.reason()).isEqualTo("UNAUTHORIZED");
        assertThat(result.toString()).doesNotContain("private-credential-canary", "sentinel");
        assertThat(requests).hasSize(1);
    }

    private Target target(String id) {
        return new Target(TargetId.of(id), id, TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://127.0.0.1", "realm", "client", "kc-" + id, null), null,
                new ObservabilityTargetConfiguration("PROMETHEUS", null, endpoint, "metrics-" + id, "shared-ns", "NAMESPACE"),
                Map.of("job", "shared-job", "target_id", "forged-tag"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        var params = new LinkedHashMap<String, String>();
        for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        String query = params.get("query");
        requests.add(new Request(query, exchange.getRequestHeaders().getFirst("Authorization")));
        String requested = query.contains("target_id=\"a\"") ? "a" : "b";
        String observed = overrideTarget == null ? requested : overrideTarget;
        var labels = new LinkedHashMap<>(Map.of("__name__", "up", "job", "shared-job", "namespace", "shared-ns",
                "instance", "internal-scrape-host:9000"));
        if (!omitTarget) labels.put("target_id", observed);
        byte[] body = mapper.writeValueAsBytes(Map.of("status", "success", "data", Map.of("resultType", "vector",
                "result", List.of(Map.of("metric", labels, "value", List.of(Long.parseLong(params.get("time")),
                        "a".equals(observed) ? "0" : "1"))))));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }
}
