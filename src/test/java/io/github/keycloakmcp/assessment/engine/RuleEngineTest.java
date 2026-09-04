package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.engine.rules.MinimumReplicasRule;

class RuleEngineTest {

    private RuleEngine ruleEngine;
    private MinimumReplicasRule minimumReplicasRule;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngine();
        minimumReplicasRule = new MinimumReplicasRule();
    }

    @Test
    void evidenceRuleFindingPipelineEmitsKcOcpHa001WhenReplicasIsOne() {
        Evidence evidence = new Evidence(
                "local-dev",
                "openshift",
                "deployment",
                MinimumReplicasRule.EVIDENCE_KEY,
                1,
                Instant.now());
        EvidenceContext context = new EvidenceContext("local-dev", List.of(evidence));

        List<Finding> findings = ruleEngine.evaluate(List.of(minimumReplicasRule), context);

        assertThat(findings).hasSize(1);
        Finding finding = findings.get(0);
        assertThat(finding.targetId()).isEqualTo("local-dev");
        assertThat(finding.id()).isEqualTo("KC-OCP-HA-001");
        assertThat(finding.severity()).isEqualTo(Severity.HIGH);
        assertThat(finding.status()).isEqualTo(FindingStatus.OPEN);
        assertThat(finding.category()).isEqualTo("high-availability");
        assertThat(finding.evidence()).containsEntry(MinimumReplicasRule.EVIDENCE_KEY, 1);
    }

    @Test
    void whenReplicasIsThreeNoFailFindingIsEmitted() {
        Evidence evidence = new Evidence(
                "local-dev",
                "openshift",
                "deployment",
                MinimumReplicasRule.EVIDENCE_KEY,
                3,
                Instant.now());
        EvidenceContext context = new EvidenceContext(List.of(evidence));

        List<Finding> findings = ruleEngine.evaluate(List.of(minimumReplicasRule), context);

        assertThat(findings).isEmpty();
        assertThat(findings).noneMatch(f -> "KC-OCP-HA-001".equals(f.id())
                && (f.status() == FindingStatus.FAIL || f.status() == FindingStatus.OPEN));
    }

    @Test
    void ruleDoesNotApplyWhenEvidenceKeyMissing() {
        EvidenceContext context = new EvidenceContext(List.of(
                new Evidence("local-dev", "keycloak", "server", "keycloak.version", "26.7.1", Instant.now())));

        List<Finding> findings = ruleEngine.evaluate(List.of(minimumReplicasRule), context);

        assertThat(findings).isEmpty();
    }

    @Test
    void realmRulesEvaluateEveryRealmIndependentlyAndIgnoreInputOrder() {
        Evidence secure = realmEvidence("safe", "realm.bruteForceProtected", true);
        Evidence insecure = realmEvidence("unsafe", "realm.bruteForceProtected", false);
        Rule rule = realmRule();

        var forward = ruleEngine.evaluateDetailed(List.of(rule), new EvidenceContext(List.of(secure, insecure)));
        var reversed = ruleEngine.evaluateDetailed(List.of(rule), new EvidenceContext(List.of(insecure, secure)));

        assertThat(forward.findings()).isEqualTo(reversed.findings());
        assertThat(forward.rulesEvaluated()).isEqualTo(2);
        assertThat(forward.rulesMatched()).isEqualTo(1);
        assertThat(forward.findings()).anySatisfy(f -> {
            assertThat(f.subject()).isEqualTo(EvidenceSubject.realm("unsafe"));
            assertThat(f.status()).isEqualTo(FindingStatus.OPEN);
        });
        assertThat(new EvidenceContext(List.of(secure, insecure)).get("realm.bruteForceProtected")).isEmpty();
    }

    @Test
    void unavailableRealmIsNotEvaluatedAndCannotBorrowAnotherRealmsEvidence() {
        var result = ruleEngine.evaluateDetailed(List.of(realmRule()), new EvidenceContext(List.of(
                realmEvidence("safe", "realm.bruteForceProtected", true),
                realmEvidence("denied", "realm.name", "denied"))));

        assertThat(result.rulesEvaluated()).isEqualTo(1);
        assertThat(result.rulesNotEvaluated()).isEqualTo(1);
        assertThat(result.findings()).anySatisfy(f -> {
            assertThat(f.subject()).isEqualTo(EvidenceSubject.realm("denied"));
            assertThat(f.status()).isEqualTo(FindingStatus.NOT_EVALUATED);
        });
    }

    @Test
    void missingRealmPropertyIsNotEvidenceForPass() {
        var result = ruleEngine.evaluateDetailed(List.of(realmRule()), new EvidenceContext(List.of(
                realmEvidence("unknown", "realm.bruteForceProtected", null))));
        assertThat(result.rulesNotEvaluated()).isEqualTo(1);
        assertThat(result.findings()).extracting(Finding::status).containsExactly(FindingStatus.NOT_EVALUATED);
    }

    @Test
    void mixedTargetEvidenceIsRejectedInBothContextConstructors() {
        var local = realmEvidence("app", "realm.bruteForceProtected", true);
        var foreign = new Evidence("other-target", "keycloak", "realm", "realm.bruteForceProtected",
                false, Instant.EPOCH, EvidenceSubject.realm("app"));
        assertThatThrownBy(() -> new EvidenceContext(List.of(local, foreign)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceContext("local-dev", List.of(foreign)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyGlobalAggregateCannotReplaceMissingScopedRealmProperty() {
        var result = ruleEngine.evaluateDetailed(List.of(realmRule()), new EvidenceContext(List.of(
                realmEvidence("denied", "realm.name", "denied"),
                new Evidence("local-dev", "keycloak", "realm", "realm.bruteForceProtected", true, Instant.EPOCH))));
        assertThat(result.rulesNotEvaluated()).isEqualTo(1);
        assertThat(result.findings()).extracting(Finding::status).containsExactly(FindingStatus.NOT_EVALUATED);
    }

    private static Evidence realmEvidence(String realm, String key, Object value) {
        return new Evidence("local-dev", "keycloak", "realm", key, value, Instant.EPOCH, EvidenceSubject.realm(realm));
    }

    private static Rule realmRule() {
        return new DeclarativeRule("KC-SEC-001", "Brute force protection", "security", Severity.HIGH,
                "Protection absent", "Risk", "Enable protection", List.of(), "security-baseline",
                Map.of("key", "realm.bruteForceProtected", "equals", false), List.of(), List.of(), List.of(),
                List.of(), List.of("realm.bruteForceProtected"), null, null, null, SubjectType.REALM);
    }
}
