package io.github.keycloakmcp.persistence.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.domain.change.ChangeDiffEntry;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ChangePolicyDecision;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ChangeResourceType;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChangePersistenceMapper {

    public ChangeRecord toDomain(ChangeRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ChangeRecord(
                entity.id,
                entity.targetId,
                entity.environment,
                ChangeResourceType.valueOf(entity.resourceType),
                entity.resourceId,
                entity.realm,
                ChangeOperationType.valueOf(entity.operation),
                ChangeStatus.valueOf(entity.status),
                entity.risk == null ? null : ChangeRisk.valueOf(entity.risk),
                entity.policyDecision == null ? null : ChangePolicyDecision.valueOf(entity.policyDecision),
                entity.policyReason,
                entity.requiresApproval,
                entity.planFingerprint,
                entity.baselineFingerprint,
                entity.approvalFingerprint,
                toDiff(entity.diffJson),
                toOperations(entity.operationsJson),
                entity.desiredState == null ? Map.of() : entity.desiredState,
                entity.baselineState == null ? Map.of() : entity.baselineState,
                entity.actor,
                entity.approvedBy,
                entity.approvedAt,
                entity.rejectedBy,
                entity.rejectedAt,
                entity.rejectionReason,
                entity.appliedAt,
                entity.verificationStatus,
                entity.verificationMessage,
                toDiff(entity.verificationJson),
                entity.resultMessage,
                entity.idempotencyKey,
                entity.createdAt,
                entity.updatedAt);
    }

    public List<Map<String, Object>> fromDiff(List<ChangeDiffEntry> diff) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (diff == null) {
            return out;
        }
        for (ChangeDiffEntry entry : diff) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("property", entry.property());
            map.put("kind", entry.kind().name());
            map.put("before", entry.before());
            map.put("after", entry.after());
            out.add(map);
        }
        return out;
    }

    public List<Map<String, Object>> fromOperations(List<ChangeOperation> operations) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (operations == null) {
            return out;
        }
        for (ChangeOperation op : operations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("property", op.property());
            map.put("operationType", op.operationType().name());
            map.put("before", op.before());
            map.put("after", op.after());
            out.add(map);
        }
        return out;
    }

    private List<ChangeDiffEntry> toDiff(List<Map<String, Object>> json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        List<ChangeDiffEntry> out = new ArrayList<>();
        for (Map<String, Object> map : json) {
            out.add(new ChangeDiffEntry(
                    String.valueOf(map.get("property")),
                    DiffKind.valueOf(String.valueOf(map.get("kind"))),
                    map.get("before") == null ? null : String.valueOf(map.get("before")),
                    map.get("after") == null ? null : String.valueOf(map.get("after"))));
        }
        return out;
    }

    private List<ChangeOperation> toOperations(List<Map<String, Object>> json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        List<ChangeOperation> out = new ArrayList<>();
        for (Map<String, Object> map : json) {
            out.add(new ChangeOperation(
                    String.valueOf(map.get("property")),
                    ChangeOperationType.valueOf(String.valueOf(map.get("operationType"))),
                    map.get("before") == null ? null : String.valueOf(map.get("before")),
                    map.get("after") == null ? null : String.valueOf(map.get("after"))));
        }
        return out;
    }
}
