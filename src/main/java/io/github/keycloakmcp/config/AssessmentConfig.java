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
}
