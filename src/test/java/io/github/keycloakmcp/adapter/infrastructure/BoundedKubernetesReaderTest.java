package io.github.keycloakmcp.adapter.infrastructure;

import static io.github.keycloakmcp.adapter.infrastructure.BoundedKubernetesReader.*;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.github.keycloakmcp.collection.CollectionBudget;

/** Uses real pinned Fabric8 transport against disposable loopback HTTP, never a real cluster. */
class BoundedKubernetesReaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SAFE = "Infrastructure response unavailable or incomplete";
    private static final String CANARY = "private-response-canary";
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ExecutorService serverTasks;
    private KubernetesClient client;
    private volatile Responder responder;
    private record Request(String method, String path, String token) { }
    @FunctionalInterface private interface Responder { void respond(HttpExchange exchange) throws Exception; }

    @BeforeEach void setup() throws IOException {
        responder = exchange -> reply(exchange, 200, serviceList());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverTasks = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverTasks);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(), exchange.getRequestHeaders().getFirst("Authorization")));
            try { responder.respond(exchange); }
            catch (Exception ignored) { exchange.close(); }
        });
        server.start();
        client = client("fixture-a", 2_000);
    }

    @AfterEach void cleanup() {
        if (client != null) client.close();
        if (server != null) server.stop(0);
        if (serverTasks != null) serverTasks.close();
    }

    @Test void validListUsesExplicitNamespaceAndPinnedCredentialsWithoutDefaultFallback() {
        assertThat(client.getNamespace()).isEqualTo("client-default");
        var services = list(client, SERVICES, "iam");
        assertThat(services).hasSize(1);
        assertThat(services.getFirst().getMetadata().getUid()).isEqualTo("uid-one");
        assertThat(services.getFirst().getSpec().getPorts().getFirst().getTargetPort().getStrVal()).isEqualTo("management");
        assertThat(requests).containsExactly(new Request("GET", "/api/v1/namespaces/iam/services?limit=501", "Bearer fixture-a"));
        assertThat(client.getHttpClient().isClosed()).isFalse();
    }

    @Test void separatePinnedClientsNeverReuseAnotherTargetsCredentials() {
        try (KubernetesClient other = client("fixture-b", 2_000)) {
            list(client, SERVICES, "iam", 100);
            list(other, SERVICES, "iam", 100);
        }
        assertThat(requests).extracting(Request::token).containsExactly("Bearer fixture-a", "Bearer fixture-b");
        assertThat(requests).extracting(Request::path).containsOnly("/api/v1/namespaces/iam/services?limit=101");
        assertThat(client.getHttpClient().isClosed()).isFalse();
    }

    @Test void explicitEmptyListIsObservedAbsence() {
        ObjectNode root = tree(serviceList());
        root.putArray("items");
        responder = exchange -> reply(exchange, 200, root.toString());
        assertThat(list(client, SERVICES, "iam")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-items", "null-items", "object-items", "missing-kind", "wrong-kind", "missing-api", "wrong-api", "missing-metadata", "null-metadata", "continue", "continue-whitespace", "continue-null", "remaining", "negative-remaining", "fractional-remaining", "null-remaining", "null-item", "wrong-item-kind", "null-item-kind", "wrong-item-api", "null-item-api", "foreign-namespace", "missing-namespace", "missing-uid", "bad-uid", "missing-name", "duplicate-name", "duplicate-uid", "labels-null", "labels-number", "too-many-labels", "too-many-items", "fractional-port", "string-port", "null-resource-metadata"})
    void malformedEnvelopesAndResourcesNeverSupplyPartialOrEmptyEvidence(String malformed) {
        ObjectNode root = tree(serviceList());
        ObjectNode item = (ObjectNode) root.path("items").get(0);
        ObjectNode meta = (ObjectNode) item.path("metadata");
        ObjectNode listMeta = (ObjectNode) root.path("metadata");
        switch (malformed) {
            case "missing-items" -> root.remove("items");
            case "null-items" -> root.putNull("items");
            case "object-items" -> root.putObject("items");
            case "missing-kind" -> root.remove("kind");
            case "wrong-kind" -> root.put("kind", "PodList");
            case "missing-api" -> root.remove("apiVersion");
            case "wrong-api" -> root.put("apiVersion", "apps/v1");
            case "missing-metadata" -> root.remove("metadata");
            case "null-metadata" -> root.putNull("metadata");
            case "continue" -> listMeta.put("continue", CANARY);
            case "continue-whitespace" -> listMeta.put("continue", " ");
            case "continue-null" -> listMeta.putNull("continue");
            case "remaining" -> listMeta.put("remainingItemCount", 1);
            case "negative-remaining" -> listMeta.put("remainingItemCount", -1);
            case "fractional-remaining" -> listMeta.put("remainingItemCount", 0.5);
            case "null-remaining" -> listMeta.putNull("remainingItemCount");
            case "null-item" -> root.putArray("items").addNull();
            case "wrong-item-kind" -> item.put("kind", "Pod");
            case "null-item-kind" -> item.putNull("kind");
            case "wrong-item-api" -> item.put("apiVersion", "apps/v1");
            case "null-item-api" -> item.putNull("apiVersion");
            case "foreign-namespace" -> meta.put("namespace", "foreign");
            case "missing-namespace" -> meta.remove("namespace");
            case "missing-uid" -> meta.remove("uid");
            case "bad-uid" -> meta.put("uid", "../" + CANARY);
            case "missing-name" -> meta.remove("name");
            case "duplicate-name" -> {
                var other = item.deepCopy(); other.withObject("metadata").put("uid", "other");
                root.withArray("items").add(other);
            }
            case "duplicate-uid" -> {
                var other = item.deepCopy(); other.withObject("metadata").put("name", "other");
                root.withArray("items").add(other);
            }
            case "labels-null" -> meta.putNull("labels");
            case "labels-number" -> meta.putObject("labels").put("app", 1);
            case "too-many-labels" -> { var labels = meta.putObject("labels"); for (int i = 0; i < 257; i++) labels.put("l" + i, "x"); }
            case "too-many-items" -> { var items = root.putArray("items"); for (int i = 0; i < 501; i++) items.add(item); }
            case "fractional-port" -> ((ObjectNode) item.path("spec").path("ports").get(0)).put("port", 9000.5);
            case "string-port" -> ((ObjectNode) item.path("spec").path("ports").get(0)).put("port", "9000");
            case "null-resource-metadata" -> item.putNull("metadata");
            default -> throw new AssertionError(malformed);
        }
        responder = exchange -> reply(exchange, 200, root.toString());
        assertSafeFailure(() -> list(client, SERVICES, "iam"));
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"typed", "generic"})
    void typedCollectionEnvelopeSuppliesOnlyAbsentItemTypeMeta(String model) {
        ObjectNode root = tree(serviceList());
        ObjectNode item = (ObjectNode) root.path("items").get(0);
        item.remove(List.of("apiVersion", "kind"));
        if (model.equals("generic")) {
            root.put("apiVersion", "monitoring.coreos.com/v1");
            root.put("kind", "ServiceMonitorList");
        }
        responder = exchange -> reply(exchange, 200, root.toString());
        var result = model.equals("typed") ? list(client, SERVICES, "iam") : list(client, SERVICE_MONITORS, "iam");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getApiVersion()).isEqualTo(model.equals("typed") ? "v1" : "monitoring.coreos.com/v1");
        assertThat(result.getFirst().getKind()).isEqualTo(model.equals("typed") ? "Service" : "ServiceMonitor");
    }

    @ParameterizedTest @ValueSource(strings = {"apiVersion", "kind"})
    void standaloneGetNeverDefaultsMissingTypeMeta(String field) {
        ObjectNode item = (ObjectNode) tree(serviceList()).path("items").get(0);
        item.remove(field);
        responder = exchange -> reply(exchange, 200, item.toString());
        assertSafeFailure(() -> get(client, SERVICES, "iam", "service-one"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate-fields", "trailing-document", "depth", "string", "number", "array-size", "not-object", "invalid-json"})
    void jsonParserLimitsAndStrictnessPrecedeModelDefaults(String malformed) {
        String raw = switch (malformed) {
            case "duplicate-fields" -> serviceList().replace("\"items\":", "\"items\":[],\"items\":");
            case "trailing-document" -> serviceList() + " {}";
            case "depth" -> "{\"extra\":" + "[".repeat(34) + "0" + "]".repeat(34) + "}";
            case "string" -> "{\"extra\":\"" + "x".repeat(9000) + "\"}";
            case "number" -> "{\"extra\":" + "1".repeat(129) + "}";
            case "array-size" -> "{\"extra\":[" + "0,".repeat(1024) + "0]}";
            case "not-object" -> "[]";
            case "invalid-json" -> "{" + CANARY;
            default -> throw new AssertionError(malformed);
        };
        responder = exchange -> reply(exchange, 200, raw);
        assertSafeFailure(() -> list(client, SERVICES, "iam"));
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 404})
    void statusErrorsExposeOnlySafeStatusWithoutBodyOrUrl(int code) {
        responder = exchange -> reply(exchange, code, CANARY);
        assertThatThrownBy(() -> list(client, SERVICES, "iam")).isInstanceOfSatisfying(KubernetesClientException.class, e -> {
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getMessage()).isEqualTo(SAFE);
            assertThat(e.getCause()).isNull();
        });
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest @ValueSource(ints = {201, 204, 429, 500, 503})
    void unsuccessfulResponsesAreNotParsedOrRetried(int code) {
        responder = exchange -> reply(exchange, code, CANARY);
        assertSafeFailure(() -> list(client, SERVICES, "iam"));
        assertThat(requests).hasSize(1);
    }

    @Test void onlyExactGetNotFoundReturnsNull() {
        responder = exchange -> reply(exchange, 404, CANARY);
        assertThat(get(client, SERVICES, "iam", "service-one")).isNull();
        assertThat(requests).containsExactly(new Request("GET", "/api/v1/namespaces/iam/services/service-one", "Bearer fixture-a"));
    }

    @Test void getValidatesExactIdentityAndNeverAcceptsAnotherName() {
        responder = exchange -> reply(exchange, 200, tree(serviceList()).path("items").get(0).toString());
        assertThat(get(client, SERVICES, "iam", "service-one").getMetadata().getName()).isEqualTo("service-one");
        assertSafeFailure(() -> get(client, SERVICES, "iam", "another"));
    }

    @Test void validDeploymentQuantitiesConvertWithoutRelaxingWireDocumentChecks() {
        String deployment = """
                {"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"keycloak","namespace":"iam","uid":"uid-deployment"},
                 "spec":{"replicas":3,"template":{"metadata":{"labels":{"app":"keycloak"}},"spec":{"containers":[
                   {"name":"keycloak","resources":{"requests":{"cpu":"500m","memory":"1Gi"},"limits":{"cpu":"1","memory":"2Gi"}}}]}}},
                 "status":{"replicas":3,"readyReplicas":3,"availableReplicas":3}}
                """;
        responder = exchange -> reply(exchange, 200, deployment);
        var observed = get(client, DEPLOYMENTS, "iam", "keycloak");
        assertThat(observed.getSpec().getReplicas()).isEqualTo(3);
        var resources = observed.getSpec().getTemplate().getSpec().getContainers().getFirst().getResources();
        assertThat(resources.getRequests().get("cpu").toString()).isEqualTo("500m");
        assertThat(resources.getRequests().get("memory").toString()).isEqualTo("1Gi");
        assertThat(resources.getLimits().get("memory").toString()).isEqualTo("2Gi");
        responder = exchange -> reply(exchange, 200, deployment + " {}");
        assertSafeFailure(() -> get(client, DEPLOYMENTS, "iam", "keycloak"));
    }

    @Test void invalidCallerScopeAndResourceVersionNeverSendRequests() {
        assertSafeFailure(() -> list(client, SERVICES, null));
        assertSafeFailure(() -> list(client, SERVICES, "../other"));
        assertSafeFailure(() -> list(client, NODES, "iam"));
        assertSafeFailure(() -> list(client, SERVICES, "iam", 0));
        assertSafeFailure(() -> list(client, SERVICES, "iam", 501));
        assertSafeFailure(() -> get(client, SERVICES, "iam", "../../secrets"));
        assertSafeFailure(() -> keycloaks("other.example/v1"));
        assertSafeFailure(() -> keycloaks("k8s.keycloak.org/v1/../../secrets"));
        assertThat(requests).isEmpty();
    }

    @Test void apiDiscoveryUsesOnlyFixedEndpointsAndPreservesObservedValues() {
        responder = exchange -> reply(exchange, 200, exchange.getRequestURI().getPath().equals("/apis")
                ? "{\"kind\":\"APIGroupList\",\"apiVersion\":\"v1\",\"groups\":[{\"name\":\"apps\",\"versions\":[{\"version\":\"v1\",\"groupVersion\":\"apps/v1\"}],\"preferredVersion\":{\"version\":\"v1\",\"groupVersion\":\"apps/v1\"}}]}"
                : "{\"gitVersion\":\"v1.30.1+fixture\"}");
        assertThat(apiGroups(client)).extracting(group -> group.getName()).containsExactly("apps");
        assertThat(version(client)).isEqualTo("v1.30.1+fixture");
        assertThat(requests).extracting(Request::path).containsExactly("/apis", "/version");
    }

    @Test void groupsAndVersionCannotDefaultMissingData() {
        responder = exchange -> reply(exchange, 200, "{\"apiVersion\":\"v1\",\"kind\":\"APIGroupList\"}");
        assertSafeFailure(() -> apiGroups(client));
        responder = exchange -> reply(exchange, 200, "{\"gitVersion\":123}");
        assertSafeFailure(() -> version(client));
    }

    @Test void unrelatedCustomApiVersionsNeedNotFollowKubernetesReleaseNaming() {
        responder = exchange -> reply(exchange, 200,
                "{\"kind\":\"APIGroupList\",\"apiVersion\":\"v1\",\"groups\":[{\"name\":\"custom.example.test\",\"versions\":[{\"version\":\"foo1\",\"groupVersion\":\"custom.example.test/foo1\"}]}]}");
        assertThat(apiGroups(client).getFirst().getVersions().getFirst().getVersion()).isEqualTo("foo1");
    }

    @ParameterizedTest @ValueSource(strings = {"a..b", "a.-b", "a-.b", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test"})
    void malformedDnsGroupNamesAreNotDiscoveryEvidence(String name) {
        responder = exchange -> reply(exchange, 200,
                "{\"kind\":\"APIGroupList\",\"apiVersion\":\"v1\",\"groups\":[{\"name\":\"" + name
                        + "\",\"versions\":[{\"version\":\"v1\",\"groupVersion\":\"" + name + "/v1\"}]}]}");
        assertSafeFailure(() -> apiGroups(client));
    }

    @ParameterizedTest @ValueSource(ints = {301, 302, 307, 308})
    void redirectsNeverReachTheDestinationOrForwardCredentials(int code) throws IOException {
        AtomicInteger destinationRequests = new AtomicInteger();
        HttpServer destination = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        destination.createContext("/", exchange -> { destinationRequests.incrementAndGet(); reply(exchange, 200, serviceList()); });
        destination.start();
        try {
            responder = exchange -> {
                exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + destination.getAddress().getPort() + "/foreign");
                reply(exchange, code, CANARY);
            };
            assertSafeFailure(() -> list(client, SERVICES, "iam"));
            assertThat(destinationRequests).hasValue(0);
            assertThat(requests).hasSize(1);
        } finally { destination.stop(0); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void byteCapAppliesToDeclaredAndChunkedBodies(boolean chunked) {
        responder = exchange -> {
            byte[] bytes = " ".repeat(MAX_BYTES + 1).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, chunked ? 0 : bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        };
        assertSafeFailure(() -> list(client, SERVICES, "iam"));
    }

    @Test void stalledBodyStopsAtTheSingleRequestDeadlineAndLeavesClientReusable() throws Exception {
        client.getConfiguration().setRequestTimeout(250);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch headers = new CountDownLatch(1);
        responder = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            headers.countDown();
            try { release.await(3, TimeUnit.SECONDS); } finally { exchange.close(); }
        };
        long start = System.nanoTime();
        try {
            assertSafeFailure(() -> list(client, SERVICES, "iam"));
            assertThat(headers.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        } finally { release.countDown(); }
        client.getConfiguration().setRequestTimeout(2000);
        responder = exchange -> reply(exchange, 200, serviceList());
        assertThat(list(client, SERVICES, "iam")).hasSize(1);
    }

    @Test void preexistingInterruptDoesNotSendRequestsOrClearTheFlag() {
        Thread.currentThread().interrupt();
        try {
            assertSafeFailure(() -> list(client, SERVICES, "iam"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(requests).isEmpty();
        } finally { Thread.interrupted(); }
    }

    @Test void expiredCollectionBudgetStopsBeforeAnyRequestAndScopeIsRestored() {
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(100), clock::get);
        clock.set(100_000_000);
        try (var scope = CollectionBudget.open("a", budget)) {
            assertThatThrownBy(() -> list(client, SERVICES, "iam")).isInstanceOf(CollectionBudget.Aborted.class).hasMessage("OPERATION_BUDGET_EXCEEDED");
            assertThat(requests).isEmpty();
        }
        assertThat(CollectionBudget.current()).isNull();
        assertThat(list(client, SERVICES, "iam")).hasSize(1);
    }

    @Test void lateNotFoundCannotBecomeObservedAbsenceAndNextQueryDoesNotStart() {
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofSeconds(1), clock::get);
        responder = exchange -> { clock.set(1_000_000_000); reply(exchange, 404, CANARY); };
        try (var scope = CollectionBudget.open("a", budget)) {
            assertThatThrownBy(() -> get(client, SERVICES, "iam", "service-one")).isInstanceOf(CollectionBudget.Aborted.class).hasMessage("OPERATION_BUDGET_EXCEEDED");
            assertThatThrownBy(() -> list(client, SERVICES, "iam")).isInstanceOf(CollectionBudget.Aborted.class);
            assertThat(requests).hasSize(1);
        }
    }

    @Test void sequentialReadsShareRemainingCollectionTimeInsteadOfResettingIt() {
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofSeconds(1), clock::get);
        responder = exchange -> { clock.addAndGet(500_000_000); reply(exchange, 200, serviceList()); };
        try (var scope = CollectionBudget.open("a", budget)) {
            assertThat(list(client, SERVICES, "iam")).hasSize(1);
            assertThatThrownBy(() -> list(client, SERVICES, "iam")).isInstanceOf(CollectionBudget.Aborted.class);
            assertThatThrownBy(() -> list(client, SERVICES, "iam")).isInstanceOf(CollectionBudget.Aborted.class);
            assertThat(requests).hasSize(2);
        }
    }

    @Test void parentBudgetCapsStalledBodyWithoutStartingANewRequestWindow() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        responder = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { release.await(3, TimeUnit.SECONDS); } finally { exchange.close(); }
        };
        long start = System.nanoTime();
        try (var scope = CollectionBudget.open("a", 150)) {
            assertThatThrownBy(() -> list(client, SERVICES, "iam")).isInstanceOf(CollectionBudget.Aborted.class).hasMessage("OPERATION_BUDGET_EXCEEDED");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(requests).hasSize(1);
        } finally { release.countDown(); }
        assertThat(client.getHttpClient().isClosed()).isFalse();
    }

    private KubernetesClient client(String token, int timeout) {
        Config config = Config.empty();
        config.setMasterUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setNamespace("client-default");
        config.setOauthToken(token);
        config.setRequestTimeout(timeout);
        config.setRequestRetryBackoffLimit(0);
        config.setAutoConfigure(false);
        return new KubernetesClientBuilder().withConfig(config)
                .withHttpClientFactory(new RedirectRejectingHttpClientFactory()).build();
    }

    private static ObjectNode tree(String value) {
        try { return (ObjectNode) JSON.readTree(value); }
        catch (IOException e) { throw new AssertionError(e); }
    }

    private static void assertSafeFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(IllegalStateException.class).hasMessage(SAFE).hasNoCause();
    }

    private static String serviceList() {
        return """
                {"apiVersion":"v1","kind":"ServiceList","metadata":{},"items":[
                  {"apiVersion":"v1","kind":"Service","metadata":{"name":"service-one","namespace":"iam","uid":"uid-one"},
                   "spec":{"selector":{"app":"keycloak"},"ports":[{"name":"metrics","port":9000,"targetPort":"management"}]}}]}
                """;
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        try (var out = exchange.getResponseBody()) { if (status != 204) out.write(bytes); }
    }
}
