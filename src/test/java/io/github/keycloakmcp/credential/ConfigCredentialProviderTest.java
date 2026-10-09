package io.github.keycloakmcp.credential;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;

class ConfigCredentialProviderTest {
    private McpRuntimeConfig.CredentialEntry entry;
    private ConfigCredentialProvider provider;

    @BeforeEach void setup() {
        var config = mock(McpRuntimeConfig.class);
        entry = mock(McpRuntimeConfig.CredentialEntry.class);
        when(config.credentials()).thenReturn(Map.of("fixture", entry));
        provider = new ConfigCredentialProvider(config);
    }

    @Test void emptyCredentialDoesNotBecomeInCluster() {
        assertThatThrownBy(() -> provider.getInfrastructureCredentials("fixture")).isInstanceOf(McpException.class);
    }
    @Test void inClusterRequiresExplicitOptIn() {
        when(entry.inCluster()).thenReturn(true);
        assertThat(provider.getInfrastructureCredentials("fixture").authMode()).isEqualTo(InfrastructureCredentials.AuthMode.IN_CLUSTER);
    }
    @Test void tokenRequiresExplicitServer() {
        when(entry.token()).thenReturn(Optional.of("fixture-secret"));
        assertThatThrownBy(() -> provider.getInfrastructureCredentials("fixture")).isInstanceOf(McpException.class)
                .hasMessageNotContaining("fixture-secret");
    }
    @Test void conflictingModesAreRejectedInsteadOfChoosingByPrecedence() {
        when(entry.inCluster()).thenReturn(true);
        when(entry.token()).thenReturn(Optional.of("fixture-secret"));
        assertThatThrownBy(() -> provider.getInfrastructureCredentials("fixture")).isInstanceOf(McpException.class);
    }
    @Test void explicitTokenBindingIsPreserved() {
        when(entry.token()).thenReturn(Optional.of("fixture-secret"));
        when(entry.apiServerUrl()).thenReturn(Optional.of("https://fixture.invalid"));
        var resolved = provider.getInfrastructureCredentials("fixture");
        assertThat(resolved.authMode()).isEqualTo(InfrastructureCredentials.AuthMode.TOKEN);
        assertThat(resolved.apiServerUrl()).isEqualTo("https://fixture.invalid");
    }
}
