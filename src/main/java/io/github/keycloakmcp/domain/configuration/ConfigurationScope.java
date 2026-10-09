package io.github.keycloakmcp.domain.configuration;

import java.util.List;

/** Authorized operator-defined handle; not an upstream realm/client inventory. */
public record ConfigurationScope(String scopeId, String targetId, String realm,
        ConfigurationKind kind, List<String> fields) {
    public ConfigurationScope { fields = List.copyOf(fields); }
}
