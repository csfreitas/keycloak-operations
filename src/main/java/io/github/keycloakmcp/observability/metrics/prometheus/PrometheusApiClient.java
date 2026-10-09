package io.github.keycloakmcp.observability.metrics.prometheus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;

import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.http.BoundedBodyHandler;
import io.github.keycloakmcp.observability.metrics.MetricSample;
import io.github.keycloakmcp.observability.metrics.MetricSeries;
import io.github.keycloakmcp.observability.metrics.MetricsTemporalValidation;
import io.github.keycloakmcp.observability.metrics.MetricsOperationBudget;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Minimal Prometheus HTTP API client using JDK {@link HttpClient}.
 * Never logs bearer tokens or passwords.
 */
@ApplicationScoped
public class PrometheusApiClient {

    private static final Logger LOG = Logger.getLogger(PrometheusApiClient.class);
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_LABELS = 64;

    public enum Status {
        OK,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER_ERROR,
        TIMEOUT,
        OPERATION_ABORTED,
        MALFORMED,
        NETWORK_ERROR,
        LIMIT_EXCEEDED,
        INCOMPLETE,
        EMPTY
    }

    public record Response(Status status, String message, List<MetricSeries> series) {
        public static Response of(Status status, String message) {
            return new Response(status, message, List.of());
        }
    }

    private final ObjectMapper objectMapper;

    @Inject
    public PrometheusApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.objectMapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxStringLength(8192).maxNumberLength(128).build());
    }

    /** CDI proxy constructor. */
    protected PrometheusApiClient() {
        this.objectMapper = null;
    }

    public Response query(String baseUrl, String promQl, MetricsCredentials credentials,
            Duration connectTimeout, Duration readTimeout) {
        return query(baseUrl, promQl, credentials, connectTimeout, readTimeout, MetricsOperationBudget.start(readTimeout));
    }

    public Response query(String baseUrl, String promQl, MetricsCredentials credentials,
            Duration connectTimeout, Duration readTimeout, MetricsOperationBudget budget) {
        Objects.requireNonNull(budget, "Metrics operation budget is required");
        if (budget.exhausted()) return aborted(budget);
        Instant evaluationTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Response response = execute(baseUrl, "/api/v1/query",
                Map.of("query", promQl, "time", String.valueOf(evaluationTime.getEpochSecond())),
                credentials, connectTimeout, readTimeout, budget);
        if (budget.exhausted()) return aborted(budget);
        if (response.status() != Status.OK) return response;
        String failure = MetricsTemporalValidation.instantFailure(response.series(), evaluationTime);
        if (budget.exhausted()) return aborted(budget);
        return failure == null ? response : Response.of(Status.INCOMPLETE, failure);
    }

    public Response queryRange(
            String baseUrl,
            String promQl,
            Instant start,
            Instant end,
            Duration step,
            MetricsCredentials credentials,
            Duration connectTimeout,
            Duration readTimeout) {
        return queryRange(baseUrl, promQl, start, end, step, credentials, connectTimeout, readTimeout,
                MetricsOperationBudget.start(readTimeout));
    }

    public Response queryRange(
            String baseUrl,
            String promQl,
            Instant start,
            Instant end,
            Duration step,
            MetricsCredentials credentials,
            Duration connectTimeout,
            Duration readTimeout,
            MetricsOperationBudget budget) {
        Objects.requireNonNull(budget, "Metrics operation budget is required");
        if (budget.exhausted()) return aborted(budget);
        if (start == null || end == null || step == null || start.isBefore(Instant.EPOCH)
                || end.isBefore(start) || end.isAfter(Instant.now()) || step.isNegative() || step.isZero()) {
            return malformed();
        }
        Instant requestedStart = start.truncatedTo(ChronoUnit.SECONDS);
        Instant requestedEnd = end.truncatedTo(ChronoUnit.SECONDS);
        Duration requestedStep = Duration.ofSeconds(Math.max(1, step.toSeconds()));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", promQl);
        params.put("start", String.valueOf(requestedStart.getEpochSecond()));
        params.put("end", String.valueOf(requestedEnd.getEpochSecond()));
        params.put("step", String.valueOf(requestedStep.toSeconds()));
        Response response = execute(baseUrl, "/api/v1/query_range", params, credentials, connectTimeout, readTimeout, budget);
        if (budget.exhausted()) return aborted(budget);
        if (response.status() != Status.OK) return response;
        String failure = MetricsTemporalValidation.rangeFailure(response.series(), requestedStart, requestedEnd, requestedStep);
        if (budget.exhausted()) return aborted(budget);
        return failure == null ? response : Response.of(Status.INCOMPLETE, failure);
    }

    private Response execute(
            String baseUrl,
            String path,
            Map<String, String> params,
            MetricsCredentials credentials,
            Duration connectTimeout,
            Duration readTimeout,
            MetricsOperationBudget budget) {
        if (budget.exhausted()) return aborted(budget);
        if (credentials == null) return Response.of(Status.UNAUTHORIZED, "Metrics credentials unavailable");
        if (baseUrl == null || baseUrl.isBlank()) {
            return Response.of(Status.NOT_FOUND, "metrics endpoint not configured");
        }
        String url = joinUrl(baseUrl.trim(), path) + "?" + encodeParams(params);
        MetricsCredentials creds = credentials;
        HttpClient client = null;

        try {
            Duration connectionLimit = budget.cap(connectTimeout);
            if (connectionLimit.isZero()) return aborted(budget);
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .connectTimeout(connectionLimit)
                    .followRedirects(HttpClient.Redirect.NEVER);
            if (creds.trustInsecure()) {
                clientBuilder.sslContext(insecureSslContext());
            }
            URI uri = URI.create(url);
            URI endpoint = URI.create(baseUrl.trim());
            if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                    || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null) {
                return Response.of(Status.MALFORMED, "Invalid configured metrics endpoint");
            }
            client = clientBuilder.build();
            Duration requestLimit = budget.cap(readTimeout);
            if (requestLimit.isZero()) return aborted(budget);
            HttpRequest.Builder req = HttpRequest.newBuilder(uri)
                    .timeout(requestLimit)
                    .GET()
                    .header("Accept", "application/json");
            applyAuth(req, creds);

            if (budget.exhausted()) return aborted(budget);
            HttpResponse<byte[]> httpResponse = client.send(req.build(),
                    new BoundedBodyHandler(MAX_RESPONSE_BYTES, readTimeout, budget::remaining));
            if (budget.exhausted()) return aborted(budget);
            int code = httpResponse.statusCode();
            if (code == 401) {
                return Response.of(Status.UNAUTHORIZED, "Unauthorized (401)");
            }
            if (code == 403) {
                return Response.of(Status.FORBIDDEN, "Forbidden (403)");
            }
            if (code == 404) {
                return Response.of(Status.NOT_FOUND, "Not found (404)");
            }
            if (code == 429) {
                return Response.of(Status.RATE_LIMITED, "Rate limited (429)");
            }
            if (code >= 500) {
                return Response.of(Status.SERVER_ERROR, "Server error (" + code + ")");
            }
            if (code < 200 || code >= 300) {
                return Response.of(Status.SERVER_ERROR, "Unexpected HTTP status " + code);
            }
            Response parsed = parseBody(httpResponse.body(), path.equals("/api/v1/query_range") ? "matrix" : "vector");
            return budget.exhausted() ? aborted(budget) : parsed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return aborted(budget);
        } catch (java.net.http.HttpTimeoutException e) {
            if (budget.exhausted()) return aborted(budget);
            LOG.debugf("Prometheus query timed out for path=%s", path);
            return Response.of(Status.TIMEOUT, "Request timed out");
        } catch (Exception e) {
            if (budget.exhausted()) return aborted(budget);
            if (BoundedBodyHandler.isLimitFailure(e)) return Response.of(Status.LIMIT_EXCEEDED, "Response limit exceeded");
            if (BoundedBodyHandler.isTimeoutFailure(e)) return Response.of(Status.TIMEOUT, "Response body timed out");
            LOG.debugf("Prometheus query failed for controlled path=%s", path);
            return Response.of(Status.NETWORK_ERROR, "Metrics backend request failed");
        } finally {
            // No graceful close/await on the caller thread after an expired operation.
            // Body cancellation plus shutdownNow requests cancellation of any transport still active.
            if (client != null) client.shutdownNow();
        }
    }

    private static Response aborted(MetricsOperationBudget budget) {
        return Response.of(Status.OPERATION_ABORTED, budget.reason());
    }

    Response parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Response.of(Status.MALFORMED, "Empty response body");
        }
        if (body.length() > MAX_RESPONSE_BYTES) {
            return Response.of(Status.LIMIT_EXCEEDED, "Response limit exceeded");
        }
        return parseBody(body.getBytes(StandardCharsets.UTF_8), null);
    }

    private Response parseBody(byte[] body, String expectedType) {
        if (body == null || body.length == 0) return malformed();
        if (body.length > MAX_RESPONSE_BYTES) {
            return Response.of(Status.LIMIT_EXCEEDED, "Response limit exceeded");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject() || !root.path("status").isTextual()) return malformed();
            String status = text(root, "status");
            if (!"success".equals(status)) {
                return Response.of(Status.SERVER_ERROR, "Metrics backend reported an error");
            }
            if (root.has("warnings") && (!root.path("warnings").isArray() || !root.path("warnings").isEmpty())) {
                return Response.of(Status.MALFORMED, "Metrics backend reported incomplete results");
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isObject()) return malformed();
            String resultType = text(data, "resultType");
            if (!"vector".equals(resultType) && !"matrix".equals(resultType)) return malformed();
            if (expectedType != null && !expectedType.equals(resultType)) return malformed();
            JsonNode result = data.get("result");
            if (result == null || !result.isArray()) return malformed();
            if (result.isEmpty()) {
                return new Response(Status.EMPTY, "Empty result", List.of());
            }
            List<MetricSeries> series = new ArrayList<>();
            for (JsonNode item : result) {
                if (!item.isObject() || !item.path("metric").isObject()) return malformed();
                Map<String, String> labels = readLabels(item.get("metric"));
                String name = labels.getOrDefault("__name__", "value");
                List<MetricSample> samples = new ArrayList<>();
                if ("matrix".equals(resultType)) {
                    JsonNode values = item.get("values");
                    if (values != null && values.isArray() && !item.has("value")) {
                        for (JsonNode pair : values) {
                            MetricSample sample = parsePair(pair, labels);
                            if (sample == null) return malformed();
                            samples.add(sample);
                        }
                    } else return malformed();
                } else {
                    if (item.has("values")) return malformed();
                    MetricSample sample = parsePair(item.get("value"), labels);
                    if (sample == null) return malformed();
                    samples.add(sample);
                }
                series.add(new MetricSeries(name, labels, samples));
            }
            return new Response(Status.OK, "ok", List.copyOf(series));
        } catch (Exception e) {
            LOG.debug("Metrics response failed bounded JSON/schema validation");
            return Response.of(Status.MALFORMED, "Malformed JSON");
        }
    }

    private static Response malformed() { return Response.of(Status.MALFORMED, "Malformed metrics response"); }

    private static MetricSample parsePair(JsonNode pair, Map<String, String> labels) {
        if (pair == null || !pair.isArray() || pair.size() != 2
                || !pair.get(0).isNumber() || !pair.get(1).isTextual()) {
            return null;
        }
        Instant instant;
        try {
            // Prometheus timestamps have millisecond precision. Never round malformed precision/overflow into a valid instant.
            long timestampMillis = pair.get(0).decimalValue().movePointRight(3).longValueExact();
            if (timestampMillis < 0) return null;
            instant = Instant.ofEpochMilli(timestampMillis);
        } catch (ArithmeticException e) {
            return null;
        }
        String raw = pair.get(1).asText();
        Double value;
        try {
            if ("NaN".equalsIgnoreCase(raw) || "+Inf".equals(raw) || "-Inf".equals(raw)) {
                value = null;
            } else {
                value = Double.parseDouble(raw);
                if (!Double.isFinite(value)) value = null;
            }
        } catch (NumberFormatException e) {
            value = null;
        }
        return new MetricSample(instant, value, labels);
    }

    private static Map<String, String> readLabels(JsonNode metric) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (metric == null || !metric.isObject()) {
            throw new IllegalArgumentException("Malformed metric labels");
        }
        if (metric.size() > MAX_LABELS) throw new IllegalArgumentException("Metric label limit exceeded");
        Iterator<String> names = metric.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.length() > 128 || !metric.get(name).isTextual() || metric.get(name).textValue().length() > 512) {
                throw new IllegalArgumentException("Malformed metric label");
            }
            labels.put(name, metric.get(name).asText());
        }
        return labels;
    }

    private static void applyAuth(HttpRequest.Builder req, MetricsCredentials creds) {
        if (creds.hasBearer()) {
            req.header("Authorization", "Bearer " + creds.bearerToken());
        } else if (creds.hasBasic()) {
            String raw = creds.username() + ":" + creds.password();
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            req.header("Authorization", "Basic " + encoded);
        }
    }

    private static String encodeParams(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String joinUrl(String base, String path) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return b + path;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static javax.net.ssl.SSLContext insecureSslContext() throws Exception {
        javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }

                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };
        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new java.security.SecureRandom());
        return ctx;
    }
}
