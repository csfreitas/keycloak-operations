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
@Table(name = "health_check_runs")
public class HealthCheckRunEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "overall_status", length = 32, nullable = false)
    public String overallStatus;

    @Column(name = "trigger_type", length = 32, nullable = false)
    public String triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    public Map<String, Object> summary;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
