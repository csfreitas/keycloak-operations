package io.github.keycloakmcp.target;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TargetMapper {

    private TargetMapper() {
    }

    public static TargetSummary toSummary(Target target) {
        Objects.requireNonNull(target, "target");
        return new TargetSummary(
                target.id().value(),
                target.displayName(),
                target.type().productLabel(),
                target.environment().name(),
                target.enabled());
    }

    public static List<TargetSummary> toSummaries(List<Target> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        return targets.stream().map(TargetMapper::toSummary).toList();
    }

    public static TargetDetails toDetails(Target target) {
        Objects.requireNonNull(target, "target");
        boolean keycloakConfigured = target.keycloak() != null
                && target.keycloak().url() != null
                && !target.keycloak().url().isBlank();
        boolean infrastructureConfigured = target.hasInfrastructure();
        String infrastructureType = target.infrastructureTypeOrNone().name();
        Map<String, String> tags = target.tags() == null ? Map.of() : target.tags();
        return new TargetDetails(
                target.id().value(),
                target.displayName(),
                target.type().productLabel(),
                target.environment().name(),
                target.enabled(),
                keycloakConfigured,
                infrastructureConfigured,
                infrastructureType,
                tags);
    }
}
