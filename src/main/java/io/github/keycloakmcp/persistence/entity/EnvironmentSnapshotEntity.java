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
@Table(name = "environment_snapshots")
public class EnvironmentSnapshotEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "snapshot_hash", length = 64, nullable = false)
    public String snapshotHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> summary;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
