package io.github.keycloakmcp.service.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.FleetItem;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.mapper.AssessmentPersistenceMapper;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.persistence.repository.SnapshotRepository;
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
    private final SnapshotRepository snapshotRepository;
    private final AssessmentPersistenceMapper assessmentMapper;
    private final PlatformPersistenceMapper platformMapper;

    @Inject
    public FleetService(
            TargetRegistry targetRegistry,
            TargetAuthorizationService targetAuthorization,
            AssessmentRepository assessmentRepository,
            HealthCheckRepository healthCheckRepository,
            SnapshotRepository snapshotRepository,
            AssessmentPersistenceMapper assessmentMapper,
            PlatformPersistenceMapper platformMapper) {
        this.targetRegistry = targetRegistry;
        this.targetAuthorization = targetAuthorization;
        this.assessmentRepository = assessmentRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.snapshotRepository = snapshotRepository;
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
            EnvironmentSnapshotEntity snapshot = snapshotRepository.findLatest(targetId).orElse(null);
            SnapshotSignals signals = SnapshotSignals.from(snapshot == null ? null : snapshot.summary);

            Integer critical = count(assessment, "critical");
            Integer high = count(assessment, "high");

            items.add(new FleetItem(
                    targetId,
                    target.displayName(),
                    target.type().name(),
                    target.environment().name(),
                    target.enabled(),
                    signals.productVersion(),
                    signals.runtime(),
                    health == null ? HealthStatus.UNKNOWN : health.overallStatus(),
                    assessment == null ? null : assessment.score(),
                    assessment == null ? null : assessment.status(),
                    assessment == null ? null : assessment.evidenceCompleteness(),
                    critical,
                    high,
                    target.hasMetrics(),
                    health == null ? null : health.createdAt(),
                    assessment == null ? null : assessment.createdAt(),
                    target.tags()));
        }
        return List.copyOf(items);
    }

    private static Integer count(AssessmentRunSummary assessment, String key) {
        if (assessment == null || assessment.findingCounts() == null) {
            return null;
        }
        return assessment.findingCounts().get(key);
    }

    /** Compact fields extracted from persisted snapshot summary (no live calls). */
    record SnapshotSignals(String productVersion, String runtime) {
        @SuppressWarnings("unchecked")
        static SnapshotSignals from(Map<String, Object> summary) {
            if (summary == null || summary.isEmpty()) {
                return new SnapshotSignals(null, null);
            }
            String version = asString(summary.get("serverVersion"));
            String runtime = null;
            Object inventory = summary.get("inventory");
            if (inventory instanceof Map<?, ?> inv) {
                runtime = asString(inv.get("runtime"));
                if (version == null) {
                    Object keycloak = inv.get("keycloak");
                    if (keycloak instanceof Map<?, ?> kc) {
                        version = asString(kc.get("version"));
                    }
                }
            }
            return new SnapshotSignals(version, runtime);
        }

        private static String asString(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
