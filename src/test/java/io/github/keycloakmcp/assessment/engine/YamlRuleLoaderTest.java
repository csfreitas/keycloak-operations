package io.github.keycloakmcp.assessment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.config.AssessmentConfig;

class YamlRuleLoaderTest {

    @Test
    void loadsPacksFromIndexAndFiltersByProfile() {
        AssessmentConfig assessmentConfig = new AssessmentConfig() {
            @Override
            public String rulesPath() {
                return "rules";
            }

            @Override
            public String defaultProfile() {
                return "keycloak-production";
            }
        };

        YamlRuleLoader loader = new YamlRuleLoader(assessmentConfig);
        loader.init();

        assertThat(loader.rulesByPack()).containsKeys("ha", "health-check", "security-baseline", "capacity");
        assertThat(loader.loadBuiltInAndClasspathRules())
                .extracting(Rule::id)
                .contains("KC-OCP-HA-001");

        AssessmentProfile haProfile = new AssessmentProfile("test-ha", java.util.List.of("ha"));
        assertThat(loader.loadForProfile(haProfile))
                .extracting(Rule::id)
                .contains("KC-OCP-HA-001");

        AssessmentProfile securityOnly = new AssessmentProfile("sec", java.util.List.of("security-baseline"));
        assertThat(loader.loadForProfile(securityOnly))
                .extracting(Rule::id)
                .doesNotContain("KC-OCP-HA-001");
    }
}
