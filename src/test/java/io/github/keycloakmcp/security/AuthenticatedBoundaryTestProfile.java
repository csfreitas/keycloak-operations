package io.github.keycloakmcp.security;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Real HTTP authorization with synthetic framework identities; no external IdP is provisioned. */
public class AuthenticatedBoundaryTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "platform.authorization.mode", "authenticated",
                "quarkus.http.auth.permission.platform.policy", "authenticated",
                "platform.authorization.grants.reader-a.targets", "lab-keycloak-a",
                "platform.authorization.grants.reader-a.permissions", "READ",
                "platform.authorization.grants.assessor-a.targets", "lab-keycloak-a",
                "platform.authorization.grants.assessor-a.permissions", "READ,ASSESS");
    }
}
