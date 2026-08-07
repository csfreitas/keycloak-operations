package io.github.keycloakmcp.service.platform;

import java.util.ArrayList;
import java.util.List;

import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.FleetItem;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.persistence.mapper.AssessmentPersistenceMapper;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FleetService {

    private final TargetRegistry targetRegistry;
    private final TargetAuthorizationService targetAuthorization;
    private final AssessmentRepository assessmentRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final AssessmentPersistenceMapper assessmentMapper;
    private final PlatformPersistenceMapper platformMapper;

    @Inject
    public FleetService(
            TargetRegistry targetRegistry,
            TargetAuthorizationService targetAuthorization,
            AssessmentRepository assessmentRepository,
            HealthCheckRepository healthCheckRepository,
            AssessmentPersistenceMapper assessmentMapper,
            PlatformPersistenceMapper platformMapper) {
        this.targetRegistry = targetRegistry;
        this.targetAuthorization = targetAuthorization;
        this.assessmentRepository = assessmentRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.assessmentMapper = assessmentMapper;
        this.platformMapper = platformMapper;
    }

    public List<FleetItem> fleet() {
        List<FleetItem> items = new ArrayList<>();
        for (Target target : targetRegistry.list()) {
            try {
                targetAuthorization.assertAllowed(target, TargetPermission.READ);
            } catch (RuntimeException e) {
                continue;
            }
            String targetId = target.id().value();
            AssessmentRunSummary assessment = assessmentRepository.findLatest(targetId)
                    .map(assessmentMapper::toSummary)
                    .orElse(null);
            HealthCheckSummary health = healthCheckRepository.findLatest(targetId)
                    .map(platformMapper::toHealthSummary)
                    .orElse(null);
            items.add(new FleetItem(
                    targetId,
                    target.displayName(),
                    target.type().name(),
                    target.environment().name(),
                    target.enabled(),
                    health == null ? HealthStatus.UNKNOWN : health.overallStatus(),
                    assessment == null ? null : assessment.score(),
                    health == null ? null : health.createdAt(),
                    assessment == null ? null : assessment.createdAt(),
                    target.tags()));
        }
        return List.copyOf(items);
    }
}
