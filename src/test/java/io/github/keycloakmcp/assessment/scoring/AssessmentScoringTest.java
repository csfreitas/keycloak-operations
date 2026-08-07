package io.github.keycloakmcp.assessment.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.assessment.engine.FindingStatus;
import io.github.keycloakmcp.assessment.engine.Severity;

class AssessmentScoringTest {

    private AssessmentScoring scoring;

    @BeforeEach
    void setUp() {
        scoring = new AssessmentScoring();
    }

    @Test
    void emptyFindingsYieldPerfectScore() {
        assertThat(scoring.score(List.of())).isEqualTo(100);
        assertThat(scoring.score(null)).isEqualTo(100);
    }

    @Test
    void appliesSeverityPenaltiesAndFloorsAtZero() {
        List<Finding> findings = List.of(
                finding("A", Severity.CRITICAL),
                finding("B", Severity.HIGH),
                finding("C", Severity.MEDIUM),
                finding("D", Severity.LOW));

        // 100 - 25 - 15 - 8 - 3 = 49
        assertThat(scoring.score(findings)).isEqualTo(49);
    }

    @Test
    void passFindingsDoNotReduceScore() {
        Finding pass = new Finding(
                "local-dev",
                "PASS-1",
                "ok",
                "ha",
                Severity.HIGH,
                FindingStatus.PASS,
                "ok",
                Map.of(),
                "",
                "",
                List.of());

        assertThat(scoring.score(List.of(pass))).isEqualTo(100);
    }

    @Test
    void scoreNeverGoesBelowZero() {
        List<Finding> manyCritical = List.of(
                finding("C1", Severity.CRITICAL),
                finding("C2", Severity.CRITICAL),
                finding("C3", Severity.CRITICAL),
                finding("C4", Severity.CRITICAL),
                finding("C5", Severity.CRITICAL));

        assertThat(scoring.score(manyCritical)).isZero();
    }

    private static Finding finding(String id, Severity severity) {
        return new Finding(
                "local-dev",
                id,
                "title-" + id,
                "category",
                severity,
                FindingStatus.OPEN,
                "description",
                Map.of(),
                "impact",
                "recommendation",
                List.of());
    }
}
