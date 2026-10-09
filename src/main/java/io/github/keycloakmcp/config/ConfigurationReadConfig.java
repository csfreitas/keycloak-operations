package io.github.keycloakmcp.config;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.keycloakmcp.domain.configuration.ConfigurationKind;
import io.smallrye.config.ConfigMapping;

/** Explicit, additive grants. Empty configuration grants no access on either transport. */
@ConfigMapping(prefix = "platform.configuration-reads")
public interface ConfigurationReadConfig {
    Map<String, Scope> scopes();

    interface Scope {
        String role();
        String targetId();
        String realm();
        String realmId();
        ConfigurationKind kind();
        Optional<String> clientId();
        Set<String> fields();
        Optional<Set<String>> restClientIds();
        Optional<Set<String>> mcpClientIds();
    }
}
