package io.github.keycloakmcp.persistence.mapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.github.keycloakmcp.domain.platform.AuditEventSummary;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.entity.HealthCheckRunEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlatformPersistenceMapper {

    public HealthCheckSummary toHealthSummary(HealthCheckRunEntity entity) {
        return new HealthCheckSummary(
                entity.id,
                entity.targetId,
                parseHealth(entity.overallStatus),
                parseTrigger(entity.triggerType),
                entity.startedAt,
                entity.completedAt,
                entity.createdAt);
    }

    public SnapshotSummary toSnapshotSummary(EnvironmentSnapshotEntity entity) {
        return new SnapshotSummary(entity.id, entity.targetId, entity.snapshotHash, entity.createdAt);
    }

    public AuditEventSummary toAuditSummary(AuditEventEntity entity) {
        return new AuditEventSummary(
                entity.id,
                entity.traceId,
                parseAuditSource(entity.source),
                entity.tool,
                entity.targetId,
                entity.operation,
                entity.status,
                entity.durationMs,
                entity.createdAt,
                entity.metadata);
    }

    public AuditEventEntity newAuditEvent(
            AuditSource source,
            String tool,
            String targetId,
            String operation,
            String status,
            Long durationMs,
            String traceId,
            Map<String, Object> params,
            Map<String, Object> metadata) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.id = UUID.randomUUID().toString();
        entity.traceId = traceId;
        entity.source = source == null ? AuditSource.SYSTEM.name() : source.name();
        entity.tool = tool;
        entity.targetId = targetId;
        entity.operation = operation;
        entity.status = status == null ? "UNKNOWN" : status;
        entity.durationMs = durationMs;
        entity.params = params;
        entity.metadata = metadata;
        entity.createdAt = Instant.now();
        return entity;
    }

    private static HealthStatus parseHealth(String raw) {
        if (raw == null || raw.isBlank()) {
            return HealthStatus.UNKNOWN;
        }
        try {
            return HealthStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return HealthStatus.UNKNOWN;
        }
    }

    private static TriggerType parseTrigger(String raw) {
        if (raw == null || raw.isBlank()) {
            return TriggerType.SYSTEM;
        }
        try {
            return TriggerType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TriggerType.SYSTEM;
        }
    }

    private static AuditSource parseAuditSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return AuditSource.SYSTEM;
        }
        try {
            return AuditSource.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return AuditSource.SYSTEM;
        }
    }
}
