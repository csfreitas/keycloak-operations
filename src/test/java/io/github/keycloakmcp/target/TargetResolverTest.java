package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class TargetResolverTest {

    private TargetRegistry registry;
    private TargetResolver resolver;

    @BeforeEach
    void setUp() {
        registry = mock(TargetRegistry.class);
        resolver = new TargetResolver(registry);
    }

    @Test
    void requireReturnsEnabledTarget() {
        Target target = sample("lab-a", true);
        when(registry.require("lab-a")).thenReturn(target);

        assertThat(resolver.require("lab-a")).isSameAs(target);
    }

    @Test
    void requireDisabledThrows() {
        when(registry.require("lab-disabled")).thenReturn(sample("lab-disabled", false));

        assertThatThrownBy(() -> resolver.require("lab-disabled"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.TARGET_DISABLED));
    }

    @Test
    void requireBlankThrowsInvalidArgument() {
        assertThatThrownBy(() -> resolver.require("  "))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void findByIdDelegates() {
        Target target = sample("lab-a", true);
        when(registry.findById("lab-a")).thenReturn(Optional.of(target));
        assertThat(resolver.findById("lab-a")).contains(target);
    }

    @Test
    void listDelegates() {
        when(registry.list()).thenReturn(List.of(sample("lab-a", true)));
        assertThat(resolver.list()).hasSize(1);
    }

    private static Target sample(String id, boolean enabled) {
        return new Target(
                TargetId.of(id),
                "Display " + id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                enabled,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "keycloak-mcp", "cred-" + id),
                null,
                null,
                Map.of());
    }
}
