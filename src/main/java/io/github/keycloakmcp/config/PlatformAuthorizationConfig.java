package io.github.keycloakmcp.config;

import java.util.Map;
import java.util.Set;

import io.github.keycloakmcp.target.TargetPermission;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Identity A grants. No role, target wildcard, or administrative inheritance is implicit. */
@ConfigMapping(prefix = "platform.authorization")
public interface PlatformAuthorizationConfig {
    @WithDefault("authenticated")
    String mode();

    Map<String, Grant> grants();

    interface Grant {
        Set<String> targets();

        Set<TargetPermission> permissions();
    }
}
