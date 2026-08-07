package io.github.keycloakmcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "assessment")
public interface AssessmentConfig {

    @WithName("rules.path")
    @WithDefault("rules")
    String rulesPath();

    @WithName("default-profile")
    @WithDefault("keycloak-production")
    String defaultProfile();

    @WithName("max-realms")
    @WithDefault("50")
    int maxRealms();

    @WithName("max-clients-per-realm")
    @WithDefault("100")
    int maxClientsPerRealm();

    /**
     * Expected availability zones for HA profiles: numeric (1/2/3) or AUTO.
     */
    @WithName("expected-availability-zones")
    @WithDefault("AUTO")
    String expectedAvailabilityZones();

    /**
     * When false, aggregate security evidence ignores the master realm.
     */
    @WithName("include-master-realm")
    @WithDefault("false")
    boolean includeMasterRealm();
}
