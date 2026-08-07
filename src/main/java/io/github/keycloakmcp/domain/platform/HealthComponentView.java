package io.github.keycloakmcp.domain.platform;

import java.util.Map;

/** Single health component result for REST/UI. */
public record HealthComponentView(
        String name,
        HealthStatus status,
        String message,
        Long durationMs,
        Map<String, Object> details) {
}
