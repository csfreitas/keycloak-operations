package io.github.keycloakmcp.health;

import io.github.keycloakmcp.target.Target;

/**
 * Pluggable health check for a Keycloak/RHBK operations target.
 */
public interface HealthCheck {

    String name();

    HealthComponentResult check(Target target);
}
