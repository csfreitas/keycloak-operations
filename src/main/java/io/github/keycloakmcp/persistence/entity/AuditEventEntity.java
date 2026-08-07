package io.github.keycloakmcp.persistence.entity;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "trace_id", length = 64)
    public String traceId;

    @Column(name = "source", length = 32, nullable = false)
    public String source;

    @Column(name = "tool")
    public String tool;

    @Column(name = "target_id", length = 128)
    public String targetId;

    @Column(name = "operation")
    public String operation;

    @Column(name = "status", length = 32, nullable = false)
    public String status;

    @Column(name = "duration_ms")
    public Long durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb")
    public Map<String, Object> params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    public Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
