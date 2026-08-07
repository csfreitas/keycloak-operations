package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class TargetRegistryTest {

    private McpRuntimeConfig runtimeConfig;
    private ConfigurationTargetRegistry registry;

    @BeforeEach
    void setUp() {
        runtimeConfig = mock(McpRuntimeConfig.class);
        Map<String, McpRuntimeConfig.TargetEntry> targets = new LinkedHashMap<>();
        targets.put("lab-a", targetEntry("Lab A", "KEYCLOAK", "DEV", true, "http://kc-a:8080", "cred-a"));
        targets.put("lab-b", targetEntry("Lab B", "RHBK", "PRD", true, "http://kc-b:8080", "cred-b"));
        when(runtimeConfig.targets()).thenReturn(targets);
        registry = new ConfigurationTargetRegistry(runtimeConfig);
        registry.load();
    }

    @Test
    void listsConfiguredTargets() {
        assertThat(registry.list()).hasSize(2);
        assertThat(registry.list()).extracting(t -> t.id().value()).containsExactly("lab-a", "lab-b");
    }

    @Test
    void findByIdReturnsTarget() {
        Optional<Target> found = registry.findById("lab-a");
        assertThat(found).isPresent();
        assertThat(found.get().displayName()).isEqualTo("Lab A");
        assertThat(found.get().keycloak().url()).isEqualTo("http://kc-a:8080");
        assertThat(found.get().keycloak().credentialRef()).isEqualTo("cred-a");
    }

    @Test
    void requireUnknownThrowsTargetNotFound() {
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.TARGET_NOT_FOUND));
    }

    private static McpRuntimeConfig.TargetEntry targetEntry(
            String displayName,
            String type,
            String environment,
            boolean enabled,
            String url,
            String credentialRef) {
        McpRuntimeConfig.KeycloakEntry keycloak = mock(McpRuntimeConfig.KeycloakEntry.class);
        when(keycloak.url()).thenReturn(url);
        when(keycloak.authRealm()).thenReturn("master");
        when(keycloak.clientId()).thenReturn("keycloak-mcp");
        when(keycloak.credentialRef()).thenReturn(credentialRef);

        McpRuntimeConfig.TargetEntry entry = mock(McpRuntimeConfig.TargetEntry.class);
        when(entry.displayName()).thenReturn(displayName);
        when(entry.type()).thenReturn(type);
        when(entry.environment()).thenReturn(environment);
        when(entry.enabled()).thenReturn(enabled);
        when(entry.keycloak()).thenReturn(keycloak);
        when(entry.infrastructure()).thenReturn(Optional.empty());
        when(entry.observability()).thenReturn(Optional.empty());
        when(entry.tags()).thenReturn(Map.of());
        return entry;
    }
}
