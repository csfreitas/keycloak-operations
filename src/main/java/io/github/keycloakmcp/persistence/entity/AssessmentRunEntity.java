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
@Table(name = "assessment_runs")
public class AssessmentRunEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "profile", nullable = false)
    public String profile;

    @Column(name = "score", nullable = false)
    public int score;

    @Column(name = "status", length = 32, nullable = false)
    public String status;

    @Column(name = "trigger_type", length = 32, nullable = false)
    public String triggerType;

    @Column(name = "evidence_completeness")
    public Integer evidenceCompleteness;

    @Column(name = "confidence", length = 16)
    public String confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_scores", columnDefinition = "jsonb")
    public Map<String, Integer> categoryScores;

    @Column(name = "rules_evaluated")
    public Integer rulesEvaluated;

    @Column(name = "rules_matched")
    public Integer rulesMatched;

    @Column(name = "rules_skipped")
    public Integer rulesSkipped;

    @Column(name = "rules_not_evaluated")
    public Integer rulesNotEvaluated;

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
