package io.github.keycloakmcp.security;

import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Synthetic trusted-identity fixture, not JWT signature/issuer validation evidence.
 * It is activated only by the explicit test attribute, never by request headers.
 */
@ApplicationScoped
public class ConfigurationReadFixtureIdentityAugmentor implements SecurityIdentityAugmentor {
    static final String CLIENT_ATTRIBUTE = "configuration-boundary-client";

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        String client = identity.getAttribute(CLIENT_ATTRIBUTE);
        if (identity.isAnonymous() || client == null) return Uni.createFrom().item(identity);
        String name = identity.getPrincipal().getName();
        Map<String, Object> claims = Map.of(
                "iss", "https://synthetic-identity.invalid/realms/operators",
                "sub", "subject-" + name,
                "azp", client,
                "preferred_username", name,
                "groups", identity.getRoles());
        JsonWebToken principal = new JsonWebToken() {
            @Override public String getName() { return name; }
            @Override public Set<String> getClaimNames() { return claims.keySet(); }
            @SuppressWarnings("unchecked")
            @Override public <T> T getClaim(String claimName) { return (T) claims.get(claimName); }
        };
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder(identity).setPrincipal(principal).build());
    }
}
