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
@Table(name = "health_check_results")
public class HealthCheckResultEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "health_check_id", length = 36, nullable = false)
    public String healthCheckId;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "check_name", nullable = false)
    public String checkName;

    @Column(name = "status", length = 32, nullable = false)
    public String status;

    @Column(name = "message", columnDefinition = "text")
    public String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    public Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
