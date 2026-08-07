package io.github.keycloakmcp.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;

class ToolAuthorizationChangeTest {

    private McpRuntimeConfig runtimeConfig;
    private ToolAuthorization toolAuthorization;

    @BeforeEach
    void setUp() {
        runtimeConfig = mock(McpRuntimeConfig.class);
        when(runtimeConfig.readOnly()).thenReturn(true);
        toolAuthorization = new ToolAuthorization(runtimeConfig);
    }

    @Test
    void allowsPlanToolsInReadOnlyMode() {
        assertThatCode(() -> toolAuthorization.assertReadOnlyOperation("keycloak_plan_client_update"))
                .doesNotThrowAnyException();
        assertThatCode(() -> toolAuthorization.assertReadOnlyOperation("keycloak_get_change"))
                .doesNotThrowAnyException();
        assertThatCode(() -> toolAuthorization.assertReadOnlyOperation("keycloak_approve_change"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksApplyInReadOnlyMode() {
        assertThatThrownBy(() -> toolAuthorization.assertReadOnlyOperation("keycloak_apply_change"))
                .isInstanceOf(McpException.class);
    }
}
