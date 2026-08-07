package io.github.keycloakmcp.config;

/**
 * Shared test stub for {@link AssessmentConfig}.
 */
public final class TestAssessmentConfig {

    private TestAssessmentConfig() {
    }

    public static AssessmentConfig defaults() {
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
