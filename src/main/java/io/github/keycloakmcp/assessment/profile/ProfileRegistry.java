package io.github.keycloakmcp.assessment.profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProfileRegistry {

    private static final List<String> KC_SOURCES = List.of("keycloak");
    private static final List<String> KC_INFRA_SOURCES = List.of("keycloak", "infrastructure");
    private static final List<String> OPTIONAL_INFRA = List.of("infrastructure");

    private final Map<String, AssessmentProfile> profiles;

    public ProfileRegistry() {
        Map<String, AssessmentProfile> builtIn = new LinkedHashMap<>();

        AssessmentProfile keycloakProduction = profile(
                HealthCheckProfile.KEYCLOAK_PRODUCTION,
                "Community Keycloak production baseline (health + security)",
                List.of("KEYCLOAK"),
                List.of(),
                List.of(HealthCheckProfile.RULE_PACK_HEALTH, HealthCheckProfile.RULE_PACK_SECURITY_BASELINE),
                KC_SOURCES,
                OPTIONAL_INFRA);
        builtIn.put(HealthCheckProfile.KEYCLOAK_PRODUCTION, keycloakProduction);
        builtIn.put("default", keycloakProduction);

        builtIn.put(
                HealthCheckProfile.RHBK_PRODUCTION,
                profile(
                        HealthCheckProfile.RHBK_PRODUCTION,
                        "RHBK production baseline (health + security)",
                        List.of("RHBK"),
                        List.of(),
                        List.of(HealthCheckProfile.RULE_PACK_HEALTH, HealthCheckProfile.RULE_PACK_SECURITY_BASELINE),
                        KC_SOURCES,
                        OPTIONAL_INFRA));

        builtIn.put(
                CapacityProfile.RHBK_OPENSHIFT_PRODUCTION,
                profile(
                        CapacityProfile.RHBK_OPENSHIFT_PRODUCTION,
                        "RHBK on OpenShift: health, security, capacity, and HA",
                        List.of("RHBK"),
                        List.of("OPENSHIFT"),
                        List.of(
                                HealthCheckProfile.RULE_PACK_HEALTH,
                                HealthCheckProfile.RULE_PACK_SECURITY_BASELINE,
                                CapacityProfile.RULE_PACK_CAPACITY,
                                CapacityProfile.RULE_PACK_HA),
                        KC_INFRA_SOURCES,
                        List.of()));

        builtIn.put(
                CapacityProfile.KEYCLOAK_KUBERNETES_PRODUCTION,
                profile(
                        CapacityProfile.KEYCLOAK_KUBERNETES_PRODUCTION,
                        "Keycloak on Kubernetes: health, security, capacity, and HA",
                        List.of("KEYCLOAK", "RHBK"),
                        List.of("KUBERNETES"),
                        List.of(
                                HealthCheckProfile.RULE_PACK_HEALTH,
                                HealthCheckProfile.RULE_PACK_SECURITY_BASELINE,
                                CapacityProfile.RULE_PACK_CAPACITY,
                                CapacityProfile.RULE_PACK_HA),
                        KC_INFRA_SOURCES,
                        List.of()));

        builtIn.put(
                CapacityProfile.RHBK_OPENSHIFT_PRODUCTION_HA,
                profile(
                        CapacityProfile.RHBK_OPENSHIFT_PRODUCTION_HA,
                        "RHBK OpenShift production HA (adds admin-security pack)",
                        List.of("RHBK"),
                        List.of("OPENSHIFT"),
                        List.of(
                                HealthCheckProfile.RULE_PACK_HEALTH,
                                HealthCheckProfile.RULE_PACK_SECURITY_BASELINE,
                                CapacityProfile.RULE_PACK_CAPACITY,
                                CapacityProfile.RULE_PACK_HA,
                                CapacityProfile.RULE_PACK_ADMIN_SECURITY),
                        KC_INFRA_SOURCES,
                        List.of()));

        this.profiles = Map.copyOf(builtIn);
    }

    private static AssessmentProfile profile(
            String name,
            String description,
            List<String> products,
            List<String> runtimes,
            List<String> packs,
            List<String> required,
            List<String> optional) {
        return new AssessmentProfile(name, description, products, runtimes, packs, required, optional);
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
        Map<String, AssessmentProfile> unique = new LinkedHashMap<>();
        for (AssessmentProfile profile : profiles.values()) {
            unique.putIfAbsent(profile.name(), profile);
        }
        return List.copyOf(unique.values());
    }
}
