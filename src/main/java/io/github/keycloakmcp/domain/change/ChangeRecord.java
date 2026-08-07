package io.github.keycloakmcp.domain.change;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate view of a controlled change lifecycle record.
 */
public record ChangeRecord(
        String changeId,
        String targetId,
        String environment,
        ChangeResourceType resourceType,
        String resourceId,
        String realm,
        ChangeOperationType operation,
        ChangeStatus status,
        ChangeRisk risk,
        ChangePolicyDecision policyDecision,
        String policyReason,
        boolean requiresApproval,
        String planFingerprint,
        String baselineFingerprint,
        String approvalFingerprint,
        List<ChangeDiffEntry> diff,
        List<ChangeOperation> operations,
        Map<String, Object> desiredState,
        Map<String, Object> baselineState,
        String actor,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectionReason,
        Instant appliedAt,
        String verificationStatus,
        String verificationMessage,
        List<ChangeDiffEntry> verificationMismatches,
        String resultMessage,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt) {

    public ChangeRecord {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(status, "status");
        diff = diff == null ? List.of() : List.copyOf(diff);
        operations = operations == null ? List.of() : List.copyOf(operations);
        // LinkedHashMap copy — values may be null (Map.copyOf forbids nulls).
        desiredState = desiredState == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(desiredState));
        baselineState = baselineState == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(baselineState));
        verificationMismatches =
                verificationMismatches == null ? List.of() : List.copyOf(verificationMismatches);
    }
}
