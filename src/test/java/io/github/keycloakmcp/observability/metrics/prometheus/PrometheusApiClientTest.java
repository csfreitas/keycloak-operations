package io.github.keycloakmcp.observability.metrics.prometheus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.keycloakmcp.credential.MetricsCredentials;

class PrometheusApiClientTest {

    private HttpServer server;
    private String baseUrl;
    private PrometheusApiClient client;
    private final AtomicInteger lastStatus = new AtomicInteger(200);
    private volatile String lastBody;
    private volatile String lastAuth;

    @BeforeEach
    void setUp() throws IOException {
        client = new PrometheusApiClient(new ObjectMapper());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", this::handleQuery);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesVectorSuccess() {
        lastStatus.set(200);
        lastBody = """
                {"status":"success","data":{"resultType":"vector","result":[
                  {"metric":{"__name__":"up","job":"keycloak"},"value":[1710000000,"1"]}
                ]}}
                """;
        PrometheusApiClient.Response response = client.query(
                baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.OK);
        assertThat(response.series()).hasSize(1);
        assertThat(response.series().get(0).samples()).hasSize(1);
        assertThat(response.series().get(0).samples().get(0).value()).isEqualTo(1.0);
        assertThat(lastAuth).isNull();
    }

    @Test
    void sendsBearerTokenWithoutLoggingRequirement() {
        lastStatus.set(200);
        lastBody = """
                {"status":"success","data":{"resultType":"vector","result":[]}}
                """;
        PrometheusApiClient.Response response = client.query(
                baseUrl,
                "up",
                MetricsCredentials.bearer("secret-token", null, false),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.EMPTY);
        assertThat(lastAuth).isEqualTo("Bearer secret-token");
    }

    @Test
    void emptyResult() {
        lastStatus.set(200);
        lastBody = """
                {"status":"success","data":{"resultType":"vector","result":[]}}
                """;
        PrometheusApiClient.Response response = client.query(
                baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.EMPTY);
        assertThat(response.series()).isEmpty();
    }

    @Test
    void unauthorized() {
        lastStatus.set(401);
        lastBody = "unauthorized";
        PrometheusApiClient.Response response = client.query(
                baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.UNAUTHORIZED);
    }

    @Test
    void serverError() {
        lastStatus.set(500);
        lastBody = "boom";
        PrometheusApiClient.Response response = client.query(
                baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.SERVER_ERROR);
    }

    @Test
    void malformedJson() {
        lastStatus.set(200);
        lastBody = "{not-json";
        PrometheusApiClient.Response response = client.query(
                baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
    }

    @Test
    void parseBodyMatrix() {
        String body = """
                {"status":"success","data":{"resultType":"matrix","result":[
                  {"metric":{"job":"kc"},"values":[[1710000000,"0.1"],[1710000060,"0.2"]]}
                ]}}
                """;
        PrometheusApiClient.Response response = client.parseBody(body);
        assertThat(response.status()).isEqualTo(PrometheusApiClient.Status.OK);
        assertThat(response.series().get(0).samples()).hasSize(2);
        assertThat(response.series().get(0).samples().get(1).value()).isEqualTo(0.2);
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
        byte[] bytes = (lastBody == null ? "" : lastBody).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(lastStatus.get(), bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
