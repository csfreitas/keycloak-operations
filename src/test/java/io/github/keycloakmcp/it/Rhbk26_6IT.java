package io.github.keycloakmcp.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * RHBK integration test — requires registry.redhat.io credentials.
 * Never enabled in default CI.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_RHBK_IT", matches = "true")
class Rhbk26_6IT {

    @Test
    void placeholderEnabledOnlyWhenFlagSet() {
        org.assertj.core.api.Assertions.assertThat(System.getenv("RUN_RHBK_IT")).isEqualTo("true");
    }
}
