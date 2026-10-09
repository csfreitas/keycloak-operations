package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.KeycloakCredentials;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.*;

class CollectionDiagnosticIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"org.apache.http.wire", "org.apache.http.headers", "org.jboss.resteasy.client.jaxrs.i18n"})
    void unsafeDiagnosticsRejectBeforeResolvingCredentials(String category) {
        Logger logger = Logger.getLogger(category);
        Level original = logger.getLevel();
        var credentials = mock(CredentialProvider.class);
        var factory = new KeycloakClientFactory(credentials);
        try (var scope = CollectionBudget.open("test", 30_000)) {
            logger.setLevel(Level.FINE);
            assertThatThrownBy(() -> factory.getClient(target("http://127.0.0.1:1")))
                    .isInstanceOf(McpException.class).hasNoCause();
            verifyNoInteractions(credentials);
            assertThat(logger.getLevel()).isEqualTo(Level.FINE);
        } finally {
            logger.setLevel(original);
            factory.shutdown();
        }
    }

    @Test
    void cachedCollectionClientRechecksDiagnosticsBeforeTokenRequest() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        var credentials = mock(CredentialProvider.class);
        when(credentials.getKeycloakCredentials("ref")).thenReturn(new KeycloakCredentials("client", "test-secret"));
        var factory = new KeycloakClientFactory(credentials);
        var target = target("http://127.0.0.1:" + server.getAddress().getPort());
        Logger logger = Logger.getLogger("org.apache.http.wire");
        Level original = logger.getLevel();
        try (var scope = CollectionBudget.open("test", 30_000)) {
            var cached = factory.getClient(target);
            logger.setLevel(Level.FINEST);
            assertThatThrownBy(() -> cached.serverInfo().getInfo()).isInstanceOf(RuntimeException.class);
            assertThat(requests).hasValue(0);
            assertThatThrownBy(() -> new StableAdminApiAdapter(factory, mock(McpMetrics.class)).listRealms(target))
                    .isInstanceOf(McpException.class).hasNoCause();
            assertThat(requests).hasValue(0);
        } finally {
            logger.setLevel(original);
            factory.shutdown();
            server.stop(0);
        }
    }

    @Test
    void ordinaryClientConstructionDoesNotAcquireScopedDiagnosticPolicy() {
        var credentials = mock(CredentialProvider.class);
        when(credentials.getKeycloakCredentials("ref")).thenReturn(new KeycloakCredentials("client", "test-secret"));
        var factory = new KeycloakClientFactory(credentials);
        Logger logger = Logger.getLogger("org.apache.http.wire");
        Level original = logger.getLevel();
        try {
            logger.setLevel(Level.FINE);
            assertThat(factory.getClient(target("http://127.0.0.1:1"))).isNotNull();
            verify(credentials).getKeycloakCredentials("ref");
        } finally {
            logger.setLevel(original);
            factory.shutdown();
        }
    }

    private static Target target(String url) {
        return new Target(TargetId.of("test"), "test", TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                new KeycloakTargetConfiguration(url, "master", "client", "ref"), null, null, Map.of());
    }
}
