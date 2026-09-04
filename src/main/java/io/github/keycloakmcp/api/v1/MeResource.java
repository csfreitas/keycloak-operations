package io.github.keycloakmcp.api.v1;

import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.SecurityIdentity;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Auth session probe for the Web UI. Works in open lab mode (OIDC disabled)
 * and returns principal claims when Identity A OIDC is enabled.
 */
@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
public class MeResource {

    @Inject
    Instance<SecurityIdentity> securityIdentity;

    @Inject
    Instance<JsonWebToken> jwt;

    @Inject
    TargetAuthorizationService authorization;

    @GET
    public Map<String, Object> me() {
        if (authorization.isLocalLab()) {
            return Map.of(
                    "authenticated", true,
                    "authMode", "OPEN_LAB",
                    "subject", "local-lab",
                    "displayName", "Local lab (no authentication)");
        }
        authorization.assertSession();
        SecurityIdentity identity = securityIdentity.isResolvable() ? securityIdentity.get() : null;
        boolean authenticated = identity != null && !identity.isAnonymous();
        String subject = authorization.currentActor();
        String name = Optional.ofNullable(jwt.isResolvable() ? jwt.get().getName() : null).orElse(subject);
        return Map.of(
                "authenticated", authenticated,
                "authMode", "OIDC",
                "subject", subject == null ? "" : subject,
                "displayName", name == null ? "" : name);
    }
}
