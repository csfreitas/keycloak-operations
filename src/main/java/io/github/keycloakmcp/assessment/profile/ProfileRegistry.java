package io.github.keycloakmcp.assessment.profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProfileRegistry {

    private final Map<String, AssessmentProfile> profiles;

    public ProfileRegistry() {
        Map<String, AssessmentProfile> builtIn = new LinkedHashMap<>();
        AssessmentProfile keycloakProduction = new AssessmentProfile(
                HealthCheckProfile.KEYCLOAK_PRODUCTION,
                List.of(HealthCheckProfile.RULE_PACK_HEALTH, HealthCheckProfile.RULE_PACK_SECURITY_BASELINE));
        builtIn.put(HealthCheckProfile.KEYCLOAK_PRODUCTION, keycloakProduction);
        // Convenience alias for REST/UI callers
        builtIn.put("default", keycloakProduction);
        builtIn.put(
                HealthCheckProfile.RHBK_PRODUCTION,
                new AssessmentProfile(
                        HealthCheckProfile.RHBK_PRODUCTION,
                        List.of(HealthCheckProfile.RULE_PACK_HEALTH, HealthCheckProfile.RULE_PACK_SECURITY_BASELINE)));
        builtIn.put(
                CapacityProfile.RHBK_OPENSHIFT_PRODUCTION,
                new AssessmentProfile(
                        CapacityProfile.RHBK_OPENSHIFT_PRODUCTION,
                        List.of(
                                HealthCheckProfile.RULE_PACK_HEALTH,
                                CapacityProfile.RULE_PACK_CAPACITY,
                                CapacityProfile.RULE_PACK_HA)));
        builtIn.put(
                CapacityProfile.KEYCLOAK_KUBERNETES_PRODUCTION,
                new AssessmentProfile(
                        CapacityProfile.KEYCLOAK_KUBERNETES_PRODUCTION,
                        List.of(
                                HealthCheckProfile.RULE_PACK_HEALTH,
                                CapacityProfile.RULE_PACK_CAPACITY,
                                CapacityProfile.RULE_PACK_HA)));
        this.profiles = Map.copyOf(builtIn);
    }

    public Optional<AssessmentProfile> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(name));
    }

    public AssessmentProfile require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("Unknown assessment profile: " + name));
    }

    public Collection<AssessmentProfile> all() {
        return profiles.values();
    }
}
