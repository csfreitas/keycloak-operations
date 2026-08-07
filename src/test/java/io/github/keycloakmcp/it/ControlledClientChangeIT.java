package io.github.keycloakmcp.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in integration test for the 0.8 controlled client-update proof-of-concept.
 * Enabled when {@code RUN_KEYCLOAK_IT=true} against a live Keycloak with write-capable
 * credentials and {@code mcp.read-only=false}.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_KEYCLOAK_IT", matches = "true")
class ControlledClientChangeIT {

    @Test
    void placeholderEnabledOnlyWhenFlagSet() {
        // Expand with live plan → approve → apply → verify against Testcontainers/Keycloak
        // when RUN_KEYCLOAK_IT=true in CI with a writable service account.
        org.assertj.core.api.Assertions.assertThat(System.getenv("RUN_KEYCLOAK_IT")).isEqualTo("true");
    }
}
