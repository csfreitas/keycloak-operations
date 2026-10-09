package io.github.keycloakmcp.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KeycloakAdminApiHealthCheck implements HealthCheck {

    private final StableAdminApiAdapter adminApi;

    @Inject
    public KeycloakAdminApiHealthCheck(StableAdminApiAdapter adminApi) {
        this.adminApi = adminApi;
    }

    @Override
    public String name() {
        return "keycloak.adminApi";
    }

    @Override
    public HealthComponentResult check(Target target) {
        long start = System.currentTimeMillis();
        try {
            ServerInfoRepresentation info = adminApi.getServerInfo(target);
            if (info == null) {
                return failure(start, HealthStatus.UNKNOWN, "METADATA_UNAVAILABLE",
                        "Admin API metadata response is missing; availability is inconclusive");
            }
            SystemInfoRepresentation system = info.getSystemInfo();
            Map<String, Object> details = new LinkedHashMap<>();
            String version = system == null ? null : system.getVersion();
            if (version != null && !version.isBlank()) details.put("version", version);
            details.put("versionAvailable", version != null && !version.isBlank());
            long latency = System.currentTimeMillis() - start;
            details.put("latencyMs", latency);
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.HEALTHY,
                    "Admin API metadata endpoint reachable; this does not establish full environment health",
                    details,
                    latency);
        } catch (McpException e) {
            return switch (e.getCode()) {
                case AUTHORIZATION_FAILED, TARGET_NOT_AUTHORIZED -> failure(start, HealthStatus.UNKNOWN,
                        "ACCESS_DENIED", "Admin API metadata access denied; availability is inconclusive");
                case AUTHENTICATION_FAILED -> failure(start, HealthStatus.UNKNOWN,
                        "AUTHENTICATION_FAILED", "Admin API authentication failed; availability is inconclusive");
                case KEYCLOAK_UNAVAILABLE -> failure(start, HealthStatus.CRITICAL,
                        "REQUEST_FAILED", "Admin API metadata request failed; transport or upstream failure, not proof of a full outage");
                case UNSUPPORTED_CAPABILITY -> failure(start, HealthStatus.UNKNOWN,
                        "METADATA_UNAVAILABLE", "Admin API metadata is not supported; availability is inconclusive");
                default -> failure(start, HealthStatus.UNKNOWN,
                        "CHECK_FAILED", "Admin API metadata check could not determine availability");
            };
        } catch (RuntimeException e) {
            // Exception messages/causes can contain credentials or provider response bodies.
            return failure(start, HealthStatus.UNKNOWN, "CHECK_FAILED",
                    "Admin API metadata check could not determine availability");
        }
    }

    private HealthComponentResult failure(long start, HealthStatus status, String reason, String message) {
        return HealthComponentResult.of(name(), status, message, Map.of("reasonCode", reason),
                System.currentTimeMillis() - start);
    }
}
