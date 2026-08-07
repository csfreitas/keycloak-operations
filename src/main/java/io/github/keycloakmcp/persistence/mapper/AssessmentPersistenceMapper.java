package io.github.keycloakmcp.persistence.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Severity;
import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.FindingLifecycleStatus;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.AssessmentFindingEntity;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AssessmentPersistenceMapper {

    private final SensitiveDataFilter sensitiveDataFilter;

    @Inject
    public AssessmentPersistenceMapper(SensitiveDataFilter sensitiveDataFilter) {
        this.sensitiveDataFilter = sensitiveDataFilter;
    }

    public AssessmentRunEntity toRunEntity(AssessmentResult result, String runId, TriggerType triggerType) {
        AssessmentRunEntity entity = new AssessmentRunEntity();
        entity.id = runId;
        entity.targetId = result.targetId();
        entity.profile = result.profile();
        entity.score = result.score();
        entity.status = "COMPLETED";
        entity.triggerType = triggerType == null ? TriggerType.SYSTEM.name() : triggerType.name();
        Map<String, Object> summary = new HashMap<>();
        summary.put("findingCount", result.findings() == null ? 0 : result.findings().size());
        summary.put("score", result.score());
        entity.summary = sensitiveDataFilter.redact(summary);
        entity.startedAt = result.startedAt() == null ? Instant.now() : result.startedAt();
        entity.completedAt = result.completedAt() == null ? Instant.now() : result.completedAt();
        entity.createdAt = Instant.now();
        return entity;
    }

    public List<AssessmentFindingEntity> toFindingEntities(AssessmentResult result, String assessmentId) {
        if (result.findings() == null || result.findings().isEmpty()) {
            return List.of();
        }
        List<AssessmentFindingEntity> entities = new ArrayList<>();
        for (Finding finding : result.findings()) {
            FindingLifecycleStatus lifecycle = toLifecycle(finding.status());
            if (lifecycle == null) {
                continue;
            }
            AssessmentFindingEntity entity = new AssessmentFindingEntity();
            entity.id = UUID.randomUUID().toString();
            entity.assessmentId = assessmentId;
            entity.targetId = result.targetId();
            entity.findingKey = finding.id();
            entity.title = finding.title();
            entity.category = finding.category();
            entity.severity = finding.severity() == null ? Severity.INFO.name() : finding.severity().name();
            entity.engineStatus = finding.status() == null ? FindingStatus.OPEN.name() : finding.status().name();
            entity.lifecycleStatus = lifecycle.name();
            entity.description = finding.description();
            entity.evidence = finding.evidence() == null
                    ? null
                    : sensitiveDataFilter.redact(new HashMap<>(finding.evidence()));
            entity.impact = finding.impact();
            entity.recommendation = finding.recommendation();
            entity.references = finding.references() == null ? List.of() : List.copyOf(finding.references());
            entity.createdAt = Instant.now();
            entities.add(entity);
        }
        return entities;
    }

    public AssessmentRunSummary toSummary(AssessmentRunEntity entity) {
        return new AssessmentRunSummary(
                entity.id,
                entity.targetId,
                entity.profile,
                entity.score,
                entity.status,
                parseTrigger(entity.triggerType),
                entity.startedAt,
                entity.completedAt,
                entity.createdAt);
    }

    public Finding toDomainFinding(AssessmentFindingEntity entity) {
        return new Finding(
                entity.targetId,
                entity.findingKey,
                entity.title,
                entity.category,
                parseSeverity(entity.severity),
                parseEngineStatus(entity.engineStatus),
                entity.description,
                entity.evidence,
                entity.impact,
                entity.recommendation,
                entity.references);
    }

    /**
     * Maps engine status to lifecycle. PASS findings are stored as RESOLVED;
     * OPEN/FAIL/WARNING → OPEN.
     */
    public static FindingLifecycleStatus toLifecycle(FindingStatus status) {
        if (status == null) {
            return FindingLifecycleStatus.OPEN;
        }
        return switch (status) {
            case PASS -> FindingLifecycleStatus.RESOLVED;
            case OPEN, FAIL, WARNING -> FindingLifecycleStatus.OPEN;
        };
    }

    private static TriggerType parseTrigger(String raw) {
        if (raw == null || raw.isBlank()) {
            return TriggerType.SYSTEM;
        }
        try {
            return TriggerType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TriggerType.SYSTEM;
        }
    }

    private static Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return Severity.INFO;
        }
        try {
            return Severity.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Severity.INFO;
        }
    }

    private static FindingStatus parseEngineStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return FindingStatus.OPEN;
        }
        try {
            return FindingStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return FindingStatus.OPEN;
        }
    }
}
