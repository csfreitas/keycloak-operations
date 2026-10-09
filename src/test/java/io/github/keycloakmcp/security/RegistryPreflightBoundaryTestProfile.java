package io.github.keycloakmcp.security;

import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Synthetic administrative admission fixture; not a real OIDC or first-boot acceptance test. */
public class RegistryPreflightBoundaryTestProfile implements QuarkusTestProfile {
    static final String ROLE = "registry-preflight";
    static final String CLIENT = "operator-console";
    static final String TARGET = "lab-keycloak-a";
    static final String CREDENTIAL_REFERENCE = "lab-a";

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("platform.authorization.mode", "authenticated");
        config.put("quarkus.http.auth.permission.platform.policy", "authenticated");
        config.put("mcp.read-only", "true");
        config.put("platform.registry-administration.enabled", "true");
        String prefix = "platform.registry-administration.administrators.initial-admin.";
        config.put(prefix + "issuer", "https://synthetic-identity.invalid/realms/operators");
        config.put(prefix + "subject", "subject-admin");
        config.put(prefix + "role", ROLE);
        config.put(prefix + "rest-client-ids", CLIENT);
        // A legacy target administrator is deliberately not a registry administrator.
        config.put("platform.authorization.grants.legacy-admin.targets", TARGET);
        config.put("platform.authorization.grants.legacy-admin.permissions", "READ,ADMIN");
        return Map.copyOf(config);
    }
}
