package io.github.keycloakmcp.observability.metrics.prometheus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.observability.metrics.MetricsOperationBudget;

class PrometheusApiClientTest {

    private HttpServer server;
    private String baseUrl;
    private PrometheusApiClient client;
    private final AtomicInteger lastStatus = new AtomicInteger(200);
    private volatile String lastBody;
    private volatile String lastAuth;
    private volatile String lastQuery;
    private volatile boolean chunked;
    private volatile long bodyDelayMs;
    private volatile long headerDelayMs;
    private volatile Runnable beforeHeaders;
    private volatile String redirect;
    private volatile byte[] rawBody;
    private volatile java.util.concurrent.CountDownLatch bodyGate;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final CountDownLatch bodyStarted = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        client = new PrometheusApiClient(new ObjectMapper());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/query", this::handleQuery);
        server.createContext("/api/v1/query_range", this::handleQuery);
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

    @Test void upstreamErrorTextNeverBecomesAnOutputMessage() {
        var result = client.parseBody("{\"status\":\"error\",\"error\":\"private-provider-canary\"}");
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.SERVER_ERROR);
        assertThat(result.message()).doesNotContain("private-provider-canary");
        assertThat(result.series()).isEmpty();
    }

    @Test void partialBackendWarningIsNotACompleteObservation() {
        var result = client.parseBody("{\"status\":\"success\",\"warnings\":[\"private-provider-canary\"],\"data\":{\"resultType\":\"vector\",\"result\":[]}}");
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
        assertThat(result.message()).doesNotContain("private-provider-canary");
    }

    @Test void rejectsMalformedEnvelopesAndAmbiguousJson() {
        for (String body : new String[]{"null", "[]", "{}", "{\"status\":\"success\"}",
                "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":{}}}",
                "{\"status\":\"success\",\"data\":{\"resultType\":\"scalar\",\"result\":[]}}",
                "{\"status\":\"success\",\"status\":\"error\"}", "{} {}"}) {
            assertThat(client.parseBody(body).status()).as(body).isEqualTo(PrometheusApiClient.Status.MALFORMED);
        }
    }

    @Test void invalidTimestampOrLabelTypeCannotBeCoercedToAnObservation() {
        for (String item : new String[]{"{\"metric\":{},\"value\":[\"invalid\",\"0\"]}",
                "{\"metric\":{},\"value\":[-1,\"0\"]}",
                "{\"metric\":{\"scope\":{\"secret\":\"canary\"}},\"value\":[1710000000,\"0\"]}",
                "{\"metric\":{},\"value\":[1710000000,0]}",
                "{\"metric\":{},\"values\":[[1710000000,\"0\"]]}"}) {
            var result = client.parseBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[" + item + "]}}");
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
            assertThat(result.series()).isEmpty();
        }
    }

    @Test void nonFiniteNumbersRemainMissingRatherThanZero() {
        for (String value : new String[]{"NaN", "+Inf", "-Inf", "1e309", "Infinity"}) {
            var result = client.parseBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1710000000,\"" + value + "\"]}]}}");
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OK);
            assertThat(result.series().get(0).samples().get(0).value()).isNull();
        }
    }

    @Test void labelsAndJsonDepthAreBounded() {
        String longLabel = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{\"label\":\"" + "x".repeat(513) + "\"},\"value\":[1710000000,\"0\"]}]}}";
        assertThat(client.parseBody(longLabel).status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
        assertThat(client.parseBody("[".repeat(40) + "0" + "]".repeat(40)).status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
    }

    @Test void boundedParserRejectsUtf8Oversize() {
        assertThat(client.parseBody("é".repeat(PrometheusApiClient.MAX_RESPONSE_BYTES / 2 + 1)).status())
                .isEqualTo(PrometheusApiClient.Status.LIMIT_EXCEEDED);
    }

    @Test void rejectsFixedLengthAndChunkedOversizeResponses() {
        lastBody = "x".repeat(PrometheusApiClient.MAX_RESPONSE_BYTES + 1);
        for (boolean streamed : new boolean[]{false, true}) {
            chunked = streamed;
            var result = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.LIMIT_EXCEEDED);
            assertThat(result.series()).isEmpty();
        }
    }

    @Test void neverFollowsRedirectsOrForwardsCredentials() {
        var calls = new AtomicInteger();
        server.createContext("/unexpected", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(200,-1); exchange.close(); });
        redirect = baseUrl + "/unexpected";
        lastStatus.set(302);
        var result = client.query(baseUrl, "up", MetricsCredentials.bearer("redirect-canary",null,false), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.SERVER_ERROR);
        assertThat(calls).hasValue(0);
        assertThat(result.message()).doesNotContain("redirect-canary", redirect);
    }

    @Test void slowChunkedBodyHasADeadlineAfterHeaders() {
        chunked = true; bodyGate = new java.util.concurrent.CountDownLatch(1); lastBody = "{}";
        try {
            long start = System.nanoTime();
            var result = client.query(baseUrl,"up",MetricsCredentials.none(),Duration.ofSeconds(2),Duration.ofMillis(150));
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
            assertThat(result.message()).isEqualTo("OPERATION_BUDGET_EXCEEDED");
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));
            assertThat(bodyGate.getCount()).isEqualTo(1); // Client returned while the response body is still held.
        } finally { bodyGate.countDown(); }
    }

    @Test void invalidUtf8ResponseIsNotNormalizedIntoAnObservation() {
        byte[] prefix = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{\"label\":\"".getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "\"},\"value\":[1710000000,\"1\"]}]}}".getBytes(StandardCharsets.UTF_8);
        rawBody = new byte[prefix.length+1+suffix.length];
        System.arraycopy(prefix,0,rawBody,0,prefix.length); rawBody[prefix.length]=(byte)0xff;
        System.arraycopy(suffix,0,rawBody,prefix.length+1,suffix.length);
        assertThat(client.query(baseUrl,"up",MetricsCredentials.none(),Duration.ofSeconds(2),Duration.ofSeconds(2)).status())
                .isEqualTo(PrometheusApiClient.Status.MALFORMED);
    }

    @Test void rangeEndpointRequiresMatrixRatherThanInstantVector() {
        lastBody = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";
        assertThat(client.queryRange(baseUrl,"up",java.time.Instant.now().minusSeconds(60),java.time.Instant.now(),
                Duration.ofSeconds(1),MetricsCredentials.none(),Duration.ofSeconds(2),Duration.ofSeconds(2)).status())
                .isEqualTo(PrometheusApiClient.Status.MALFORMED);
    }

    @Test void timestampPrecisionIsPreservedWithoutRoundingMalformedValues() {
        var valid = client.parseBody(vector("1710000000.001", "1"));
        assertThat(valid.series().getFirst().samples().getFirst().timestamp())
                .isEqualTo(Instant.ofEpochMilli(1710000000001L));
        for (String timestamp : new String[]{"1710000000.0001", "1e100", "9223372036854775.808", "-0.001"}) {
            assertThat(client.parseBody(vector(timestamp, "1")).status())
                    .as(timestamp).isEqualTo(PrometheusApiClient.Status.MALFORMED);
        }
    }

    @Test void instantRequestPinsEvaluationTimeAndRejectsFutureSamples() {
        lastBody = vector(String.valueOf(Instant.now().plusSeconds(60).getEpochSecond()), "0");
        var result = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(lastQuery).contains("time=");
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.INCOMPLETE);
        assertThat(result.series()).isEmpty();
    }

    @Test void instantResponseCannotRetainFiniteSubsetOfMissingValues() {
        lastBody = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":["
                + "{\"metric\":{\"instance\":\"a\"},\"value\":[1710000000,\"0\"]},"
                + "{\"metric\":{\"instance\":\"b\"},\"value\":[1710000000,\"NaN\"]}]}}";
        var result = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.INCOMPLETE);
        assertThat(result.series()).isEmpty();
    }

    @Test void rangeAcceptsCompleteInclusiveEvaluationGridNotNecessarilyPointAtEnd() {
        lastBody = matrix("[1710000000,\"0\"],[1710000060,\"2\"],[1710000120,\"4\"]");
        var result = range(1710000000, 1710000130, 60);
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OK);
        assertThat(result.series().getFirst().samples()).hasSize(3);
        assertThat(lastQuery).contains("start=1710000000", "end=1710000130", "step=60");
    }

    @Test void rangeRejectsMissingDuplicateReversedOffGridOutOfWindowAndNonFinitePoints() {
        for (String samples : new String[]{
                "[1710000060,\"0\"],[1710000120,\"0\"]",
                "[1710000000,\"0\"],[1710000120,\"0\"]",
                "[1710000000,\"0\"],[1710000060,\"0\"]",
                "[1710000000,\"0\"],[1710000000,\"0\"],[1710000120,\"0\"]",
                "[1710000120,\"0\"],[1710000060,\"0\"],[1710000000,\"0\"]",
                "[1710000000,\"0\"],[1710000061,\"0\"],[1710000120,\"0\"]",
                "[1709999940,\"0\"],[1710000060,\"0\"],[1710000120,\"0\"]",
                "[1710000000,\"0\"],[1710000060,\"NaN\"],[1710000120,\"0\"]",
                "[1710000000,\"0\"],[1710000060,\"garbage\"],[1710000120,\"0\"]",
                ""}) {
            lastBody = matrix(samples);
            var result = range(1710000000, 1710000120, 60);
            assertThat(result.status()).as(samples).isEqualTo(PrometheusApiClient.Status.INCOMPLETE);
            assertThat(result.series()).isEmpty();
        }
    }

    @Test void invalidOrFutureRangeBoundsAreRejectedWithoutRequest() {
        for (Instant end : new Instant[]{Instant.ofEpochSecond(99), Instant.now().plusSeconds(60)}) {
            var result = client.queryRange(baseUrl, "up", Instant.ofEpochSecond(100), end,
                    Duration.ofSeconds(10), MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
            assertThat(lastQuery).isNull();
        }
        assertThat(client.queryRange(baseUrl, "up", Instant.ofEpochSecond(100), Instant.ofEpochSecond(110),
                Duration.ZERO, MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2)).status())
                .isEqualTo(PrometheusApiClient.Status.MALFORMED);
        assertThat(lastQuery).isNull();
    }

    private PrometheusApiClient.Response range(long start, long end, long step) {
        return client.queryRange(baseUrl, "up", Instant.ofEpochSecond(start), Instant.ofEpochSecond(end),
                Duration.ofSeconds(step), MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private static String vector(String timestamp, String value) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":["
                + timestamp + ",\"" + value + "\"]}]}}";
    }

    private static String matrix(String samples) {
        return "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{},\"values\":["
                + samples + "]}]}}";
    }

    @Test void invalidConfiguredEndpointNeverLeaksItsValue() {
        var result = client.query("http://user:private-url-canary@127.0.0.1:1", "up", MetricsCredentials.none(), Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.MALFORMED);
        assertThat(result.message()).doesNotContain("private-url-canary", "127.0.0.1");
        assertThat(lastAuth).isNull();
    }

    @Test void expiredBudgetMakesNoInstantOrRangeRequest() {
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofMillis(50), clock::get);
        clock.set(Duration.ofMillis(50).toNanos());
        var instant = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2), budget);
        var range = client.queryRange(baseUrl, "up", Instant.ofEpochSecond(100), Instant.ofEpochSecond(110),
                Duration.ofSeconds(10), MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2), budget);
        for (var result : new PrometheusApiClient.Response[]{instant, range}) {
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
            assertThat(result.message()).isEqualTo("OPERATION_BUDGET_EXCEEDED");
            assertThat(result.series()).isEmpty();
        }
        assertThat(requestCount).hasValue(0);
    }

    @Test void nullCredentialsFailClosedWithoutAnonymousFallback() {
        var instant = client.query(baseUrl, "up", null, Duration.ofSeconds(2), Duration.ofSeconds(2));
        var range = client.queryRange(baseUrl, "up", Instant.ofEpochSecond(100), Instant.ofEpochSecond(110),
                Duration.ofSeconds(10), null, Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThat(instant.status()).isEqualTo(PrometheusApiClient.Status.UNAUTHORIZED);
        assertThat(range.status()).isEqualTo(PrometheusApiClient.Status.UNAUTHORIZED);
        assertThat(instant.message()).isEqualTo("Metrics credentials unavailable");
        assertThat(requestCount).hasValue(0);
    }

    @Test void preexistingInterruptionIsNotConsumedAndMakesNoRequest() {
        try {
            Thread.currentThread().interrupt();
            var result = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
            assertThat(result.message()).isEqualTo("OPERATION_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(requestCount).hasValue(0);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void sharedBudgetCannotBeResetByTheNextRequest() {
        lastBody = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofSeconds(2), clock::get);
        assertThat(client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2), budget).status())
                .isEqualTo(PrometheusApiClient.Status.EMPTY);
        clock.set(Duration.ofSeconds(2).toNanos());
        assertThat(client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2), budget).status())
                .isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
        assertThat(requestCount).hasValue(1);
    }

    @Test void parsedResultArrivingAfterDeadlineIsDiscarded() throws Exception {
        lastBody = vector("1710000000", "1");
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofSeconds(2), clock::get);
        ObjectMapper original = spy(new ObjectMapper());
        ObjectMapper parsing = spy(new ObjectMapper());
        doReturn(parsing).when(original).copy();
        doAnswer(invocation -> {
            Object parsed = invocation.callRealMethod();
            clock.set(Duration.ofSeconds(2).toNanos());
            return parsed;
        }).when(parsing).readTree(any(byte[].class));
        var boundedClient = new PrometheusApiClient(original);
        var result = boundedClient.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2), budget);
        assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
        assertThat(result.message()).isEqualTo("OPERATION_BUDGET_EXCEEDED");
        assertThat(result.series()).isEmpty();
        assertThat(requestCount).hasValue(1);
    }

    @Test void requestTimeoutDoesNotConsumeASeparateLongerOperationBudget() {
        chunked = true; bodyGate = new CountDownLatch(1); lastBody = "{}";
        try {
            var budget = MetricsOperationBudget.start(Duration.ofSeconds(5));
            var result = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofMillis(100), budget);
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.TIMEOUT);
            assertThat(budget.exhausted()).isFalse();
            assertThat(bodyGate.getCount()).isEqualTo(1);
        } finally { bodyGate.countDown(); }
    }

    @Test void delayedHeadersAndStalledBodyShareOneDeadlineWithoutWaitingForServerClose() {
        headerDelayMs = 150; chunked = true; bodyGate = new CountDownLatch(1); lastBody = "{}";
        try {
            long start = System.nanoTime();
            var result = client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2),
                    MetricsOperationBudget.start(Duration.ofMillis(300)));
            assertThat(result.status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
            assertThat(result.message()).isEqualTo("OPERATION_BUDGET_EXCEEDED");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(bodyGate.getCount()).isEqualTo(1);
        } finally { bodyGate.countDown(); }
    }

    @Test void deadlineExpiredAtHeadersIsNotResetToItsSendTimeRemainder() throws Exception {
        chunked = true; bodyGate = new CountDownLatch(1); lastBody = "{}";
        var clock = new AtomicLong();
        var budget = new MetricsOperationBudget(Duration.ofSeconds(10), clock::get);
        var headersReady = new CountDownLatch(1);
        beforeHeaders = () -> {
            clock.set(Duration.ofSeconds(10).toNanos());
            headersReady.countDown();
        };
        var result = new AtomicReference<PrometheusApiClient.Response>();
        var finished = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            try {
                result.set(client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(10),
                        Duration.ofSeconds(10), budget));
            } finally {
                finished.countDown();
            }
        });
        try {
            caller.start();
            assertThat(headersReady.await(2, TimeUnit.SECONDS)).isTrue();
            // A captured send-time timeout would still be 10 seconds. The shared deadline
            // is already zero at headers, so completion must not wait for this held body.
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get().status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
            assertThat(result.get().message()).isEqualTo("OPERATION_BUDGET_EXCEEDED");
            assertThat(result.get().series()).isEmpty();
            assertThat(bodyGate.getCount()).isEqualTo(1);
            assertThat(requestCount).hasValue(1);
        } finally {
            bodyGate.countDown();
            caller.interrupt();
            caller.join(2000);
        }
    }

    @Test void interruptedInFlightRequestReturnsWithoutClearingTheSignal() throws Exception {
        chunked = true; bodyGate = new CountDownLatch(1); lastBody = "{}";
        var result = new AtomicReference<PrometheusApiClient.Response>();
        var interrupted = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            result.set(client.query(baseUrl, "up", MetricsCredentials.none(), Duration.ofSeconds(2), Duration.ofSeconds(2)));
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        try {
            caller.start();
            assertThat(bodyStarted.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(1000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(result.get().status()).isEqualTo(PrometheusApiClient.Status.OPERATION_ABORTED);
            assertThat(result.get().message()).isEqualTo("OPERATION_INTERRUPTED");
            assertThat(interrupted).isTrue();
            assertThat(bodyGate.getCount()).isEqualTo(1);
        } finally {
            bodyGate.countDown();
            caller.interrupt();
            caller.join(2000);
        }
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        lastAuth = exchange.getRequestHeaders().getFirst("Authorization");
        lastQuery = exchange.getRequestURI().getRawQuery();
        byte[] bytes = rawBody == null ? (lastBody == null ? "" : lastBody).getBytes(StandardCharsets.UTF_8) : rawBody;
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (redirect != null) exchange.getResponseHeaders().add("Location", redirect);
        if (headerDelayMs > 0) {
            try { Thread.sleep(headerDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (beforeHeaders != null) beforeHeaders.run();
        exchange.sendResponseHeaders(lastStatus.get(), chunked ? 0 : bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            if (bodyGate != null) {
                os.write(' '); os.flush();
                bodyStarted.countDown();
                try { bodyGate.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (bodyDelayMs > 0) {
                os.write(' '); os.flush();
                try { Thread.sleep(bodyDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            os.write(bytes);
        }
    }
}
