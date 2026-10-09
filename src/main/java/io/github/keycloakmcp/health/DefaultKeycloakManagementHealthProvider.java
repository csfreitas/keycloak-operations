package io.github.keycloakmcp.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.config.HealthConfig;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.http.BoundedBodyHandler;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves management URL from target keycloak.managementUrl or tag {@code management-url}.
 * Does not recommend exposing management port 9000 publicly.
 */
@ApplicationScoped
public class DefaultKeycloakManagementHealthProvider implements KeycloakManagementHealthProvider {

    private static final List<String> PATHS = List.of("/health/ready", "/health/live", "/health");
    static final int MAX_RESPONSE_BYTES = 64 * 1024;
    static final int MAX_CHECKS = 100;

    private final HealthConfig healthConfig;
    private final ObjectMapper objectMapper;
    @ConfigProperty(name = "collection.operation-timeout-ms", defaultValue = "30000")
    long collectionTimeoutMs = 30000;

    @Inject
    public DefaultKeycloakManagementHealthProvider(HealthConfig healthConfig, ObjectMapper objectMapper) {
        this.healthConfig = healthConfig;
        this.objectMapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.objectMapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16)
                .maxStringLength(4096)
                .maxNumberLength(128)
                .build());
    }

    @Override
    public ManagementHealthResult check(Target target) {
        if (target == null) return ManagementHealthResult.notConfigured();
        try (var scope = CollectionBudget.open(target.id().value(), collectionTimeoutMs)) {
            return checkScoped(target, scope.budget());
        }
    }

    private ManagementHealthResult checkScoped(Target target, CollectionBudget budget) {
        String baseUrl = resolveManagementUrl(target);
        if (baseUrl == null || baseUrl.isBlank()) {
            return ManagementHealthResult.notConfigured();
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("configured", true);
        if (!validBaseUrl(baseUrl)) {
            details.put("reasonCode", "INVALID_CONFIGURATION");
            return new ManagementHealthResult(HealthStatus.UNKNOWN,
                    "Management health endpoint configuration is invalid", details);
        }

        Duration connect = Duration.ofMillis(Math.max(100, healthConfig.management().connectTimeoutMs()));
        Duration read = Duration.ofMillis(Math.max(100, healthConfig.management().readTimeoutMs()));
        HealthStatus worst = HealthStatus.HEALTHY;
        if (budget.exhausted()) {
            details.put(PATHS.getFirst(), Map.of("status", "UNKNOWN", "reasonCode", abortPathReason(budget)));
            markAborted(details, budget);
            return result(HealthStatus.UNKNOWN, details);
        }
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(budget.cap(connect))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            for (String path : PATHS) {
                Map<String, Object> pathDetails = new LinkedHashMap<>();
                HealthStatus pathStatus = HealthStatus.UNKNOWN;
                try {
                    budget.checkpoint();
                    Duration pathTimeout = budget.cap(read);
                    CollectionBudget pathBudget = CollectionBudget.start(pathTimeout);
                    HttpRequest request = HttpRequest.newBuilder(URI.create(joinUrl(baseUrl, path)))
                            .timeout(pathTimeout)
                            .GET()
                            .header("Accept", "application/json")
                            .build();
                    HttpResponse<byte[]> response = client.send(request,
                            new BoundedBodyHandler(MAX_RESPONSE_BYTES, pathTimeout,
                                    () -> remaining(budget, pathBudget)));
                    budget.checkpoint();
                    if (pathBudget.exhausted()) throw new HttpTimeoutException("Management response deadline exceeded");
                    int status = response.statusCode();
                    pathDetails.put("httpStatus", status);
                    if ((status >= 200 && status < 300) || status == 503) {
                        pathStatus = parseBodyStatus(response.body(), pathDetails);
                        // A valid DOWN on the standard health failure response is an observation.
                        // A 503 carrying UP/WARNING cannot establish health.
                        if (status == 503 && (pathStatus == HealthStatus.HEALTHY || pathStatus == HealthStatus.WARNING)) {
                            pathStatus = HealthStatus.UNKNOWN;
                            pathDetails.put("reasonCode", "HTTP_STATUS_CONFLICT");
                        }
                    } else {
                        pathDetails.put("reasonCode", switch (status) {
                            case 401 -> "AUTHENTICATION_FAILED";
                            case 403 -> "ACCESS_DENIED";
                            case 404 -> "ENDPOINT_NOT_FOUND";
                            default -> status >= 300 && status < 400 ? "REDIRECT_REJECTED" : "HTTP_RESPONSE_UNAVAILABLE";
                        });
                    }
                    budget.checkpoint();
                    if (pathBudget.exhausted()) throw new HttpTimeoutException("Management response deadline exceeded");
                } catch (CollectionBudget.Aborted e) {
                    pathStatus = HealthStatus.UNKNOWN;
                    pathDetails.clear();
                    pathDetails.put("reasonCode", abortPathReason(budget));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pathStatus = HealthStatus.UNKNOWN;
                    pathDetails.clear();
                    pathDetails.put("reasonCode", "INTERRUPTED");
                } catch (Exception e) {
                    pathStatus = HealthStatus.UNKNOWN;
                    pathDetails.clear();
                    // Exception messages and causes may contain endpoint credentials or response bodies.
                    String reason = budget.exhausted() ? abortPathReason(budget)
                            : BoundedBodyHandler.isLimitFailure(e) ? "RESPONSE_LIMIT_EXCEEDED"
                            : BoundedBodyHandler.isTimeoutFailure(e) || e instanceof HttpTimeoutException ? "TIMEOUT"
                            : "REQUEST_FAILED";
                    pathDetails.put("reasonCode", reason);
                }
                pathDetails.put("status", pathStatus.name());
                details.put(path, Map.copyOf(pathDetails));
                worst = worse(worst, pathStatus);
                if (budget.exhausted()) {
                    markAborted(details, budget);
                    break;
                }
            }
        } catch (RuntimeException e) {
            if (budget.exhausted()) markAborted(details, budget);
            else details.put("reasonCode", "REQUEST_FAILED");
            worst = worse(worst, HealthStatus.UNKNOWN);
        }
        if (budget.exhausted()) {
            markAborted(details, budget);
            worst = worse(worst, HealthStatus.UNKNOWN);
        }
        return result(worst, details);
    }

    private static Duration remaining(CollectionBudget parent, CollectionBudget path) {
        Duration a = parent.remaining(), b = path.remaining();
        return a.compareTo(b) < 0 ? a : b;
    }

    private static String abortPathReason(CollectionBudget budget) {
        return CollectionBudget.REASON_INTERRUPTED.equals(budget.reason()) ? "INTERRUPTED" : budget.reason();
    }

    private static void markAborted(Map<String, Object> details, CollectionBudget budget) {
        details.put("collectionComplete", false);
        details.put("reasonCode", budget.reason());
    }

    private static ManagementHealthResult result(HealthStatus worst, Map<String, Object> details) {
        String message = switch (worst) {
            case HEALTHY -> "Management health endpoints reported UP";
            case WARNING -> "Management health endpoints reported WARNING";
            case CRITICAL -> "One or more management health endpoints reported DOWN";
            case UNKNOWN -> "Management health observation is incomplete or unavailable";
        };
        return new ManagementHealthResult(worst, message, details);
    }

    static String resolveManagementUrl(Target target) {
        if (target == null || target.keycloak() == null) {
            return null;
        }
        String fromConfig = target.keycloak().managementUrl();
        if (fromConfig != null && !fromConfig.isBlank()) {
            return fromConfig.trim();
        }
        if (target.tags() != null) {
            String fromTag = target.tags().get("management-url");
            if (fromTag != null && !fromTag.isBlank()) {
                return fromTag.trim();
            }
        }
        return null;
    }

    private HealthStatus parseBodyStatus(byte[] body, Map<String, Object> pathDetails) {
        if (body == null || body.length == 0) {
            pathDetails.put("reasonCode", "MALFORMED_RESPONSE");
            return HealthStatus.UNKNOWN;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                pathDetails.put("reasonCode", "MALFORMED_RESPONSE");
                return HealthStatus.UNKNOWN;
            }
            HealthStatus mapped = mapStatus(text(root, "status"));
            if (mapped == HealthStatus.UNKNOWN) {
                pathDetails.put("reasonCode", "STATUS_UNAVAILABLE");
            }
            JsonNode checks = root.get("checks");
            if (checks != null) {
                if (!checks.isArray() || checks.size() > MAX_CHECKS) {
                    pathDetails.put("reasonCode", checks.isArray() ? "CHECK_LIMIT_EXCEEDED" : "MALFORMED_RESPONSE");
                    return worse(mapped, HealthStatus.UNKNOWN);
                }
                Map<String, Integer> statuses = new LinkedHashMap<>();
                for (JsonNode check : checks) {
                    HealthStatus nested = mapStatus(text(check, "status"));
                    statuses.merge(nested.name(), 1, Integer::sum);
                    mapped = worse(mapped, nested);
                    if (nested == HealthStatus.UNKNOWN) {
                        pathDetails.put("reasonCode", "STATUS_UNAVAILABLE");
                    }
                }
                // Names and data are untrusted and may contain secrets. Keep only bounded counts.
                pathDetails.put("checkCount", checks.size());
                pathDetails.put("checkStatuses", Map.copyOf(statuses));
            }
            return mapped;
        } catch (Exception e) {
            pathDetails.put("reasonCode", "MALFORMED_RESPONSE");
            return HealthStatus.UNKNOWN;
        }
    }

    private static HealthStatus mapStatus(String raw) {
        if (raw == null) {
            return HealthStatus.UNKNOWN;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "UP", "OK", "HEALTHY" -> HealthStatus.HEALTHY;
            case "DOWN", "CRITICAL" -> HealthStatus.CRITICAL;
            case "WARNING", "DEGRADED" -> HealthStatus.WARNING;
            default -> HealthStatus.UNKNOWN;
        };
    }

    private static HealthStatus worse(HealthStatus a, HealthStatus b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(HealthStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case HEALTHY -> 0;
            case UNKNOWN -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }

    private static String joinUrl(String base, String path) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return b + path;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.has(field) || !node.get(field).isTextual()) {
            return null;
        }
        return node.get(field).asText();
    }

    private static boolean validBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
