package io.github.keycloakmcp.persistence.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_findings")
public class AssessmentFindingEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "assessment_id", length = 36, nullable = false)
    public String assessmentId;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "finding_key", nullable = false)
    public String findingKey;

    @Column(name = "title", length = 512, nullable = false)
    public String title;

    @Column(name = "category")
    public String category;

    @Column(name = "severity", length = 32, nullable = false)
    public String severity;

    /** Assessment engine FindingStatus (OPEN/PASS/WARNING/FAIL). */
    @Column(name = "engine_status", length = 32, nullable = false)
    public String engineStatus;

    /** Persistence lifecycle (OPEN/ACKNOWLEDGED/RESOLVED/...). */
    @Column(name = "lifecycle_status", length = 32, nullable = false)
    public String lifecycleStatus;

    @Column(name = "description", columnDefinition = "text")
    public String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    public Map<String, Object> evidence;

    @Column(name = "impact", columnDefinition = "text")
    public String impact;

    @Column(name = "recommendation", columnDefinition = "text")
    public String recommendation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reference_urls", columnDefinition = "jsonb")
    public List<String> references;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
