package io.github.keycloakmcp.observability.metrics.prometheus;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.credential.MetricsCredentials;
import io.github.keycloakmcp.observability.metrics.MetricSample;
import io.github.keycloakmcp.observability.metrics.MetricSeries;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Minimal Prometheus HTTP API client using JDK {@link HttpClient}.
 * Never logs bearer tokens or passwords.
 */
@ApplicationScoped
public class PrometheusApiClient {

    private static final Logger LOG = Logger.getLogger(PrometheusApiClient.class);

    public enum Status {
        OK,
        UNAUTHORIZED,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER_ERROR,
        TIMEOUT,
        MALFORMED,
        NETWORK_ERROR,
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
        this.objectMapper = objectMapper;
    }

    /** CDI proxy constructor. */
    protected PrometheusApiClient() {
        this.objectMapper = null;
    }

    public Response query(String baseUrl, String promQl, MetricsCredentials credentials,
            Duration connectTimeout, Duration readTimeout) {
        return execute(baseUrl, "/api/v1/query", Map.of("query", promQl), credentials, connectTimeout, readTimeout);
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", promQl);
        params.put("start", String.valueOf(start.getEpochSecond()));
        params.put("end", String.valueOf(end.getEpochSecond()));
        params.put("step", String.valueOf(Math.max(1, step.toSeconds())));
        return execute(baseUrl, "/api/v1/query_range", params, credentials, connectTimeout, readTimeout);
    }

    private Response execute(
            String baseUrl,
            String path,
            Map<String, String> params,
            MetricsCredentials credentials,
            Duration connectTimeout,
            Duration readTimeout) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Response.of(Status.NOT_FOUND, "metrics endpoint not configured");
        }
        String url = joinUrl(baseUrl.trim(), path) + "?" + encodeParams(params);
        MetricsCredentials creds = credentials == null ? MetricsCredentials.none() : credentials;

        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .connectTimeout(connectTimeout)
                    .followRedirects(HttpClient.Redirect.NORMAL);
            if (creds.trustInsecure()) {
                clientBuilder.sslContext(insecureSslContext());
            }
            HttpClient client = clientBuilder.build();

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(readTimeout)
                    .GET()
                    .header("Accept", "application/json");
            applyAuth(req, creds);

            HttpResponse<String> httpResponse = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
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
            return parseBody(httpResponse.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.of(Status.TIMEOUT, "Interrupted");
        } catch (java.net.http.HttpTimeoutException e) {
            LOG.debugf("Prometheus query timed out for path=%s", path);
            return Response.of(Status.TIMEOUT, "Request timed out");
        } catch (Exception e) {
            LOG.debugf(e, "Prometheus query failed for path=%s", path);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Response.of(Status.NETWORK_ERROR, msg);
        }
    }

    Response parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Response.of(Status.MALFORMED, "Empty response body");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String status = text(root, "status");
            if (status != null && !"success".equalsIgnoreCase(status)) {
                String error = text(root, "error");
                return Response.of(Status.SERVER_ERROR, error != null ? error : "Prometheus status=" + status);
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) {
                return Response.of(Status.EMPTY, "No data");
            }
            String resultType = text(data, "resultType");
            JsonNode result = data.get("result");
            if (result == null || !result.isArray() || result.isEmpty()) {
                return new Response(Status.EMPTY, "Empty result", List.of());
            }
            List<MetricSeries> series = new ArrayList<>();
            for (JsonNode item : result) {
                Map<String, String> labels = readLabels(item.get("metric"));
                String name = labels.getOrDefault("__name__", "value");
                List<MetricSample> samples = new ArrayList<>();
                if ("matrix".equalsIgnoreCase(resultType) || item.has("values")) {
                    JsonNode values = item.get("values");
                    if (values != null && values.isArray()) {
                        for (JsonNode pair : values) {
                            MetricSample sample = parsePair(pair, labels);
                            if (sample != null) {
                                samples.add(sample);
                            }
                        }
                    }
                } else {
                    MetricSample sample = parsePair(item.get("value"), labels);
                    if (sample != null) {
                        samples.add(sample);
                    }
                }
                series.add(new MetricSeries(name, labels, samples));
            }
            return new Response(Status.OK, "ok", List.copyOf(series));
        } catch (Exception e) {
            LOG.debugf(e, "Failed to parse Prometheus JSON");
            return Response.of(Status.MALFORMED, "Malformed JSON");
        }
    }

    private static MetricSample parsePair(JsonNode pair, Map<String, String> labels) {
        if (pair == null || !pair.isArray() || pair.size() < 2) {
            return null;
        }
        double ts = pair.get(0).asDouble();
        String raw = pair.get(1).asText();
        Double value;
        try {
            if ("NaN".equalsIgnoreCase(raw) || "+Inf".equals(raw) || "-Inf".equals(raw)) {
                value = null;
            } else {
                value = Double.parseDouble(raw);
            }
        } catch (NumberFormatException e) {
            value = null;
        }
        Instant instant = Instant.ofEpochMilli((long) (ts * 1000d));
        return new MetricSample(instant, value, labels);
    }

    private static Map<String, String> readLabels(JsonNode metric) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (metric == null || !metric.isObject()) {
            return labels;
        }
        Iterator<String> names = metric.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
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
