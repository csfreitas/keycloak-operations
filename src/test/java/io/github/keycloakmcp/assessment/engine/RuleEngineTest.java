package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

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
}
