package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

class InstallationConfigMappingTest {
    private Map<String, String> properties() {
        var values = new LinkedHashMap<String, String>();
        String prefix = "mcp.targets.bound.";
        values.put(prefix + "display-name", "Bound");
        values.put(prefix + "type", "RHBK");
        values.put(prefix + "environment", "TEST");
        values.put(prefix + "keycloak.url", "http://localhost:8080");
        values.put(prefix + "keycloak.client-id", "assessor");
        values.put(prefix + "keycloak.credential-ref", "iam-ref");
        values.put(prefix + "infrastructure.type", "KUBERNETES");
        values.put(prefix + "infrastructure.cluster-id", "lab");
        values.put(prefix + "infrastructure.namespace", "iam");
        values.put(prefix + "infrastructure.credential-ref", "cluster-ref");
        return values;
    }

    private McpRuntimeConfig mapping(Map<String, String> values) {
        return new SmallRyeConfigBuilder().withMapping(McpRuntimeConfig.class).withDefaultValues(values)
                .build().getConfigMapping(McpRuntimeConfig.class);
    }

    @Test
    void legacyConfigurationRemainsUnbound() {
        assertThat(mapping(properties()).targets().get("bound").infrastructure().orElseThrow().installation()).isEmpty();
    }

    @Test
    void completePropertiesReachDomainBinding() {
        var values = properties();
        values.put("mcp.targets.bound.infrastructure.installation.api-version", "apps/v1");
        values.put("mcp.targets.bound.infrastructure.installation.kind", "StatefulSet");
        values.put("mcp.targets.bound.infrastructure.installation.name", "rhbk");
        values.put("mcp.targets.bound.infrastructure.installation.uid", "approved-uid");
        var registry = new ConfigurationTargetRegistry(mapping(values));
        registry.load();
        assertThat(registry.require("bound").infrastructure().installation()).isEqualTo(
                new KubernetesInstallationBinding("apps/v1", "StatefulSet", "rhbk", "approved-uid"));
    }

    @Test
    void partialPropertiesFailConfigurationValidation() {
        var values = properties();
        values.put("mcp.targets.bound.infrastructure.installation.name", "rhbk");
        assertThatThrownBy(() -> mapping(values)).isInstanceOf(io.smallrye.config.ConfigValidationException.class);
    }
}
