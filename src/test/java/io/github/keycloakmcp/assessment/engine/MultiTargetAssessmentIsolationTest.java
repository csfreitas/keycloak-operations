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
