package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.profile.AssessmentProfileResolver;
import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.assessment.scoring.AssessmentScoring;
import io.github.keycloakmcp.collector.AssessmentEvidenceService;
import io.github.keycloakmcp.config.TestAssessmentConfig;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

/**
 * Ensures assessment findings / evidence stay scoped to the requested target.
 */
class MultiTargetAssessmentIsolationTest {

    private AssessmentEngine engine;
    private AssessmentEvidenceService evidenceService;

    @BeforeEach
    void setUp() {
        evidenceService = mock(AssessmentEvidenceService.class);
        YamlRuleLoader loader = new YamlRuleLoader(TestAssessmentConfig.defaults());
        loader.init();
        engine = new AssessmentEngine(
                new ProfileRegistry(),
                new AssessmentProfileResolver(),
                loader,
                new RuleEngine(),
                new AssessmentScoring(),
                TestAssessmentConfig.defaults(),
                evidenceService,
                mock(McpMetrics.class));
    }

    @Test
    void findingsCarryOnlyAssessedTargetId() {
        Target targetA = target("target-a");
        Target targetB = target("target-b");

        when(evidenceService.collect(any())).thenAnswer(inv -> {
            Target t = inv.getArgument(0);
            String id = t.id().value();
            List<Evidence> evidence = List.of(
                    new Evidence(id, "keycloak", "server", "keycloak.product", "KEYCLOAK", Instant.now()),
                    new Evidence(id, "keycloak", "server", "keycloak.version", "26.7.1", Instant.now()),
                    new Evidence(id, "infrastructure", "runtime", "runtime.type", "KUBERNETES", Instant.now()),
                    new Evidence(id, "infrastructure", "workload", "deployment.replicas", 1, Instant.now()));
            return new AssessmentEvidenceService.EvidenceCollectionResult(
                    evidence, List.of("keycloak", "infrastructure", "target"), List.of());
        });

        AssessmentResult resultA = engine.assess(targetA, "keycloak-kubernetes-production");
        AssessmentResult resultB = engine.assess(targetB, "keycloak-kubernetes-production");

        assertThat(resultA.targetId()).isEqualTo("target-a");
        assertThat(resultB.targetId()).isEqualTo("target-b");
        assertThat(resultA.findings()).allMatch(f -> "target-a".equals(f.targetId()));
        assertThat(resultB.findings()).allMatch(f -> "target-b".equals(f.targetId()));
        assertThat(resultA.evidence()).allMatch(e -> "target-a".equals(e.targetId()));
        assertThat(resultB.evidence()).allMatch(e -> "target-b".equals(e.targetId()));
    }

    @Test
    void partialKeycloakCollectionNeverYieldsConfidentCompleteScore() {
        Target target = target("target-a");
        when(evidenceService.collect(target)).thenReturn(new AssessmentEvidenceService.EvidenceCollectionResult(
                List.of(new Evidence("target-a", "keycloak", "server", "keycloak.product", "KEYCLOAK", Instant.EPOCH),
                        new Evidence("target-a", "keycloak", "realm", "realm.bruteForceProtected", true,
                                Instant.EPOCH, EvidenceSubject.realm("safe"))),
                List.of("keycloak"), List.of(), List.of("keycloak")));

        AssessmentResult result = engine.assess(target, "keycloak-production");

        assertThat(result.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(result.confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(result.evidenceCompleteness()).isLessThan(100);
        assertThat(result.scoreAvailable()).isFalse();
    }

    @Test
    void noEvaluatedRulesNeverAdvertisesAvailableScore() {
        Target target = target("target-a");
        when(evidenceService.collect(target)).thenReturn(new AssessmentEvidenceService.EvidenceCollectionResult(
                List.of(), List.of(), List.of("keycloak")));

        AssessmentResult result = engine.assess(target, "keycloak-production");

        assertThat(result.rulesEvaluated()).isZero();
        assertThat(result.evidenceCompleteness()).isZero();
        assertThat(result.confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(result.scoreAvailable()).isFalse();
        assertThat(result.categoryScores()).isEmpty();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        assertThat(mapper.convertValue(result, Map.class)).containsEntry("scoreAvailable", false);
        assertThat(new io.github.keycloakmcp.security.SensitiveDataFilter(mapper).redact(result).scoreAvailable()).isFalse();
    }

    @Test
    void missingRulesCapConfidenceEvenWhenAllSourcesReturned() {
        Target base = target("target-a");
        Target target = new Target(base.id(), base.displayName(), base.type(), base.environment(), true,
                base.keycloak(), new io.github.keycloakmcp.target.InfrastructureTargetConfiguration(
                        io.github.keycloakmcp.target.InfrastructureType.KUBERNETES, "cluster", "namespace", "ref"),
                null, Map.of());
        when(evidenceService.collect(target)).thenReturn(new AssessmentEvidenceService.EvidenceCollectionResult(
                List.of(new Evidence("target-a", "keycloak", "server", "keycloak.product", "KEYCLOAK", Instant.EPOCH),
                        new Evidence("target-a", "keycloak", "realm", "realm.bruteForceProtected", true,
                                Instant.EPOCH, EvidenceSubject.realm("safe"))),
                List.of("keycloak", "infrastructure"), List.of()));

        AssessmentResult result = engine.assess(target, "keycloak-production");
        assertThat(result.rulesEvaluated()).isGreaterThan(0);
        assertThat(result.rulesNotEvaluated()).isGreaterThan(0);
        assertThat(result.confidence()).isEqualTo(AssessmentConfidence.MEDIUM);
        assertThat(result.scoreAvailable()).isFalse();
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedHelperWithoutEvidenceCannotAdvertiseComplete() {
        AssessmentResult result = engine.run("keycloak-production", List.of());
        assertThat(result.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(result.confidence()).isEqualTo(AssessmentConfidence.LOW);
        assertThat(result.evidenceCompleteness()).isZero();
        assertThat(result.scoreAvailable()).isFalse();
    }

    private static Target target(String id) {
        return new Target(
                new TargetId(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.PRD,
                true,
                new KeycloakTargetConfiguration("http://localhost", "master", "c", "ref"),
                null,
                null,
                Map.of());
    }
}
