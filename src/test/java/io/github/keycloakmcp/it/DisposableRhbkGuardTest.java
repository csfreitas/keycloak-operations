package io.github.keycloakmcp.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;
import org.junit.jupiter.api.Test;

class DisposableRhbkGuardTest {
    @Test
    void requiresExplicitOptInAndObservedVersionWithoutPrintingCredential() {
        var settings = DisposableRhbkGuard.settings(valid());
        assertThat(settings.expectedVersion).isEqualTo("26.6.3.redhat-00002");
        assertThat(settings.toString()).doesNotContain("unit-test-credential");
        for (String key : new String[] { "RUN_RHBK_IT", "RHBK_URL", "RHBK_EXPECTED_VERSION", "RHBK_CLIENT_SECRET" }) {
            var missing = valid();
            missing.remove(key);
            assertThatThrownBy(() -> DisposableRhbkGuard.settings(missing)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsRemoteAlternatePortAndAmbiguousLocalUrls() {
        for (String url : new String[] {
                "https://production.example", "http://localhost:8080", "http://localhost:8280/",
                "http://localhost:8280?target=prod", "http://user:pass@localhost:8280",
                "http://localhost.example:8280", "http://127.0.0.1:8280", "http://localhost:8280#fragment" }) {
            var environment = valid();
            environment.put("RHBK_URL", url);
            assertThatThrownBy(() -> DisposableRhbkGuard.settings(environment)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejectsIncompleteOrDifferentVersionFamily() {
        for (String version : new String[] { "26.6", "latest", "26.7.1", "26.6.3\nother" }) {
            var environment = valid();
            environment.put("RHBK_EXPECTED_VERSION", version);
            assertThatThrownBy(() -> DisposableRhbkGuard.settings(environment)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void resolvedTargetMustMatchTheDedicatedReadOnlyFixture() {
        var safe = target(DisposableRhbkGuard.TARGET_ID, DisposableRhbkGuard.URL, TargetEnvironment.TEST);
        assertThat(DisposableRhbkGuard.requireTarget(safe, valid())).isNotNull();
        assertThatThrownBy(() -> DisposableRhbkGuard.requireTarget(
                target("another-target", DisposableRhbkGuard.URL, TargetEnvironment.TEST), valid()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DisposableRhbkGuard.requireTarget(
                target(DisposableRhbkGuard.TARGET_ID, "http://localhost:8080", TargetEnvironment.TEST), valid()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DisposableRhbkGuard.requireTarget(
                target(DisposableRhbkGuard.TARGET_ID, DisposableRhbkGuard.URL, TargetEnvironment.PRD), valid()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Map<String, String> valid() {
        return new HashMap<>(Map.of(
                "RUN_RHBK_IT", "true",
                "RHBK_URL", DisposableRhbkGuard.URL,
                "RHBK_EXPECTED_VERSION", "26.6.3.redhat-00002",
                "RHBK_CLIENT_SECRET", "unit-test-credential"));
    }

    private static Target target(String id, String url, TargetEnvironment environment) {
        return new Target(TargetId.of(id), "RHBK unit fixture", TargetType.RHBK, environment, true,
                new KeycloakTargetConfiguration(url, "master", DisposableRhbkGuard.CLIENT_ID,
                        DisposableRhbkGuard.CREDENTIAL_REF), null, null, Map.of());
    }
}
