package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.config.TestAssessmentConfig;

/**
 * Fixture-based evaluation of declarative HA / security / capacity packs.
 */
class RulePackFixtureTest {

    private YamlRuleLoader loader;
    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        loader = new YamlRuleLoader(TestAssessmentConfig.defaults());
        loader.init();
        ruleEngine = new RuleEngine();
    }

    @Test
    void healthyHaDoesNotEmitHaFindings() {
        EvidenceContext ctx = context(List.of(
                ev("runtime.type", "OPENSHIFT"),
                ev("target.environment", "PRD"),
                ev("deployment.replicas", 3),
                ev("keycloak.replicas.readyBelowDesired", false),
                ev("keycloak.topology.singleZoneConcentration", false),
                ev("keycloak.topology.singleNodeConcentration", false),
                ev("keycloak.scheduling.zoneSpread.present", true),
                ev("keycloak.scheduling.hostnameSpread.present", true),
                ev("keycloak.pdb.present", true),
                ev("keycloak.hpa.present", true),
                ev("keycloak.hpa.minReplicas", 2)));

        List<Finding> findings = actionable(haRules(), ctx);
        assertThat(findings).isEmpty();
    }

    @Test
    void singleReplicaEmitsKcOcpHa001() {
        EvidenceContext ctx = context(List.of(
                ev("runtime.type", "KUBERNETES"),
                ev("deployment.replicas", 1)));

        List<Finding> findings = actionable(haRules(), ctx);
        assertThat(findings).extracting(Finding::id).contains("KC-OCP-HA-001");
    }

    @Test
    void brokenMultiAzEmitsConcentrationAndSpread() {
        EvidenceContext ctx = context(List.of(
                ev("runtime.type", "OPENSHIFT"),
                ev("target.environment", "PRD"),
                ev("deployment.replicas", 3),
                ev("keycloak.replicas.readyBelowDesired", false),
                ev("keycloak.topology.singleZoneConcentration", true),
                ev("keycloak.topology.singleNodeConcentration", false),
                ev("keycloak.scheduling.zoneSpread.present", false),
                ev("keycloak.scheduling.hostnameSpread.present", false),
                ev("keycloak.pdb.present", true),
                ev("keycloak.hpa.present", false)));

        List<Finding> findings = actionable(haRules(), ctx);
        assertThat(findings)
                .extracting(Finding::id)
                .contains("KC-HA-003", "KC-HA-004", "KC-HA-005");
    }

    @Test
    void hpaUnsafeInProduction() {
        EvidenceContext ctx = context(List.of(
                ev("runtime.type", "OPENSHIFT"),
                ev("target.environment", "PRD"),
                ev("deployment.replicas", 3),
                ev("keycloak.replicas.readyBelowDesired", false),
                ev("keycloak.topology.singleZoneConcentration", false),
                ev("keycloak.topology.singleNodeConcentration", false),
                ev("keycloak.scheduling.zoneSpread.present", true),
                ev("keycloak.scheduling.hostnameSpread.present", true),
                ev("keycloak.pdb.present", true),
                ev("keycloak.hpa.present", true),
                ev("keycloak.hpa.minReplicas", 1)));

        List<Finding> findings = actionable(haRules(), ctx);
        assertThat(findings).extracting(Finding::id).contains("KC-HA-007");
    }

    @Test
    void securityBaselineFiresOnWeakRealm() {
        AssessmentProfile sec = new AssessmentProfile("sec", List.of("security-baseline"));
        List<Rule> rules = loader.loadForProfile(sec);
        EvidenceContext ctx = context(List.of(
                ev("realm.bruteForceProtected", false),
                ev("realm.sslRequired", "none"),
                ev("keycloak.clients.wildcardRedirectUri", 2),
                ev("keycloak.clients.wildcardWebOrigin", 1),
                ev("keycloak.clients.implicitFlowCount", 1),
                ev("keycloak.clients.publicWithoutPkceS256", 3),
                ev("keycloak.management.publiclyExposed", true)));

        List<Finding> findings = actionable(rules, ctx);
        assertThat(findings)
                .extracting(Finding::id)
                .contains(
                        "KC-SEC-001",
                        "KC-SEC-002",
                        "KC-SEC-003",
                        "KC-SEC-004",
                        "KC-SEC-005",
                        "KC-SEC-006",
                        "KC-SEC-007");
    }

    @Test
    void capacityMissingResources() {
        AssessmentProfile cap = new AssessmentProfile("cap", List.of("capacity"));
        EvidenceContext ctx = context(List.of(
                ev("runtime.type", "KUBERNETES"),
                ev("keycloak.resources.requests.cpu.present", false),
                ev("keycloak.resources.requests.memory.present", false),
                ev("keycloak.resources.limits.memory.present", false)));

        List<Finding> findings = actionable(loader.loadForProfile(cap), ctx);
        assertThat(findings).extracting(Finding::id).containsExactlyInAnyOrder(
                "KC-CAP-001", "KC-CAP-002", "KC-CAP-003");
    }

    private List<Rule> haRules() {
        return loader.loadForProfile(new AssessmentProfile("ha", List.of("ha")));
    }

    private List<Finding> actionable(List<Rule> rules, EvidenceContext ctx) {
        return ruleEngine.evaluate(rules, ctx);
    }

    private static EvidenceContext context(List<Evidence> evidence) {
        return new EvidenceContext("fixture-target", evidence);
    }

    private static Evidence ev(String key, Object value) {
        return new Evidence("fixture-target", "test", "fixture", key, value, Instant.now());
    }
}
