package io.github.keycloakmcp.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.config.HealthConfig;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves management URL from target keycloak.managementUrl or tag {@code management-url}.
 * Does not recommend exposing management port 9000 publicly.
 */
@ApplicationScoped
public class DefaultKeycloakManagementHealthProvider implements KeycloakManagementHealthProvider {

    private static final Logger LOG = Logger.getLogger(DefaultKeycloakManagementHealthProvider.class);
    private static final List<String> PATHS = List.of("/health/ready", "/health/live", "/health");

    private final HealthConfig healthConfig;
    private final ObjectMapper objectMapper;

    @Inject
    public DefaultKeycloakManagementHealthProvider(HealthConfig healthConfig, ObjectMapper objectMapper) {
        this.healthConfig = healthConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public ManagementHealthResult check(Target target) {
        String baseUrl = resolveManagementUrl(target);
        if (baseUrl == null || baseUrl.isBlank()) {
            return ManagementHealthResult.notConfigured();
        }

        Duration connect = Duration.ofMillis(Math.max(100, healthConfig.management().connectTimeoutMs()));
        Duration read = Duration.ofMillis(Math.max(100, healthConfig.management().readTimeoutMs()));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connect)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("configured", true);
        details.put("managementUrl", baseUrl);
        details.put("note", "Management interface should not be exposed publicly (e.g. port 9000)");

        HealthStatus worst = HealthStatus.HEALTHY;
        String message = "Management health endpoints reachable";

        for (String path : PATHS) {
            String url = joinUrl(baseUrl, path);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(read)
                        .GET()
                        .header("Accept", "application/json")
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                Map<String, Object> pathDetails = new LinkedHashMap<>();
                pathDetails.put("httpStatus", response.statusCode());
                HealthStatus pathStatus;
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    pathStatus = parseBodyStatus(response.body(), pathDetails);
                } else if (response.statusCode() == 404) {
                    pathStatus = HealthStatus.UNKNOWN;
                    pathDetails.put("reason", "endpoint not found");
                } else {
                    pathStatus = HealthStatus.CRITICAL;
                    pathDetails.put("bodySnippet", truncate(response.body(), 200));
                }
                details.put(path, pathDetails);
                worst = worse(worst, pathStatus);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                details.put(path, Map.of("error", "interrupted"));
                worst = HealthStatus.CRITICAL;
                message = "Interrupted probing management health";
                break;
            } catch (Exception e) {
                LOG.debugf(e, "Management health probe failed for %s", url);
                details.put(path, Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                worst = HealthStatus.CRITICAL;
                message = "Management health probe failed: " + path;
            }
        }

        if (worst == HealthStatus.CRITICAL) {
            message = message.startsWith("Management") ? message : "One or more management health endpoints are DOWN";
        } else if (worst == HealthStatus.WARNING) {
            message = "Management health reported WARNING";
        }
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

    private HealthStatus parseBodyStatus(String body, Map<String, Object> pathDetails) {
        if (body == null || body.isBlank()) {
            return HealthStatus.HEALTHY;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String status = text(root, "status");
            if (status != null) {
                pathDetails.put("status", status);
                HealthStatus mapped = mapStatus(status);
                JsonNode checks = root.get("checks");
                if (checks != null && checks.isArray()) {
                    Map<String, String> nested = new LinkedHashMap<>();
                    for (JsonNode check : checks) {
                        String name = text(check, "name");
                        String st = text(check, "status");
                        if (name != null && st != null) {
                            nested.put(name, st);
                            if (isDbOrCluster(name) && "DOWN".equalsIgnoreCase(st)) {
                                mapped = worse(mapped, HealthStatus.CRITICAL);
                            }
                        }
                    }
                    if (!nested.isEmpty()) {
                        pathDetails.put("checks", nested);
                    }
                }
                return mapped;
            }
        } catch (Exception e) {
            pathDetails.put("parseError", e.getMessage());
        }
        return HealthStatus.HEALTHY;
    }

    private static boolean isDbOrCluster(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("database") || lower.contains("cluster") || lower.contains("db");
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
            case UNKNOWN -> 0;
            case HEALTHY -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
