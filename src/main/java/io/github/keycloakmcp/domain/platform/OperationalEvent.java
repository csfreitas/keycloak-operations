package io.github.keycloakmcp.domain.platform;

import java.time.Instant;

/**
 * Bounded operational SSE event for the Fleet Operations Console.
 * Never carries raw Prometheus samples or secrets.
 */
public record OperationalEvent(
        String type,
        String targetId,
        Instant at,
        String message,
        String relatedId) {

    public static OperationalEvent of(String type, String targetId, String message, String relatedId) {
        return new OperationalEvent(type, targetId, Instant.now(), message, relatedId);
    }
}
