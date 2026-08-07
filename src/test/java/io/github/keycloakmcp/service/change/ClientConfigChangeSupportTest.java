package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.security.SensitiveDataFilter;

class ClientConfigChangeSupportTest {

    private ClientConfigChangeSupport support;

    @BeforeEach
    void setUp() {
        support = new ClientConfigChangeSupport(new SensitiveDataFilter(new ObjectMapper()));
    }

    @Test
    void plansDiffForNameChange() {
        ClientRepresentation current = new ClientRepresentation();
        current.setName("Old");
        current.setDescription("Desc");

        var planned = support.plan(current, Map.of("name", "New"));
        assertThat(planned.operations()).hasSize(1);
        assertThat(planned.diff().get(0).kind()).isEqualTo(DiffKind.CHANGED);
        assertThat(planned.diff().get(0).before()).isEqualTo("Old");
        assertThat(planned.diff().get(0).after()).isEqualTo("New");
    }

    @Test
    void rejectsSecretDesiredState() {
        ClientRepresentation current = new ClientRepresentation();
        current.setName("c");
        assertThatThrownBy(() -> support.plan(current, Map.of("secret", "s3cr3t")))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }

    @Test
    void rejectsNonAllowlistedProperty() {
        ClientRepresentation current = new ClientRepresentation();
        current.setName("c");
        assertThatThrownBy(() -> support.plan(current, Map.of("redirectUris", "http://x")))
                .isInstanceOf(McpException.class);
    }

    @Test
    void appliesPkceAttribute() {
        ClientRepresentation rep = new ClientRepresentation();
        var planned = support.plan(rep, Map.of("pkceCodeChallengeMethod", "S256"));
        support.applyToRepresentation(rep, planned.operations());
        assertThat(rep.getAttributes()).containsEntry("pkce.code.challenge.method", "S256");
    }
}
