package io.github.keycloakmcp.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_KEYCLOAK_IT", matches = "true")
class KeycloakCommunity26_6IT {

    @Test
    void placeholderEnabledOnlyWhenFlagSet() {
        org.assertj.core.api.Assertions.assertThat(System.getenv("RUN_KEYCLOAK_IT")).isEqualTo("true");
    }
}
