package io.github.keycloakmcp.service.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.domain.platform.TargetOverview;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.repository.SnapshotRepository;
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
    private final SnapshotRepository snapshotRepository;

    @Inject
    public TargetOverviewService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            AssessmentHistoryService assessmentHistoryService,
            HealthCheckService healthCheckService,
            SnapshotService snapshotService,
            SnapshotRepository snapshotRepository) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.assessmentHistoryService = assessmentHistoryService;
        this.healthCheckService = healthCheckService;
        this.snapshotService = snapshotService;
        this.snapshotRepository = snapshotRepository;
    }

    public TargetOverview overview(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        AssessmentRunSummary assessment = assessmentHistoryService.latest(targetId).orElse(null);
        HealthCheckSummary health = healthCheckService.latest(targetId).orElse(null);
        SnapshotSummary snapshot = snapshotService.latest(targetId).orElse(null);
        EnvironmentSnapshotEntity snapshotEntity = snapshotRepository.findLatest(targetId).orElse(null);
        OverviewSignals signals = OverviewSignals.from(snapshotEntity == null ? null : snapshotEntity.summary);

        return new TargetOverview(
                target.id().value(),
                target.displayName(),
                target.type().name(),
                target.environment().name(),
                target.enabled(),
                target.keycloak().url(),
                signals.productVersion(),
                signals.runtime(),
                signals.namespace(),
                signals.desiredReplicas(),
                signals.readyReplicas(),
                signals.podCount(),
                signals.zoneCount(),
                target.hasMetrics(),
                health == null ? HealthStatus.UNKNOWN : health.overallStatus(),
                assessment,
                health,
                snapshot,
                Instant.now(),
                target.tags());
    }

    record OverviewSignals(
            String productVersion,
            String runtime,
            String namespace,
            Integer desiredReplicas,
            Integer readyReplicas,
            Integer podCount,
            Integer zoneCount) {

        @SuppressWarnings("unchecked")
        static OverviewSignals from(Map<String, Object> summary) {
            if (summary == null || summary.isEmpty()) {
                return new OverviewSignals(null, null, null, null, null, null, null);
            }
            String version = asString(summary.get("serverVersion"));
            String runtime = null;
            String namespace = null;
            Integer desired = null;
            Integer ready = null;
            Integer pods = null;
            Integer zones = null;

            Object inventory = summary.get("inventory");
            if (inventory instanceof Map<?, ?> inv) {
                runtime = asString(inv.get("runtime"));
                Object keycloak = inv.get("keycloak");
                if (keycloak instanceof Map<?, ?> kc) {
                    namespace = asString(kc.get("namespace"));
                    desired = asInteger(kc.get("desiredReplicas"));
                    ready = asInteger(kc.get("readyReplicas"));
                    if (version == null) {
                        version = asString(kc.get("version"));
                    }
                }
                Object topology = inv.get("topology");
                if (topology instanceof Map<?, ?> topo) {
                    zones = asInteger(topo.get("zoneCount"));
                }
                Object cluster = inv.get("cluster");
                if (zones == null && cluster instanceof Map<?, ?> cl) {
                    zones = asInteger(cl.get("zoneCount"));
                }
                Object podList = inv.get("pods");
                if (podList instanceof List<?> list) {
                    pods = list.size();
                }
            }
            return new OverviewSignals(version, runtime, namespace, desired, ready, pods, zones);
        }

        private static String asString(Object value) {
            return value == null ? null : String.valueOf(value);
        }

        private static Integer asInteger(Object value) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            if (value instanceof String s && !s.isBlank()) {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
