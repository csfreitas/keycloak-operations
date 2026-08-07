package io.github.keycloakmcp.service.platform;

import java.time.Instant;

import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.domain.platform.TargetOverview;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TargetOverviewService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final AssessmentHistoryService assessmentHistoryService;
    private final HealthCheckService healthCheckService;
    private final SnapshotService snapshotService;

    @Inject
    public TargetOverviewService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            AssessmentHistoryService assessmentHistoryService,
            HealthCheckService healthCheckService,
            SnapshotService snapshotService) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.assessmentHistoryService = assessmentHistoryService;
        this.healthCheckService = healthCheckService;
        this.snapshotService = snapshotService;
    }

    public TargetOverview overview(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        AssessmentRunSummary assessment = assessmentHistoryService.latest(targetId).orElse(null);
        HealthCheckSummary health = healthCheckService.latest(targetId).orElse(null);
        SnapshotSummary snapshot = snapshotService.latest(targetId).orElse(null);

        return new TargetOverview(
                target.id().value(),
                target.displayName(),
                target.type().name(),
                target.environment().name(),
                target.enabled(),
                target.keycloak().url(),
                health == null ? HealthStatus.UNKNOWN : health.overallStatus(),
                assessment,
                health,
                snapshot,
                Instant.now(),
                target.tags());
    }
}
