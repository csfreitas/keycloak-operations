package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.config.AssessmentConfig;

class DeclarativeRuleValidationTest {

    private YamlRuleLoader loader;

    @BeforeEach
    void setUp() {
        loader = new YamlRuleLoader(fullConfig());
        loader.init();
    }

    @Test
    void indexLoadsAllPacksIncludingAdminSecurity() {
        assertThat(loader.rulesByPack())
                .containsKeys("ha", "health-check", "security-baseline", "capacity", "admin-security");
        assertThat(loader.loadBuiltInAndClasspathRules())
                .extracting(Rule::id)
                .contains("KC-OCP-HA-001", "KC-HA-002", "KC-SEC-001", "KC-PROD-001", "KC-CAP-001", "KC-ADM-001");
    }

    @Test
    void profileFiltersRulePacks() {
        AssessmentProfile haOnly = new AssessmentProfile("ha-only", List.of("ha"));
        assertThat(loader.loadForProfile(haOnly))
                .extracting(Rule::id)
                .contains("KC-OCP-HA-001", "KC-HA-006")
                .doesNotContain("KC-SEC-001");
    }

    @Test
    void unknownPackFailsForProfile() {
        AssessmentProfile bad = new AssessmentProfile("bad", List.of("does-not-exist"));
        assertThatThrownBy(() -> loader.loadForProfile(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown rule pack");
    }

    @Test
    void conditionValidateAcceptsAllAny() {
        ConditionEvaluator.validate(Map.of(
                "all",
                List.of(
                        Map.of("key", "deployment.replicas", "greaterThanOrEqual", 2),
                        Map.of("key", "keycloak.pdb.present", "equals", false))));
    }

    private static AssessmentConfig fullConfig() {
        return new AssessmentConfig() {
            @Override
            public String rulesPath() {
                return "rules";
            }

            @Override
            public String defaultProfile() {
                return "keycloak-production";
            }

            @Override
            public int maxRealms() {
                return 50;
            }

            @Override
            public int maxClientsPerRealm() {
                return 100;
            }

            @Override
            public String expectedAvailabilityZones() {
                return "AUTO";
            }

            @Override
            public boolean includeMasterRealm() {
                return false;
            }
        };
    }
}
