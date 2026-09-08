package io.github.keycloakmcp.it;

import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Local Identity A lab mode; Identity B is a pre-provisioned read-only RHBK service account. */
public class DisposableRhbkIntegrationTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        var settings = "true".equals(System.getenv("RUN_RHBK_IT"))
                ? DisposableRhbkGuard.settings(System.getenv())
                : new DisposableRhbkGuard.Settings("26.6.0.not-enabled", "not-enabled");
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("platform.authorization.mode", "local-lab");
        overrides.put("quarkus.http.auth.permission.platform.policy", "permit");
        overrides.put("quarkus.oidc.enabled", "false");
        overrides.put("mcp.read-only", "true");
        overrides.put("mcp.target.registry", "configuration");
        overrides.put("platform.target-registry", "configuration");
        overrides.put("mcp.targets.lab-keycloak-a.enabled", "false");
        overrides.put("mcp.targets.lab-keycloak-b.enabled", "false");
        String target = "mcp.targets." + DisposableRhbkGuard.TARGET_ID + ".";
        overrides.put(target + "display-name", "Disposable read-only RHBK integration");
        overrides.put(target + "type", "RHBK");
        overrides.put(target + "environment", "TEST");
        overrides.put(target + "enabled", "true");
        overrides.put(target + "keycloak.url", DisposableRhbkGuard.URL);
        overrides.put(target + "keycloak.auth-realm", "master");
        overrides.put(target + "keycloak.client-id", DisposableRhbkGuard.CLIENT_ID);
        overrides.put(target + "keycloak.credential-ref", DisposableRhbkGuard.CREDENTIAL_REF);
        overrides.put(target + "infrastructure.type", "NONE");
        overrides.put(target + "observability.metrics.type", "NONE");
        overrides.put("mcp.credentials." + DisposableRhbkGuard.CREDENTIAL_REF + ".client-secret", settings.clientSecret);
        overrides.put("discovery.kubernetes.enabled", "false");
        overrides.put("discovery.openshift.enabled", "false");
        return overrides;
    }
}
