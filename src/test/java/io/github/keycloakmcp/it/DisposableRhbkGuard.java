package io.github.keycloakmcp.it;

import java.util.Map;

import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetType;

/** Exact opt-in boundary for read-only RHBK checks; never provisions a target or credentials. */
final class DisposableRhbkGuard {
    static final String TARGET_ID = "lab-rhbk-readonly";
    static final String URL = "http://localhost:8280";
    static final String CLIENT_ID = "keycloak-mcp-readonly";
    static final String CREDENTIAL_REF = "rhbk-readonly-it";
    static final String REALM = "mcp-demo";

    private DisposableRhbkGuard() { }

    static Settings settings(Map<String, String> environment) {
        if (!"true".equals(environment.get("RUN_RHBK_IT"))) {
            throw new IllegalStateException("RHBK integration requires RUN_RHBK_IT=true");
        }
        if (!URL.equals(environment.get("RHBK_URL"))) {
            throw new IllegalStateException("RHBK integration requires the exact disposable localhost:8280 URL");
        }
        String expectedVersion = required(environment, "RHBK_EXPECTED_VERSION");
        if (!expectedVersion.matches("26\\.6\\.[0-9]+(?:[.\\-][A-Za-z0-9.\\-]+)?")) {
            throw new IllegalStateException("RHBK_EXPECTED_VERSION must be the exact observed 26.6.x version");
        }
        return new Settings(expectedVersion, required(environment, "RHBK_CLIENT_SECRET"));
    }

    static Settings requireTarget(Target target) {
        return requireTarget(target, System.getenv());
    }

    static Settings requireTarget(Target target, Map<String, String> environment) {
        Settings settings = settings(environment);
        if (!TARGET_ID.equals(target.id().value()) || !target.enabled()
                || target.type() != TargetType.RHBK || target.environment() != TargetEnvironment.TEST
                || !URL.equals(target.keycloak().url())
                || !"master".equals(target.keycloak().authRealm())
                || !CLIENT_ID.equals(target.keycloak().clientId())
                || !CREDENTIAL_REF.equals(target.keycloak().credentialRef())
                || target.hasInfrastructure() || target.hasMetrics()) {
            throw new IllegalStateException("Resolved RHBK target does not match the disposable read-only fixture");
        }
        return settings;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("RHBK integration requires " + name);
        }
        return value;
    }

    // Deliberately not a record: generated record toString would expose the test credential on failure.
    static final class Settings {
        final String expectedVersion;
        final String clientSecret;

        Settings(String expectedVersion, String clientSecret) {
            this.expectedVersion = expectedVersion;
            this.clientSecret = clientSecret;
        }

        @Override
        public String toString() {
            return "DisposableRhbkSettings[credentials=REDACTED]";
        }
    }
}
