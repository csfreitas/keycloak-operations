package io.github.keycloakmcp.health;

import java.util.Map;

import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.Target;

/**
 * Probes Keycloak management interface health endpoints when a management URL is configured.
 */
public interface KeycloakManagementHealthProvider {

    ManagementHealthResult check(Target target);

    record ManagementHealthResult(
            HealthStatus status,
            String message,
            Map<String, Object> details) {

        public ManagementHealthResult {
            details = details == null ? Map.of() : Map.copyOf(details);
        }

        public static ManagementHealthResult notConfigured() {
            return new ManagementHealthResult(
                    HealthStatus.UNKNOWN,
                    "NOT_CONFIGURED",
                    Map.of("configured", false));
        }
    }
}
