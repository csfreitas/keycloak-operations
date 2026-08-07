package io.github.keycloakmcp.persistence.mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.EvidenceSubject;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Severity;
import io.github.keycloakmcp.assessment.engine.SubjectType;
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
        entity.score = result.overallScore();
        entity.status = result.status() == null ? "COMPLETE" : result.status().name();
        entity.triggerType = triggerType == null ? TriggerType.SYSTEM.name() : triggerType.name();
        entity.evidenceCompleteness = result.evidenceCompleteness();
        entity.confidence = result.confidence() == null ? null : result.confidence().name();
        entity.categoryScores = result.categoryScores() == null ? null : Map.copyOf(result.categoryScores());
        entity.rulesEvaluated = result.rulesEvaluated();
        entity.rulesMatched = result.rulesMatched();
        entity.rulesSkipped = result.rulesSkipped();
        entity.rulesNotEvaluated = result.rulesNotEvaluated();

        Map<String, Integer> counts = findingCounts(result);
        Map<String, Object> summary = new HashMap<>();
        summary.put("findingCount", counts.values().stream().mapToInt(Integer::intValue).sum());
        summary.put("score", result.overallScore());
        summary.put("overallScore", result.overallScore());
        summary.put("evidenceCompleteness", result.evidenceCompleteness());
        summary.put("confidence", entity.confidence);
        summary.put("categoryScores", result.categoryScores());
        summary.put("rulesEvaluated", result.rulesEvaluated());
        summary.put("rulesMatched", result.rulesMatched());
        summary.put("rulesSkipped", result.rulesSkipped());
        summary.put("rulesNotEvaluated", result.rulesNotEvaluated());
        summary.put("missingEvidence", result.missingEvidence());
        summary.put("criticalCount", counts.getOrDefault("critical", 0));
        summary.put("highCount", counts.getOrDefault("high", 0));
        summary.put("mediumCount", counts.getOrDefault("medium", 0));
        summary.put("lowCount", counts.getOrDefault("low", 0));
        summary.put("infoCount", counts.getOrDefault("info", 0));
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
            // Persist only actionable OPEN/FAIL/WARNING findings
            if (lifecycle != FindingLifecycleStatus.OPEN) {
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
            entity.lifecycleStatus = FindingLifecycleStatus.OPEN.name();
            entity.description = finding.description();
            entity.evidence = finding.evidence() == null
                    ? null
                    : sensitiveDataFilter.redact(new HashMap<>(finding.evidence()));
            entity.impact = finding.impact();
            entity.recommendation = finding.recommendation();
            entity.references = finding.references() == null ? List.of() : List.copyOf(finding.references());
            if (finding.subject() != null) {
                entity.resourceType = finding.subject().type() == null ? null : finding.subject().type().name();
                entity.resourceId = finding.subject().id();
                entity.resourceName = finding.subject().displayName();
            }
            entity.createdAt = Instant.now();
            entities.add(entity);
        }
        return entities;
    }

    public AssessmentRunSummary toSummary(AssessmentRunEntity entity) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Integer> categoryScores = entity.categoryScores;
        Integer completeness = entity.evidenceCompleteness;
        String confidence = entity.confidence;
        if (entity.summary != null) {
            if (completeness == null && entity.summary.get("evidenceCompleteness") instanceof Number n) {
                completeness = n.intValue();
            }
            if (confidence == null && entity.summary.get("confidence") != null) {
                confidence = String.valueOf(entity.summary.get("confidence"));
            }
            if (categoryScores == null && entity.summary.get("categoryScores") instanceof Map<?, ?> raw) {
                Map<String, Integer> parsed = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    if (e.getKey() != null && e.getValue() instanceof Number n) {
                        parsed.put(String.valueOf(e.getKey()), n.intValue());
                    }
                }
                categoryScores = parsed;
            }
            putCount(counts, "critical", entity.summary.get("criticalCount"));
            putCount(counts, "high", entity.summary.get("highCount"));
            putCount(counts, "medium", entity.summary.get("mediumCount"));
            putCount(counts, "low", entity.summary.get("lowCount"));
            putCount(counts, "info", entity.summary.get("infoCount"));
        }
        return new AssessmentRunSummary(
                entity.id,
                entity.targetId,
                entity.profile,
                entity.score,
                entity.status,
                parseTrigger(entity.triggerType),
                entity.startedAt,
                entity.completedAt,
                entity.createdAt,
                completeness,
                confidence,
                categoryScores,
                counts.isEmpty() ? null : Map.copyOf(counts));
    }

    public Finding toDomainFinding(AssessmentFindingEntity entity) {
        EvidenceSubject subject = null;
        if (entity.resourceType != null || entity.resourceId != null) {
            SubjectType type = parseSubjectType(entity.resourceType);
            subject = new EvidenceSubject(type, entity.resourceId, entity.resourceName);
        }
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
                entity.references,
                subject);
    }

    /**
     * Maps engine status to lifecycle. PASS/SKIPPED/NOT_EVALUATED → RESOLVED (not persisted as OPEN).
     * OPEN/FAIL/WARNING → OPEN.
     */
    public static FindingLifecycleStatus toLifecycle(FindingStatus status) {
        if (status == null) {
            return FindingLifecycleStatus.OPEN;
        }
        return switch (status) {
            case PASS, SKIPPED, NOT_EVALUATED -> FindingLifecycleStatus.RESOLVED;
            case OPEN, FAIL, WARNING -> FindingLifecycleStatus.OPEN;
        };
    }

    private static Map<String, Integer> findingCounts(AssessmentResult result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("critical", 0);
        counts.put("high", 0);
        counts.put("medium", 0);
        counts.put("low", 0);
        counts.put("info", 0);
        if (result.findings() == null) {
            return counts;
        }
        for (Finding finding : result.findings()) {
            if (finding == null || finding.status() == FindingStatus.PASS
                    || finding.status() == FindingStatus.NOT_EVALUATED
                    || finding.status() == FindingStatus.SKIPPED) {
                continue;
            }
            String key = finding.severity() == null ? "info" : finding.severity().name().toLowerCase();
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private static void putCount(Map<String, Integer> counts, String key, Object raw) {
        if (raw instanceof Number n) {
            counts.put(key, n.intValue());
        }
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

    private static SubjectType parseSubjectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return SubjectType.TARGET;
        }
        try {
            return SubjectType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return SubjectType.TARGET;
        }
    }
}
