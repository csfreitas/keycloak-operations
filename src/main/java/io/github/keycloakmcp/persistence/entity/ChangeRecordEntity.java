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
@Table(name = "change_records")
public class ChangeRecordEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    public String id;

    @Column(name = "target_id", length = 128, nullable = false)
    public String targetId;

    @Column(name = "environment", length = 32, nullable = false)
    public String environment;

    @Column(name = "resource_type", length = 64, nullable = false)
    public String resourceType;

    @Column(name = "resource_id", nullable = false)
    public String resourceId;

    @Column(name = "realm", nullable = false)
    public String realm;

    @Column(name = "operation", length = 32, nullable = false)
    public String operation;

    @Column(name = "status", length = 32, nullable = false)
    public String status;

    @Column(name = "risk", length = 32)
    public String risk;

    @Column(name = "policy_decision", length = 64)
    public String policyDecision;

    @Column(name = "policy_reason", columnDefinition = "text")
    public String policyReason;

    @Column(name = "requires_approval", nullable = false)
    public boolean requiresApproval;

    @Column(name = "plan_fingerprint", length = 128)
    public String planFingerprint;

    @Column(name = "baseline_fingerprint", length = 128)
    public String baselineFingerprint;

    @Column(name = "approval_fingerprint", length = 128)
    public String approvalFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "desired_state", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> desiredState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "baseline_state", columnDefinition = "jsonb", nullable = false)
    public Map<String, Object> baselineState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json", columnDefinition = "jsonb", nullable = false)
    public List<Map<String, Object>> diffJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operations_json", columnDefinition = "jsonb", nullable = false)
    public List<Map<String, Object>> operationsJson;

    @Column(name = "actor")
    public String actor;

    @Column(name = "approved_by")
    public String approvedBy;

    @Column(name = "approved_at")
    public Instant approvedAt;

    @Column(name = "rejected_by")
    public String rejectedBy;

    @Column(name = "rejected_at")
    public Instant rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    public String rejectionReason;

    @Column(name = "applied_at")
    public Instant appliedAt;

    @Column(name = "verification_status", length = 32)
    public String verificationStatus;

    @Column(name = "verification_message", columnDefinition = "text")
    public String verificationMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_json", columnDefinition = "jsonb")
    public List<Map<String, Object>> verificationJson;

    @Column(name = "result_message", columnDefinition = "text")
    public String resultMessage;

    @Column(name = "idempotency_key", length = 128)
    public String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
