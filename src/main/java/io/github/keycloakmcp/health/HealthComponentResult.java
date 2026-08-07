package io.github.keycloakmcp.health;

import java.util.Map;

import io.github.keycloakmcp.domain.platform.HealthStatus;

/**
 * Outcome of a single health component check.
 */
public record HealthComponentResult(
        String name,
        HealthStatus status,
        String message,
        Map<String, Object> details,
        long durationMs) {

    public HealthComponentResult {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static HealthComponentResult of(
            String name, HealthStatus status, String message, Map<String, Object> details, long durationMs) {
        return new HealthComponentResult(name, status, message, details, durationMs);
    }
}
