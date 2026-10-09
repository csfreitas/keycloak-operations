package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.KeycloakCredentials;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.*;

class CollectionAdminTransportTest {
    @Test
    void tokenAndAdminCallsShareBudgetAndFollowingExpiredCallSendsNothing() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            reply(exchange, exchange.getRequestURI().getPath().endsWith("/token")
                    ? "{\"access_token\":\"test-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}" : "[]");
        });
        server.start();
        var provider = mock(CredentialProvider.class);
        when(provider.getKeycloakCredentials("ref")).thenReturn(new KeycloakCredentials("client", "secret"));
        var factory = new KeycloakClientFactory(provider);
        var adapter = new StableAdminApiAdapter(factory, mock(McpMetrics.class));
        Target target = target(server);
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofSeconds(10), clock::get))) {
            assertThat(adapter.listRealms(target)).isEmpty();
            assertThat(requests.get()).isEqualTo(2);
            clock.set(10_000_000_000L);
            assertThatThrownBy(() -> adapter.listRealms(target)).isInstanceOf(CollectionBudget.Aborted.class);
            assertThat(requests.get()).isEqualTo(2);
        } finally { factory.shutdown(); server.stop(0); }
    }

    @Test
    void lateTokenResponseIsRejectedWithoutIssuingAdminRequest() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            clock.set(10_000_000_000L);
            reply(exchange, "{\"access_token\":\"sensitive-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}");
        });
        server.start();
        var provider = mock(CredentialProvider.class);
        when(provider.getKeycloakCredentials("ref")).thenReturn(new KeycloakCredentials("client", "secret"));
        var factory = new KeycloakClientFactory(provider);
        var adapter = new StableAdminApiAdapter(factory, mock(McpMetrics.class));
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofSeconds(10), clock::get))) {
            assertThatThrownBy(() -> adapter.listRealms(target(server))).isInstanceOf(CollectionBudget.Aborted.class)
                    .hasMessage("OPERATION_BUDGET_EXCEEDED").hasNoCause();
            assertThat(requests.get()).isEqualTo(1);
        } finally { factory.shutdown(); server.stop(0); }
    }

    @Test
    void cachedCollectionClientCannotExecuteUnderForeignOrMissingScope() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { requests.incrementAndGet(); reply(exchange, "[]"); });
        server.start();
        try (var client = CollectionAdminTransport.create("test")) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> client.target(url).request().get()).isInstanceOf(RuntimeException.class);
            try (var scope = CollectionBudget.open("foreign", 30_000)) {
                assertThatThrownBy(() -> client.target(url).request().get()).isInstanceOf(RuntimeException.class);
            }
            assertThat(requests.get()).isZero();
        } finally { server.stop(0); }
    }

    private static void reply(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().close();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    private static Target target(HttpServer server) {
        return new Target(TargetId.of("test"), "test", TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration("http://127.0.0.1:" + server.getAddress().getPort(), "master", "client", "ref"),
                null, null, Map.of());
    }
}
