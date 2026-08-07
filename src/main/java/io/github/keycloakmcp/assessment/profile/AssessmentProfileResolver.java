package io.github.keycloakmcp.assessment.profile;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.assessment.engine.EvidenceContext;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetType;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Suggests an assessment profile from target metadata / evidence.
 * Never overrides an explicitly provided profile name — callers must pass blank/null
 * to receive a suggestion.
 */
@ApplicationScoped
public class AssessmentProfileResolver {

    public Optional<String> suggest(Target target, List<Evidence> evidence) {
        if (target == null) {
            return Optional.empty();
        }
        EvidenceContext ctx = new EvidenceContext(
                target.id().value(), evidence == null ? List.of() : evidence);
        String product = ctx.findString("keycloak.product")
                .orElse(target.type() == TargetType.RHBK ? "RHBK" : "KEYCLOAK");
        String runtime = ctx.findString("runtime.type").orElseGet(() -> {
            InfrastructureType t = target.infrastructureTypeOrNone();
            return t == InfrastructureType.NONE ? null : t.name();
        });
        boolean prd = target.environment() == TargetEnvironment.PRD;
        boolean rhbk = "RHBK".equalsIgnoreCase(product) || target.type() == TargetType.RHBK;
        boolean openshift = runtime != null && runtime.equalsIgnoreCase("OPENSHIFT");
        boolean kubernetes = runtime != null && runtime.equalsIgnoreCase("KUBERNETES");

        if (rhbk && openshift && prd) {
            if (target.hasMetrics()) {
                return Optional.of(PerformanceProfile.RHBK_OPENSHIFT_PRODUCTION_PERFORMANCE);
            }
            return Optional.of(CapacityProfile.RHBK_OPENSHIFT_PRODUCTION_HA);
        }
        if (rhbk && openshift) {
            if (target.hasMetrics()) {
                return Optional.of(PerformanceProfile.RHBK_OPENSHIFT_PRODUCTION_PERFORMANCE);
            }
            return Optional.of(CapacityProfile.RHBK_OPENSHIFT_PRODUCTION);
        }
        if (kubernetes) {
            return Optional.of(CapacityProfile.KEYCLOAK_KUBERNETES_PRODUCTION);
        }
        if (rhbk) {
            if (target.hasMetrics()) {
                return Optional.of(PerformanceProfile.RHBK_PRODUCTION_PERFORMANCE);
            }
            return Optional.of(HealthCheckProfile.RHBK_PRODUCTION);
        }
        if (target.hasMetrics()) {
            return Optional.of(PerformanceProfile.KEYCLOAK_PRODUCTION_PERFORMANCE);
        }
        return Optional.of(HealthCheckProfile.KEYCLOAK_PRODUCTION);
    }
}
