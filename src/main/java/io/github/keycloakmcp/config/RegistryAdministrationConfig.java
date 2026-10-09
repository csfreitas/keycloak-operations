package io.github.keycloakmcp.config;

import java.util.Map;
import java.util.Set;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Explicit first-administrator configuration for REST-only, side-effect-free preflight. */
@ConfigMapping(prefix = "platform.registry-administration")
public interface RegistryAdministrationConfig {
    @WithDefault("false")
    boolean enabled();

    Map<String, Administrator> administrators();

    interface Administrator {
        String issuer();
        String subject();
        String role();
        Set<String> restClientIds();
    }
}
