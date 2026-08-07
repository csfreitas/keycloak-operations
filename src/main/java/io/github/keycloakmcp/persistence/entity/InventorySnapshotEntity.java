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
@Table(name = "inventory_snapshots")
public class InventorySnapshotEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "environment_snapshot_id", length = 36)
    public String environmentSnapshotId;

    @Column(name = "inventory_type", length = 64, nullable = false)
    public String inventoryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    public Map<String, Object> summary;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
