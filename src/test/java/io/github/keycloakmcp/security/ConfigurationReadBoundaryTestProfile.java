package io.github.keycloakmcp.security;

import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Test-only pinned resources, separate roles and explicit client/channel grants. */
public class ConfigurationReadBoundaryTestProfile implements QuarkusTestProfile {
    static final String TARGET = "lab-keycloak-a";
    static final String REALM_ALPHA_ID = "11111111-1111-4111-8111-111111111111";
    static final String REALM_BETA_ID = "22222222-2222-4222-8222-222222222222";
    static final String CLIENT_BETA_ID = "33333333-3333-4333-8333-333333333333";

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("platform.authorization.mode", "authenticated");
        config.put("quarkus.http.auth.permission.platform.policy", "authenticated");
        config.put("platform.authorization.grants.legacy-reader.targets", TARGET);
        config.put("platform.authorization.grants.legacy-reader.permissions", "READ");
        scope(config, "realm-alpha", "scope-alpha", "realm-a", REALM_ALPHA_ID, "REALM",
                "registrationAllowed,verifyEmail");
        scope(config, "client-beta", "scope-beta", "realm-b", REALM_BETA_ID, "CLIENT",
                "enabled,publicClient");
        config.put("platform.configuration-reads.scopes.client-beta.client-id", CLIENT_BETA_ID);
        return Map.copyOf(config);
    }

    private static void scope(Map<String, String> config, String id, String role,
            String realm, String realmId, String kind, String fields) {
        String prefix = "platform.configuration-reads.scopes." + id + ".";
        config.put(prefix + "role", role);
        config.put(prefix + "target-id", TARGET);
        config.put(prefix + "realm", realm);
        config.put(prefix + "realm-id", realmId);
        config.put(prefix + "kind", kind);
        config.put(prefix + "fields", fields);
        config.put(prefix + "rest-client-ids", "operator-console,dual-client");
        config.put(prefix + "mcp-client-ids", "reference-agent,dual-client");
    }
}
