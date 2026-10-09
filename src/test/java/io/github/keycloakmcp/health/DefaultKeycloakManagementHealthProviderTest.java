package io.github.keycloakmcp.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.keycloakmcp.config.HealthConfig;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetId;

@Timeout(10)
class DefaultKeycloakManagementHealthProviderTest {
    private static final String SECRET = "fixture-secret-must-not-escape";
    private static final String UP = "{\"status\":\"UP\",\"checks\":[]}";
    private static final List<String> PATHS = List.of("/health/ready", "/health/live", "/health");
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    private volatile Reply defaultReply = new Reply(200, UP, false, 0, false);
    private volatile Consumer<String> requestHook = ignored -> {};
    private volatile long headerDelayMs;
    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private DefaultKeycloakManagementHealthProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        provider = provider(1000);
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void unconfiguredDoesNotConnect() {
        var result = provider.check(target(null, Map.of()));
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(result.message()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.details()).containsEntry("configured", false);
        assertThat(requests).isEmpty();
    }

    @Test
    void allUpEstablishesHealthyWithOnlySafeProjection() {
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(result.details()).containsOnlyKeys("configured", "/health/ready", "/health/live", "/health");
        assertThat(requests.keySet()).containsExactlyInAnyOrderElementsOf(PATHS);
        assertThat(requests.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        for (String path : PATHS) {
            assertThat(details(result, path)).containsEntry("httpStatus", 200)
                    .containsEntry("status", "HEALTHY").containsEntry("checkCount", 0);
        }
        assertSafe(result);
    }

    @Test
    void tagFallbackIsExplicitAndConfiguredUrlTakesPrecedence() {
        var tagResult = provider.check(target(null, Map.of("management-url", " " + baseUrl + "/ ")));
        assertThat(tagResult.status()).isEqualTo(HealthStatus.HEALTHY);
        var configuredResult = provider.check(target(baseUrl, Map.of("management-url", "https://" + SECRET)));
        assertThat(configuredResult.status()).isEqualTo(HealthStatus.HEALTHY);
        assertSafe(tagResult);
        assertSafe(configuredResult);
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of("UP", HealthStatus.HEALTHY), Arguments.of("ok", HealthStatus.HEALTHY),
                Arguments.of("healthy", HealthStatus.HEALTHY), Arguments.of("DOWN", HealthStatus.CRITICAL),
                Arguments.of("critical", HealthStatus.CRITICAL), Arguments.of("WARNING", HealthStatus.WARNING),
                Arguments.of(" degraded ", HealthStatus.WARNING));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void recognizesOnlyKnownStatusVocabulary(String status, HealthStatus expected) {
        defaultReply = new Reply(200, "{\"status\":\"" + status + "\"}", false, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(expected);
        assertSafe(result);
    }

    static Stream<String> malformedBodies() {
        return Stream.of("", " ", "{", "<html>" + SECRET + "</html>", "null", "[]", "\"UP\"",
                "{}", "{\"status\":null}", "{\"status\":true}", "{\"status\":{\"value\":\"UP\"}}",
                "{\"status\":\"" + SECRET + "\"}", "{\"status\":\"DOWN\",\"status\":\"UP\"}",
                UP + " {}", "{\"status\":\"UP\",\"checks\":{}}", "{\"status\":\"UP\",\"checks\":null}",
                "{\"status\":\"UP\",\"checks\":[{}]}", "{\"status\":\"UP\",\"checks\":[\"UP\"]}",
                "{\"status\":\"UP\",\"checks\":[{\"status\":\"" + SECRET + "\"}]}");
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void emptyMalformedAndUnrecognizedBodiesCannotBecomeHealthy(String body) {
        defaultReply = new Reply(200, body, false, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        for (String path : PATHS) {
            assertThat(details(result, path)).containsKey("reasonCode");
        }
        assertSafe(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health/ready", "/health"})
    void mixedUnknownAndHealthyAreUnknownRegardlessOfOrder(String missingPath) {
        replies.put(missingPath, new Reply(404, SECRET, false, 0, false));
        assertThat(check().status()).isEqualTo(HealthStatus.UNKNOWN);
    }

    static Stream<Arguments> unavailableStatuses() {
        return Stream.of(Arguments.of(401, "AUTHENTICATION_FAILED"), Arguments.of(403, "ACCESS_DENIED"),
                Arguments.of(404, "ENDPOINT_NOT_FOUND"), Arguments.of(429, "HTTP_RESPONSE_UNAVAILABLE"),
                Arguments.of(500, "HTTP_RESPONSE_UNAVAILABLE"), Arguments.of(502, "HTTP_RESPONSE_UNAVAILABLE"),
                Arguments.of(301, "REDIRECT_REJECTED"), Arguments.of(307, "REDIRECT_REJECTED"));
    }

    @ParameterizedTest
    @MethodSource("unavailableStatuses")
    void accessAndHttpFailuresDoNotInventApplicationOutageOrCopyResponse(int httpStatus, String reason) {
        defaultReply = new Reply(httpStatus, SECRET, false, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        for (String path : PATHS) {
            assertThat(details(result, path)).containsEntry("reasonCode", reason);
        }
        assertThat(requests).doesNotContainKey("/redirected");
        assertSafe(result);
    }

    @Test
    void preservesStructuredDownOn503() {
        defaultReply = new Reply(503, "{\"status\":\"DOWN\"}", false, 0, false);
        assertThat(check().status()).isEqualTo(HealthStatus.CRITICAL);
    }

    @Test
    void upOn503IsNotHealthy() {
        defaultReply = new Reply(503, UP, false, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(details(result, "/health/ready")).containsEntry("reasonCode", "HTTP_STATUS_CONFLICT");
    }

    @Test
    void nestedDownAnyNameIsCriticalWithoutCopyingNamesOrData() {
        defaultReply = new Reply(200, """
                {"status":"UP","checks":[
                  {"name":"fixture-secret-must-not-escape","status":"DOWN",
                   "data":{"password":"fixture-secret-must-not-escape"}},
                  {"name":"ordinary","status":"UP"}
                ]}
                """, false, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
        assertThat(details(result, "/health/ready")).containsEntry("checkCount", 2)
                .containsEntry("checkStatuses", Map.of("CRITICAL", 1, "HEALTHY", 1));
        assertSafe(result);
        assertThat(result.toString()).doesNotContain("ordinary", "password");
    }

    @Test
    void unknownDoesNotHideObservedCritical() {
        replies.put("/health/ready", new Reply(404, SECRET, false, 0, false));
        replies.put("/health/live", new Reply(200, "{\"status\":\"DOWN\"}", false, 0, false));
        assertThat(check().status()).isEqualTo(HealthStatus.CRITICAL);
    }

    @Test
    void excessiveNestedChecksAreUnknownWithoutReturningNames() {
        String checks = String.join(",", java.util.Collections.nCopies(101, "{\"name\":\"" + SECRET + "\",\"status\":\"UP\"}"));
        defaultReply = new Reply(200, "{\"status\":\"UP\",\"checks\":[" + checks + "]}", false, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(details(result, "/health/ready")).containsEntry("reasonCode", "CHECK_LIMIT_EXCEEDED");
        assertSafe(result);
    }

    @Test
    void deeplyNestedAndOversizedStringsAreRejected() {
        for (String extra : List.of("[".repeat(20) + "0" + "]".repeat(20), "\"" + "x".repeat(5000) + "\"")) {
            defaultReply = new Reply(200, "{\"status\":\"UP\",\"data\":" + extra + "}", false, 0, false);
            var result = check();
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(details(result, "/health/ready")).containsEntry("reasonCode", "MALFORMED_RESPONSE");
            assertSafe(result);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fixedLengthAndChunkedBodiesAreBounded(boolean chunked) {
        defaultReply = new Reply(200, " ".repeat(DefaultKeycloakManagementHealthProvider.MAX_RESPONSE_BYTES + 1),
                chunked, 0, false);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        for (String path : PATHS) {
            assertThat(details(result, path)).containsEntry("reasonCode", "RESPONSE_LIMIT_EXCEEDED");
        }
        assertSafe(result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothDelayedHeadersAndStalledBodyAreBounded(boolean delayBody) {
        provider = provider(150);
        defaultReply = new Reply(200, UP, true, 2500, delayBody);
        long start = System.nanoTime();
        var result = check();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
        for (String path : PATHS) {
            assertThat(details(result, path)).containsEntry("reasonCode", "TIMEOUT");
        }
        assertSafe(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://fixture-secret-must-not-escape@127.0.0.1", "file:///etc/passwd",
            "http://127.0.0.1?token=fixture-secret-must-not-escape", "http://127.0.0.1#fixture-secret-must-not-escape",
            "relative/fixture-secret-must-not-escape", "http://[broken"})
    void invalidConfigurationIsSafeAndDoesNotConnect(String url) {
        var result = provider.check(target(url, Map.of()));
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(result.details()).containsEntry("reasonCode", "INVALID_CONFIGURATION");
        assertThat(requests).isEmpty();
        assertSafe(result);
    }

    @Test
    void refusedConnectionIsUnknownWithoutEndpointInResult() {
        server.stop(0);
        var result = check();
        assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(details(result, "/health/ready")).containsEntry("reasonCode", "REQUEST_FAILED");
        assertSafe(result);
    }

    @Test
    void interruptedProbeRestoresFlagAndStopsFurtherEndpoints() {
        try {
            Thread.currentThread().interrupt();
            var result = check();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(result.details()).containsOnlyKeys("configured", "/health/ready", "collectionComplete", "reasonCode")
                    .containsEntry("collectionComplete", false).containsEntry("reasonCode", "OPERATION_INTERRUPTED");
            assertThat(details(result, "/health/ready")).containsEntry("reasonCode", "INTERRUPTED");
            assertSafe(result);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void expiredParentReturnsSafePartialWithoutAnyHttpRequest() {
        var clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(100), clock::get);
        clock.set(Duration.ofMillis(100).toNanos());
        try (var scope = CollectionBudget.open("management-target", budget)) {
            var result = check();
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(result.details()).containsEntry("collectionComplete", false)
                    .containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED")
                    .doesNotContainKeys("/health/live", "/health");
            assertThat(details(result, "/health/ready")).containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED");
            assertSafe(result);
        }
        assertThat(requests).isEmpty();
        assertThat(CollectionBudget.current()).isNull();
    }

    static Stream<Arguments> partialStatuses() {
        return Stream.of(Arguments.of("UP", HealthStatus.UNKNOWN, "HEALTHY"),
                Arguments.of("WARNING", HealthStatus.WARNING, "WARNING"),
                Arguments.of("DOWN", HealthStatus.CRITICAL, "CRITICAL"));
    }

    @ParameterizedTest
    @MethodSource("partialStatuses")
    void oneParentBudgetPreservesEarlierObservationAndStopsAfterLateSecondPath(
            String readyStatus, HealthStatus aggregate, String observed) {
        var clock = new AtomicLong();
        replies.put("/health/ready", new Reply(200, "{\"status\":\"" + readyStatus + "\"}", false, 0, false));
        requestHook = path -> {
            if (path.equals("/health/live")) clock.set(Duration.ofSeconds(10).toNanos());
        };
        try (var scope = CollectionBudget.open("management-target", new CollectionBudget(Duration.ofSeconds(10), clock::get))) {
            var result = check();
            assertThat(result.status()).isEqualTo(aggregate);
            assertThat(result.details()).containsEntry("collectionComplete", false)
                    .containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED").doesNotContainKey("/health");
            assertThat(details(result, "/health/ready")).containsEntry("status", observed);
            assertThat(details(result, "/health/live")).containsEntry("status", "UNKNOWN")
                    .containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED")
                    .doesNotContainKeys("httpStatus", "checkCount");
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
            assertSafe(result);
        }
        assertThat(requests.keySet()).containsExactlyInAnyOrder("/health/ready", "/health/live");
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test
    void lateFirstResponseCannotBecomeHealthy() {
        var clock = new AtomicLong();
        requestHook = path -> clock.set(Duration.ofSeconds(10).toNanos());
        try (var scope = CollectionBudget.open("management-target", new CollectionBudget(Duration.ofSeconds(10), clock::get))) {
            var result = check();
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(details(result, "/health/ready")).containsEntry("status", "UNKNOWN")
                    .doesNotContainKeys("httpStatus", "checkCount");
            assertThat(result.details()).containsEntry("collectionComplete", false).doesNotContainKey("/health/live");
        }
        assertThat(requests.keySet()).containsExactly("/health/ready");
    }

    @Test
    void inheritedBudgetAlsoCoversHeadersAndStalledBody() {
        provider = provider(3000);
        headerDelayMs = 200;
        defaultReply = new Reply(200, UP, true, 2500, true);
        long started = System.nanoTime();
        try (var scope = CollectionBudget.open("management-target", 700)) {
            var result = check();
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(result.details()).containsEntry("collectionComplete", false)
                    .containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED")
                    .doesNotContainKeys("/health/live", "/health");
            assertSafe(result);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void configuredMinimumCannotResurrectShorterParentBudget() {
        provider = provider(1);
        defaultReply = new Reply(200, UP, true, 2500, true);
        long started = System.nanoTime();
        try (var scope = CollectionBudget.open("management-target", 30)) {
            var result = check();
            assertThat(result.details()).containsEntry("collectionComplete", false)
                    .containsEntry("reasonCode", "OPERATION_BUDGET_EXCEEDED")
                    .doesNotContainKeys("/health/live", "/health");
            assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void differentTargetScopeFailsBeforeIoAndIsRestored() {
        try (var scope = CollectionBudget.open("other-target", 1000)) {
            assertThatThrownBy(this::check).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Collection scope target mismatch");
            assertThat(CollectionBudget.current()).isSameAs(scope.budget());
        }
        assertThat(requests).isEmpty();
        assertThat(CollectionBudget.current()).isNull();
    }

    private DefaultKeycloakManagementHealthProvider provider(int timeout) {
        HealthConfig config = mock(HealthConfig.class);
        HealthConfig.Management management = mock(HealthConfig.Management.class);
        when(config.management()).thenReturn(management);
        when(management.connectTimeoutMs()).thenReturn(timeout);
        when(management.readTimeoutMs()).thenReturn(timeout);
        return new DefaultKeycloakManagementHealthProvider(config, new ObjectMapper());
    }

    private Target target(String managementUrl, Map<String, String> tags) {
        Target target = mock(Target.class);
        when(target.id()).thenReturn(TargetId.of("management-target"));
        when(target.keycloak()).thenReturn(new KeycloakTargetConfiguration("http://unused", "realm", "client", "ref", managementUrl));
        when(target.tags()).thenReturn(tags);
        return target;
    }

    private KeycloakManagementHealthProvider.ManagementHealthResult check() {
        return provider.check(target(baseUrl, Map.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(KeycloakManagementHealthProvider.ManagementHealthResult result, String path) {
        return (Map<String, Object>) result.details().get(path);
    }

    private void assertSafe(KeycloakManagementHealthProvider.ManagementHealthResult result) {
        assertThat(result.toString()).doesNotContain(SECRET, baseUrl, "managementUrl", "bodySnippet", "parseError");
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
        requestHook.accept(path);
        Reply reply = replies.getOrDefault(path, defaultReply);
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            if (headerDelayMs > 0) Thread.sleep(headerDelayMs);
            if (reply.delayMs() > 0 && !reply.delayBody()) {
                Thread.sleep(reply.delayMs());
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (reply.status() >= 300 && reply.status() < 400) {
                exchange.getResponseHeaders().set("Location", baseUrl + "/redirected?token=" + SECRET);
            }
            exchange.sendResponseHeaders(reply.status(), reply.chunked() ? 0 : body.length == 0 ? -1 : body.length);
            if (reply.delayMs() > 0 && reply.delayBody()) {
                exchange.getResponseBody().write(' ');
                exchange.getResponseBody().flush();
                Thread.sleep(reply.delayMs());
            }
            exchange.getResponseBody().write(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // The bounded client is expected to cancel oversized/stalled fixture responses.
        }
    }

    private record Reply(int status, String body, boolean chunked, long delayMs, boolean delayBody) {}
}
