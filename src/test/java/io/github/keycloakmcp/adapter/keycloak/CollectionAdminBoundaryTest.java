package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.credential.KeycloakCredentials;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

/** Exercises the actual scoped RESTEasy/Keycloak provider chain against loopback only. */
class CollectionAdminBoundaryTest {
    private static final String CANARY = "admin-boundary-secret-canary";
    private static final String TOKEN = "{\"access_token\":\"loopback-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}";
    private static final String REALMS = "[{\"realm\":\"master\"}]";

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRealmLists")
    void invalidRealmEnvelopeCannotBecomeAnObservedList(InvalidJson invalid) throws Exception {
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), Reply.json(invalid.body()));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            assertThat(fixture.adminRequests.get()).isEqualTo(1);
        }
    }

    static Stream<InvalidJson> invalidRealmLists() {
        return Stream.of(
                new InvalidJson("duplicate field", "[{\"realm\":\"master\",\"realm\":\"" + CANARY + "\"}]"),
                new InvalidJson("trailing document", REALMS + " {\"secret\":\"" + CANARY + "\"}"),
                new InvalidJson("root null", "null"),
                new InvalidJson("object root", "{}"),
                new InvalidJson("scalar root", "true"),
                new InvalidJson("null list item", "[null]"),
                new InvalidJson("scalar list item", "[\"" + CANARY + "\"]"),
                new InvalidJson("valid prefix then malformed item", "[{\"realm\":\"master\"},false]"),
                new InvalidJson("missing realm identity", "[{}]"),
                new InvalidJson("null realm identity", "[{\"realm\":null}]"),
                new InvalidJson("blank realm identity", "[{\"realm\":\" \"}]"),
                new InvalidJson("duplicate realm identity", "[{\"realm\":\"master\"},{\"realm\":\"master\"}]"),
                new InvalidJson("duplicate explicit id", "[{\"id\":\"same\",\"realm\":\"one\"},{\"id\":\"same\",\"realm\":\"two\"}]"),
                new InvalidJson("wrong boolean type", "[{\"realm\":\"master\",\"enabled\":\"true\"}]"),
                new InvalidJson("truncated list", "[{\"realm\":\"master\"}"),
                new InvalidJson("too many realms", realmList(501)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    void invalidTokenNeverStartsAnAdminRequest(InvalidJson invalid) throws Exception {
        try (Fixture fixture = new Fixture(Reply.json(invalid.body()), Reply.json(REALMS));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            assertThat(fixture.adminRequests.get()).isZero();
        }
    }

    static Stream<InvalidJson> invalidTokens() {
        return Stream.of(
                new InvalidJson("null token", "null"),
                new InvalidJson("array token", "[]"),
                new InvalidJson("missing token", "{\"expires_in\":300,\"token_type\":\"Bearer\"}"),
                new InvalidJson("empty token", "{\"access_token\":\"\",\"expires_in\":300,\"token_type\":\"Bearer\"}"),
                new InvalidJson("token contains space", "{\"access_token\":\"bad " + CANARY + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}"),
                new InvalidJson("token contains newline", "{\"access_token\":\"bad\\n" + CANARY + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}"),
                new InvalidJson("missing expiry", "{\"access_token\":\"test\",\"token_type\":\"Bearer\"}"),
                new InvalidJson("zero expiry", "{\"access_token\":\"test\",\"expires_in\":0,\"token_type\":\"Bearer\"}"),
                new InvalidJson("negative expiry", "{\"access_token\":\"test\",\"expires_in\":-1,\"token_type\":\"Bearer\"}"),
                new InvalidJson("fractional expiry", "{\"access_token\":\"test\",\"expires_in\":2.5,\"token_type\":\"Bearer\"}"),
                new InvalidJson("text expiry", "{\"access_token\":\"test\",\"expires_in\":\"300\",\"token_type\":\"Bearer\"}"),
                new InvalidJson("wrong token type", "{\"access_token\":\"test\",\"expires_in\":300,\"token_type\":\"Basic\"}"),
                new InvalidJson("duplicate token field", TOKEN.substring(0, TOKEN.length() - 1) + ",\"access_token\":\"" + CANARY + "\"}"),
                new InvalidJson("trailing token document", TOKEN + " {\"secret\":\"" + CANARY + "\"}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidClientLists")
    void invalidClientEnvelopeCannotReturnAValidPrefix(InvalidJson invalid) throws Exception {
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), Reply.json(invalid.body()));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listClients(fixture.target, "master", false));
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            // Realm existence was read first; an invalid client list must not turn into [] or a prefix.
            assertThat(fixture.adminRequests.get()).isEqualTo(2);
        }
    }

    static Stream<InvalidJson> invalidClientLists() {
        return Stream.of(
                new InvalidJson("null client list", "null"),
                new InvalidJson("object client list", "{}"),
                new InvalidJson("missing client identity", "[{}]"),
                new InvalidJson("valid client then null", "[{\"clientId\":\"one\"},null]"),
                new InvalidJson("duplicate client identity", "[{\"clientId\":\"one\"},{\"clientId\":\"one\"}]"),
                new InvalidJson("invalid URI collection", "[{\"clientId\":\"one\",\"redirectUris\":[true]}]"),
                new InvalidJson("invalid attributes", "[{\"clientId\":\"one\",\"attributes\":{\"key\":false}}]"),
                new InvalidJson("too many clients", clientList(501)));
    }

    @Test
    void explicitlyEmptyListsRemainValidObservedZero() throws Exception {
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), Reply.json("[]"));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertThat(fixture.adapter.listRealms(fixture.target)).isEmpty();
            assertThat(fixture.adapter.listClients(fixture.target, "master", false)).isEmpty();
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            assertThat(fixture.adminRequests.get()).isEqualTo(3);
        }
    }

    @Test
    void maximumAcceptedRealmListDoesNotSilentlyTruncate() throws Exception {
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), Reply.json(realmList(500)));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertThat(fixture.adapter.listRealms(fixture.target)).hasSize(500);
            assertThat(fixture.adminRequests.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "chunked={0}")
    @ValueSource(booleans = {false, true})
    void oversizedAdminBodyRejectsEvenAValidJsonPrefix(boolean chunked) throws Exception {
        Reply oversized = new Reply(200, bytes(REALMS + " ".repeat(1_048_576)), chunked, Map.of());
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), oversized);
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
            assertThat(fixture.adminRequests.get()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "chunked={0}")
    @ValueSource(booleans = {false, true})
    void oversizedTokenBodyNeverStartsAdminRequest(boolean chunked) throws Exception {
        Reply oversized = new Reply(200, bytes(TOKEN + " ".repeat(65_536)), chunked, Map.of());
        try (Fixture fixture = new Fixture(oversized, Reply.json(REALMS));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            assertThat(fixture.adminRequests.get()).isZero();
        }
    }

    @Test
    void compressedBodyIsBoundedAfterDecompression() throws Exception {
        byte[] compressed = gzip(REALMS + " ".repeat(1_048_576));
        assertThat(compressed.length).isLessThan(8_192);
        Reply oversized = new Reply(200, compressed, false, Map.of("Content-Encoding", "gzip"));
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), oversized);
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
            assertThat(fixture.adminRequests.get()).isEqualTo(1);
        }
    }

    @Test
    void ordinaryCompressedReplyRemainsSupported() throws Exception {
        Reply compressed = new Reply(200, gzip(REALMS), false, Map.of("Content-Encoding", "gzip"));
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), compressed);
                var scope = CollectionBudget.open("test", 30_000)) {
            assertThat(fixture.adapter.listRealms(fixture.target)).singleElement()
                    .extracting(realm -> realm.getRealm()).isEqualTo("master");
        }
    }

    @Test
    void malformedAuthenticationChallengeDoesNotLogSecretsOrTriggerNativeAuth() throws Exception {
        Reply rejected = new Reply(401, bytes("{\"error\":\"" + CANARY + "\"}"), false,
                Map.of("WWW-Authenticate", "Basic realm=\"" + CANARY + "\", charset=\"unterminated",
                        "Set-Cookie", "session=" + CANARY + "; Expires=" + CANARY));
        try (CapturedDiagnostics diagnostics = new CapturedDiagnostics();
                Fixture fixture = new Fixture(Reply.json(TOKEN), rejected);
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            assertThat(fixture.adminRequests.get()).isEqualTo(1);
            diagnostics.assertSafe();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"br", "deflate", "gzip, gzip", ""})
    void unsupportedEncodingCannotMakeRawJsonLookValid(String encoding) throws Exception {
        Reply invalid = new Reply(200, bytes(REALMS), false, Map.of("Content-Encoding", encoding));
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), invalid);
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
        }
    }

    @Test
    void malformedGzipIsCauseFree() throws Exception {
        Reply invalid = new Reply(200, bytes(CANARY), false, Map.of("Content-Encoding", "gzip"));
        try (Fixture fixture = new Fixture(Reply.json(TOKEN), invalid);
                var scope = CollectionBudget.open("test", 30_000)) {
            assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void compressedTokenRespectsDecodedTokenLimit(boolean oversized) throws Exception {
        Reply token = new Reply(200, gzip(TOKEN + (oversized ? " ".repeat(65_536) : "")), false,
                Map.of("Content-Encoding", "gzip"));
        try (Fixture fixture = new Fixture(token, Reply.json("[]"));
                var scope = CollectionBudget.open("test", 30_000)) {
            if (oversized) {
                assertSafeFailure(() -> fixture.adapter.listRealms(fixture.target));
                assertThat(fixture.adminRequests).hasValue(0);
            } else {
                assertThat(fixture.adapter.listRealms(fixture.target)).isEmpty();
                assertThat(fixture.adminRequests).hasValue(1);
            }
        }
    }

    @Test
    void cookiesAreNotReplayedOrLoggedFromCollectionResponses() throws Exception {
        Map<String, String> cookie = Map.of("Set-Cookie", "session=" + CANARY + "; Path=/; Expires=" + CANARY);
        try (CapturedDiagnostics diagnostics = new CapturedDiagnostics();
                Fixture fixture = new Fixture(new Reply(200, bytes(TOKEN), false, cookie),
                        new Reply(200, bytes(REALMS), false, cookie));
                var scope = CollectionBudget.open("test", 30_000)) {
            assertThat(fixture.adapter.listRealms(fixture.target)).hasSize(1);
            assertThat(fixture.adapter.listRealms(fixture.target)).hasSize(1);
            assertThat(fixture.tokenRequests.get()).isEqualTo(1);
            assertThat(fixture.adminRequests.get()).isEqualTo(2);
            assertThat(fixture.receivedCookies).isEmpty();
            diagnostics.assertSafe();
        }
    }

    private static void assertSafeFailure(Runnable action) {
        McpException failure = assertThrows(McpException.class, action::run);
        assertThat(failure).hasNoCause();
        assertThat(failure.getSuppressed()).isEmpty();
        assertThat(failure.getMessage()).doesNotContain(CANARY, "127.0.0.1", "loopback-token");
        assertThat(failure.getError().details()).isEmpty();
    }

    private static String realmList(int count) {
        return "[" + String.join(",", java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "{\"realm\":\"realm-" + i + "\"}").toList()) + "]";
    }

    private static String clientList(int count) {
        return "[" + String.join(",", java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "{\"clientId\":\"client-" + i + "\"}").toList()) + "]";
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) { gzip.write(bytes(value)); }
        return output.toByteArray();
    }

    private record InvalidJson(String label, String body) {
        @Override public String toString() { return label; }
    }

    private record Reply(int status, byte[] body, boolean chunked, Map<String, String> headers) {
        static Reply json(String body) { return new Reply(200, bytes(body), false, Map.of()); }
    }

    private static final class Fixture implements AutoCloseable {
        final AtomicInteger tokenRequests = new AtomicInteger();
        final AtomicInteger adminRequests = new AtomicInteger();
        final List<String> receivedCookies = new CopyOnWriteArrayList<>();
        final HttpServer server;
        final KeycloakClientFactory factory;
        final StableAdminApiAdapter adapter;
        final Target target;

        Fixture(Reply token, Reply admin) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                if (cookie != null) receivedCookies.add(cookie);
                String path = exchange.getRequestURI().getPath();
                Reply reply;
                if (path.endsWith("/token")) {
                    tokenRequests.incrementAndGet();
                    reply = token;
                } else {
                    adminRequests.incrementAndGet();
                    reply = path.endsWith("/realms/master") ? Reply.json("{\"realm\":\"master\"}") : admin;
                }
                respond(exchange, reply);
            });
            CredentialProvider credentials = mock(CredentialProvider.class);
            when(credentials.getKeycloakCredentials("ref")).thenReturn(new KeycloakCredentials("client", "secret"));
            factory = new KeycloakClientFactory(credentials);
            adapter = new StableAdminApiAdapter(factory, mock(McpMetrics.class));
            target = new Target(TargetId.of("test"), "test", TargetType.KEYCLOAK, TargetEnvironment.DEV, true,
                    new KeycloakTargetConfiguration("http://127.0.0.1:" + server.getAddress().getPort(),
                            "master", "client", "ref"), null, null, Map.of());
            server.start();
        }

        private static void respond(HttpExchange exchange, Reply reply) {
            try {
                exchange.getRequestBody().close();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                reply.headers().forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
                exchange.sendResponseHeaders(reply.status(), reply.chunked() ? 0 : reply.body().length);
                exchange.getResponseBody().write(reply.body());
            } catch (IOException ignored) {
                // Rejecting a bounded body aborts the connection before the fixture finishes writing.
            } finally { exchange.close(); }
        }

        @Override public void close() {
            try { factory.shutdown(); } finally { server.stop(0); }
        }
    }

    private static final class CapturedDiagnostics implements AutoCloseable {
        private final Logger logger = Logger.getLogger("org.apache.http");
        private final Level previous = logger.getLevel();
        private final List<LogRecord> records = new ArrayList<>();
        private final Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };

        CapturedDiagnostics() {
            // Enable WARN diagnostics without enabling unsafe third-party DEBUG/TRACE logging.
            logger.setLevel(Level.INFO);
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
        }

        void assertSafe() {
            SimpleFormatter formatter = new SimpleFormatter();
            assertThat(records).allSatisfy(record -> assertThat(formatter.format(record))
                    .doesNotContain(CANARY, "loopback-token"));
        }

        @Override public void close() {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
    }
}
