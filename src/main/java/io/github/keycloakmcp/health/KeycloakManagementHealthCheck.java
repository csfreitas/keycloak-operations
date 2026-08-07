package io.github.keycloakmcp.health;

import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KeycloakManagementHealthCheck implements HealthCheck {

    private final KeycloakManagementHealthProvider provider;

    @Inject
    public KeycloakManagementHealthCheck(KeycloakManagementHealthProvider provider) {
        this.provider = provider;
    }

    @Override
    public String name() {
        return "keycloak.management";
    }

    @Override
    public HealthComponentResult check(Target target) {
        long start = System.currentTimeMillis();
        KeycloakManagementHealthProvider.ManagementHealthResult result = provider.check(target);
        return HealthComponentResult.of(
                name(),
                result.status(),
                result.message(),
                result.details(),
                System.currentTimeMillis() - start);
    }
}
