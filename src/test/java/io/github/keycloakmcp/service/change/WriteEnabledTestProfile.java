package io.github.keycloakmcp.service.change;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WriteEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "mcp.read-only", "false",
                "mcp.targets.lab-keycloak-a.environment", "DEV",
                "mcp.targets.lab-keycloak-b.environment", "PRD");
    }
}
