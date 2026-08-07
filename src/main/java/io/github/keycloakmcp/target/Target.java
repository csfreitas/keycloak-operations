package io.github.keycloakmcp.target;

import java.util.Map;
import java.util.Objects;

/**
 * Fully resolved operational target (Keycloak/RHBK instance plus optional infra/obs).
 */
public record Target(
        TargetId id,
        String displayName,
        TargetType type,
        TargetEnvironment environment,
        boolean enabled,
        KeycloakTargetConfiguration keycloak,
        InfrastructureTargetConfiguration infrastructure,
        ObservabilityTargetConfiguration observability,
        Map<String, String> tags) {

    public Target {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(keycloak, "keycloak");
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    public boolean hasInfrastructure() {
        return infrastructure != null
                && infrastructure.type() != null
                && infrastructure.type() != InfrastructureType.NONE;
    }

    public boolean hasMetrics() {
        return observability != null && observability.hasMetrics();
    }

    /** Alias for assessment/evidence wiring — same as {@link #hasMetrics()}. */
    public boolean hasObservabilityMetrics() {
        return hasMetrics();
    }

    public InfrastructureType infrastructureTypeOrNone() {
        if (infrastructure == null || infrastructure.type() == null) {
            return InfrastructureType.NONE;
        }
        return infrastructure.type();
    }
}
