package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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

    @Test
    void structuredFingerprintsAreStableForNormalizedUrlSets() {
        List<ChangeOperation> a = List.of(new ChangeOperation(
                "redirectUris",
                ChangeOperationType.UPDATE,
                List.of("https://old.example/callback"),
                List.of("https://a.example/callback", "https://b.example/callback")));
        List<ChangeOperation> b = List.of(new ChangeOperation(
                "redirectUris",
                ChangeOperationType.UPDATE,
                List.of("https://old.example/callback"),
                List.of("https://a.example/callback", "https://b.example/callback")));

        assertThat(fingerprinter.fingerprintPlan("t", "r", "CLIENT", "c", "UPDATE", a))
                .isEqualTo(fingerprinter.fingerprintPlan("t", "r", "CLIENT", "c", "UPDATE", b));
        assertThat(fingerprinter.fingerprintBaseline(java.util.Map.of(
                "redirectUris", List.of("https://a.example", "https://b.example"))))
                .isEqualTo(fingerprinter.fingerprintBaseline(java.util.Map.of(
                        "redirectUris", List.of("https://a.example", "https://b.example"))));
    }

    @Test
    void legacyScalarFingerprintsRemainCompatibleWithVersionZeroEight() {
        assertThat(fingerprinter.fingerprintBaseline(Map.of("name", "Old", "description", "Desc")))
                .isEqualTo("287ae186fcf7d946321af6f0979c262fc30a09a5f5f2355a26f215aa2a7d8929");
        assertThat(fingerprinter.fingerprintPlan(
                "t",
                "r",
                "CLIENT",
                "c",
                "UPDATE",
                List.of(new ChangeOperation("name", ChangeOperationType.UPDATE, "old", "new"))))
                .isEqualTo("7d1fc17c7675a30dd95f175be74be0d80f41fe4697a0198e77749071821ef119");
    }

    @Test
    void classifiesClientUrlTransitionsAndDeniesUnsafeProductionAdditions() {
        List<ChangeOperation> exactHttps = List.of(new ChangeOperation(
                "redirectUris", ChangeOperationType.UPDATE, List.of(), List.of("https://app.example/callback")));
        List<ChangeOperation> wildcard = List.of(new ChangeOperation(
                "redirectUris", ChangeOperationType.UPDATE, List.of(), List.of("https://app.example/*")));
        List<ChangeOperation> nonLoopbackHttp = List.of(new ChangeOperation(
                "redirectUris", ChangeOperationType.UPDATE, List.of(), List.of("http://app.example/callback")));
        List<ChangeOperation> plusOrigin = List.of(new ChangeOperation(
                "webOrigins", ChangeOperationType.UPDATE, List.of(), List.of("+")));

        assertThat(riskClassifier.classifyClientUrls(exactHttps)).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(riskClassifier.classifyClientUrls(wildcard)).isEqualTo(ChangeRisk.HIGH);
        assertThat(riskClassifier.classifyClientUrls(nonLoopbackHttp)).isEqualTo(ChangeRisk.HIGH);
        assertThat(riskClassifier.classifyClientUrls(plusOrigin)).isEqualTo(ChangeRisk.HIGH);
        assertThat(policyEvaluator.evaluateClientUrls(TargetEnvironment.PRD, ChangeRisk.HIGH, true).decision())
                .isEqualTo(ChangePolicyDecision.DENY);
        assertThat(policyEvaluator.evaluateClientUrls(TargetEnvironment.HML, ChangeRisk.HIGH, true).decision())
                .isEqualTo(ChangePolicyDecision.APPROVAL_REQUIRED);
    }

    @Test
    void classifiesClientSecurityTransitionsAndDeniesUnsafeProductionWeakening() {
        List<ChangeOperation> enablePkce = List.of(new ChangeOperation(
                "pkceCodeChallengeMethod", ChangeOperationType.UPDATE, "NONE", "S256"));
        List<ChangeOperation> disablePkce = List.of(new ChangeOperation(
                "pkceCodeChallengeMethod", ChangeOperationType.UPDATE, "S256", "NONE"));
        List<ChangeOperation> enableImplicit = List.of(new ChangeOperation(
                "implicitFlowEnabled", ChangeOperationType.UPDATE, false, true));
        List<ChangeOperation> disableDirect = List.of(new ChangeOperation(
                "directAccessGrantsEnabled", ChangeOperationType.UPDATE, true, false));
        List<ChangeOperation> becomePublic = List.of(new ChangeOperation(
                "publicClient", ChangeOperationType.UPDATE, false, true));

        assertThat(riskClassifier.classifyClientSecurity(enablePkce)).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(riskClassifier.classifyClientSecurity(disablePkce)).isEqualTo(ChangeRisk.HIGH);
        assertThat(riskClassifier.classifyClientSecurity(enableImplicit)).isEqualTo(ChangeRisk.HIGH);
        assertThat(riskClassifier.classifyClientSecurity(disableDirect)).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(riskClassifier.classifyClientSecurity(becomePublic)).isEqualTo(ChangeRisk.HIGH);
        assertThat(policyEvaluator.evaluateClientSecurity(TargetEnvironment.PRD, ChangeRisk.HIGH, true).decision())
                .isEqualTo(ChangePolicyDecision.DENY);
        assertThat(policyEvaluator.evaluateClientSecurity(TargetEnvironment.HML, ChangeRisk.HIGH, true).decision())
                .isEqualTo(ChangePolicyDecision.APPROVAL_REQUIRED);
    }
}
