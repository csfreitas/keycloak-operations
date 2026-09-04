package io.github.keycloakmcp.it;

import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Runtime configuration for opt-in tests against the local disposable Keycloak stack. */
public class DisposableKeycloakIntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("mcp.read-only", "false");
        overrides.put("mcp.targets.lab-keycloak-a.environment", "TEST");
        overrides.put("mcp.targets.lab-keycloak-a.keycloak.url", envOrDefault(
                "KEYCLOAK_URL", "http://localhost:8080"));
        overrides.put("mcp.credentials.lab-a.client-secret", envOrDefault(
                "KEYCLOAK_CLIENT_SECRET", "missing-disposable-test-secret"));
        overrides.put("mcp.targets.lab-keycloak-b.enabled", "false");
        return overrides;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
