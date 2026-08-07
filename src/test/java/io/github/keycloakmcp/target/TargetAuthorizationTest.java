package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class TargetAuthorizationTest {

    private McpRuntimeConfig runtimeConfig;
    private TargetAuthorizationService authz;

    @BeforeEach
    void setUp() {
        runtimeConfig = mock(McpRuntimeConfig.class);
        when(runtimeConfig.readOnly()).thenReturn(true);
        authz = new TargetAuthorizationService(runtimeConfig);
    }

    @Test
    void allowsReadAndAssessOnEnabledTarget() {
        Target target = sample(true);
        authz.assertAllowed(target, TargetPermission.READ);
        authz.assertAllowed(target, TargetPermission.ASSESS);
    }

    @Test
    void deniesWriteWhenReadOnly() {
        Target target = sample(true);
        assertThatThrownBy(() -> authz.assertAllowed(target, TargetPermission.WRITE))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.TARGET_NOT_AUTHORIZED));
    }

    @Test
    void deniesDisabledTarget() {
        Target target = sample(false);
        assertThatThrownBy(() -> authz.assertAllowed(target, TargetPermission.READ))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> {
                    ErrorCode code = ((McpException) ex).getCode();
                    assertThat(code).isIn(ErrorCode.TARGET_DISABLED, ErrorCode.TARGET_NOT_AUTHORIZED);
                });
    }

    private static Target sample(boolean enabled) {
        return new Target(
                TargetId.of("lab-a"),
                "Lab A",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                enabled,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "mcp", "cred-a"),
                null,
                null,
                Map.of());
    }
}
