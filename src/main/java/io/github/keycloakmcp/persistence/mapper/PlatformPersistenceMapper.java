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
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlatformPersistenceMapper {

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

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
                sensitiveDataFilter.redactString(entity.tool),
                entity.targetId,
                sensitiveDataFilter.redactString(entity.operation),
                entity.status,
                entity.durationMs,
                entity.createdAt,
                sensitiveDataFilter.redactMetadata(entity.metadata));
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
        entity.tool = sensitiveDataFilter.redactString(tool);
        entity.targetId = targetId;
        entity.operation = sensitiveDataFilter.redactString(operation);
        entity.status = status == null ? "UNKNOWN" : status;
        entity.durationMs = durationMs;
        // Only free-form payloads are lossy projections; correlation/scope identifiers
        // above remain exact so filtering cannot move an event to a different target.
        entity.params = sensitiveDataFilter.redactMetadata(params);
        entity.metadata = sensitiveDataFilter.redactMetadata(metadata);
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
