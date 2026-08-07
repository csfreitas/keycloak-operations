package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

public record AuditEventSummary(
        String id,
        String traceId,
        AuditSource source,
        String tool,
        String targetId,
        String operation,
        String status,
        Long durationMs,
        Instant createdAt,
        Map<String, Object> metadata) {
}
