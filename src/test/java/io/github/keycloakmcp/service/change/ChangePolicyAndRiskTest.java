package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ChangePolicyDecision;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import io.github.keycloakmcp.target.TargetEnvironment;

class ChangePolicyAndRiskTest {

    private final ChangeRiskClassifier riskClassifier = new ChangeRiskClassifier();
    private final ChangePolicyEvaluator policyEvaluator = new ChangePolicyEvaluator();
    private final ChangePlanFingerprinter fingerprinter = new ChangePlanFingerprinter();

    @Test
    void classifiesNameAsLowAndPkceAsMedium() {
        assertThat(riskClassifier.classifyProperty("name")).isEqualTo(ChangeRisk.LOW);
        assertThat(riskClassifier.classifyProperty("pkceCodeChallengeMethod")).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(riskClassifier.classify(List.of(
                new ChangeOperation("name", ChangeOperationType.UPDATE, "a", "b"),
                new ChangeOperation("pkceCodeChallengeMethod", ChangeOperationType.UPDATE, null, "S256"))))
                .isEqualTo(ChangeRisk.MEDIUM);
    }

    @Test
    void productionRequiresApproval() {
        var result = policyEvaluator.evaluate(
                TargetEnvironment.PRD, ChangeOperationType.UPDATE, ChangeRisk.LOW, false);
        assertThat(result.decision()).isEqualTo(ChangePolicyDecision.APPROVAL_REQUIRED);
        assertThat(result.requiresApproval()).isTrue();
    }

    @Test
    void devLowRiskAllowsWithoutApproval() {
        var result = policyEvaluator.evaluate(
                TargetEnvironment.DEV, ChangeOperationType.UPDATE, ChangeRisk.LOW, false);
        assertThat(result.decision()).isEqualTo(ChangePolicyDecision.ALLOW);
        assertThat(result.requiresApproval()).isFalse();
    }

    @Test
    void destructiveDenied() {
        var result = policyEvaluator.evaluate(
                TargetEnvironment.DEV, ChangeOperationType.UPDATE, ChangeRisk.LOW, true);
        assertThat(result.decision()).isEqualTo(ChangePolicyDecision.DENY);
    }

    @Test
    void planFingerprintChangesWhenOperationsChange() {
        List<ChangeOperation> a = List.of(
                new ChangeOperation("name", ChangeOperationType.UPDATE, "old", "new"));
        List<ChangeOperation> b = List.of(
                new ChangeOperation("name", ChangeOperationType.UPDATE, "old", "other"));
        String fa = fingerprinter.fingerprintPlan("t", "r", "CLIENT", "c", "UPDATE", a);
        String fb = fingerprinter.fingerprintPlan("t", "r", "CLIENT", "c", "UPDATE", b);
        assertThat(fa).isNotEqualTo(fb);
        assertThat(fa).hasSize(64);
    }
}
