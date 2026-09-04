package io.github.keycloakmcp.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.assessment.engine.AssessmentConfidence;
import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.AssessmentScope;
import io.github.keycloakmcp.assessment.engine.AssessmentStatus;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.security.SensitiveDataFilter;

class AssessmentPersistenceMapperTest {
    private final SensitiveDataFilter filter = new SensitiveDataFilter(new ObjectMapper().findAndRegisterModules());
    private final AssessmentPersistenceMapper mapper = new AssessmentPersistenceMapper(filter);

    @Test
    void persistsTrustedEvaluationMarkerAndPreservesItThroughRedaction() {
        var entity = mapper.toRunEntity(result(AssessmentStatus.COMPLETE, 100, 2), "run", TriggerType.API);
        assertThat(entity.summary).containsEntry("scoreAvailable", true)
                .containsEntry("evaluationRevision", AssessmentPersistenceMapper.SCORE_EVALUATION_REVISION);
        var summary = filter.redact(mapper.toSummary(entity));
        assertThat(summary.scoreAvailable()).isTrue();
        assertThat(summary.evaluationRevision()).isEqualTo(AssessmentPersistenceMapper.SCORE_EVALUATION_REVISION);
    }

    @Test
    void historicalCompleteHundredWithoutMarkerIsUnknown() {
        var entity = mapper.toRunEntity(result(AssessmentStatus.COMPLETE, 100, 2), "run", TriggerType.API);
        entity.summary = Map.of("overallScore", 100, "evidenceCompleteness", 100);
        var summary = mapper.toSummary(entity);
        assertThat(summary.score()).isEqualTo(100);
        assertThat(summary.scoreAvailable()).isNull();
        assertThat(summary.evaluationRevision()).isNull();
    }

    @Test
    void unknownEvaluationRevisionDoesNotCertifyHistory() {
        var entity = mapper.toRunEntity(result(AssessmentStatus.COMPLETE, 100, 2), "run", TriggerType.API);
        entity.summary = Map.of("scoreAvailable", true, "evaluationRevision", "unknown-version");
        assertThat(mapper.toSummary(entity).scoreAvailable()).isNull();
    }

    @Test
    void partialAndUnevaluatedResultsRemainUnavailable() {
        assertThat(mapper.toSummary(mapper.toRunEntity(
                result(AssessmentStatus.PARTIAL, 100, 2), "partial", TriggerType.API)).scoreAvailable()).isFalse();
        assertThat(mapper.toSummary(mapper.toRunEntity(
                result(AssessmentStatus.COMPLETE, 100, 0), "empty", TriggerType.API)).scoreAvailable()).isFalse();
    }

    @Test
    void markerCannotOverrideContradictoryStoredCoverage() {
        var entity = mapper.toRunEntity(result(AssessmentStatus.COMPLETE, 100, 2), "run", TriggerType.API);
        entity.evidenceCompleteness = 99;
        assertThat(mapper.toSummary(entity).scoreAvailable()).isFalse();
        entity.evidenceCompleteness = 100;
        entity.rulesEvaluated = 0;
        assertThat(mapper.toSummary(entity).scoreAvailable()).isFalse();
        entity.rulesEvaluated = 2;
        entity.rulesNotEvaluated = 1;
        assertThat(mapper.toSummary(entity).scoreAvailable()).isFalse();
    }

    @Test
    void contradictoryCompleteResultCannotPersistAnAvailableScore() {
        for (AssessmentResult result : List.of(
                result(AssessmentStatus.COMPLETE, 100, 2, 1, List.of()),
                result(AssessmentStatus.COMPLETE, 100, 2, 0, List.of("unavailable-source")))) {
            var entity = mapper.toRunEntity(result, "contradictory", TriggerType.API);
            assertThat(entity.summary).containsEntry("scoreAvailable", false);
            assertThat(mapper.toSummary(entity).scoreAvailable()).isFalse();
        }
    }

    private static AssessmentResult result(AssessmentStatus status, int completeness, int evaluated) {
        return result(status, completeness, evaluated, 0, List.of());
    }

    private static AssessmentResult result(AssessmentStatus status, int completeness, int evaluated,
            int notEvaluated, List<String> missingEvidence) {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        return new AssessmentResult("run", "target-a", "profile", AssessmentScope.target("target-a"),
                status, 100, Map.of("security", 100), completeness, AssessmentConfidence.MEDIUM,
                evaluated, 0, 0, notEvaluated, missingEvidence, List.of(), List.of(), now, now);
    }
}
