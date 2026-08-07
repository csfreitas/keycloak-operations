package io.github.keycloakmcp.service.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.HealthCheckDetail;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthComponentView;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.health.HealthCheckEngine;
import io.github.keycloakmcp.health.HealthCheckEngine.HealthRunResult;
import io.github.keycloakmcp.health.HealthComponentResult;
import io.github.keycloakmcp.persistence.entity.HealthCheckResultEntity;
import io.github.keycloakmcp.persistence.entity.HealthCheckRunEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Target health checks — delegates execution to {@link HealthCheckEngine}.
 * Distinct from full assessments.
 */
@ApplicationScoped
public class HealthCheckService {

    private static final Logger LOG = Logger.getLogger(HealthCheckService.class);

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final HealthCheckEngine healthCheckEngine;
    private final HealthCheckRepository healthCheckRepository;
    private final PlatformPersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final OperationalEventBus eventBus;

    @Inject
    public HealthCheckService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            HealthCheckEngine healthCheckEngine,
            HealthCheckRepository healthCheckRepository,
            PlatformPersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter,
            OperationalEventBus eventBus) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.healthCheckEngine = healthCheckEngine;
        this.healthCheckRepository = healthCheckRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.eventBus = eventBus;
    }

    @Transactional
    public HealthCheckSummary run(String targetId, TriggerType triggerType) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        String runId = UUID.randomUUID().toString();
        HealthRunResult runResult = healthCheckEngine.run(target);
        HealthStatus overall = runResult.overallStatus() == null ? HealthStatus.UNKNOWN : runResult.overallStatus();

        List<HealthCheckResultEntity> results = new ArrayList<>();
        for (HealthComponentResult component : runResult.results()) {
            results.add(toEntity(runId, targetId, component));
        }

        HealthCheckRunEntity run = new HealthCheckRunEntity();
        run.id = runId;
        run.targetId = targetId;
        run.overallStatus = overall.name();
        run.triggerType = triggerType == null ? TriggerType.API.name() : triggerType.name();
        Map<String, Object> summary = new HashMap<>();
        summary.put("resultCount", results.size());
        summary.put("overallStatus", overall.name());
        summary.put("components", runResult.componentStatuses());
        run.summary = sensitiveDataFilter.redact(summary);
        run.startedAt = runResult.startedAt() == null ? Instant.now() : runResult.startedAt();
        run.completedAt = runResult.completedAt() == null ? Instant.now() : runResult.completedAt();
        run.createdAt = Instant.now();

        LOG.debugf("Health check completed target=%s overall=%s components=%d", targetId, overall, results.size());
        healthCheckRepository.persistRunWithResults(run, results);
        eventBus.publish(OperationalEvent.of(
                "health_check_completed",
                targetId,
                "Health check " + overall.name(),
                runId));
        return mapper.toHealthSummary(run);
    }

    public PageResult<HealthCheckSummary> list(String targetId, int page, int size) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        var pageResult = healthCheckRepository.listByTarget(targetId, page, size);
        List<HealthCheckSummary> items = pageResult.items().stream().map(mapper::toHealthSummary).toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    public Optional<HealthCheckSummary> latest(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return healthCheckRepository.findLatest(targetId).map(mapper::toHealthSummary);
    }

    public HealthCheckDetail get(String targetId, String healthCheckId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        HealthCheckRunEntity run = healthCheckRepository.findByIdForTarget(healthCheckId, targetId)
                .orElseThrow(() -> McpException.invalidArgument("health check not found: " + healthCheckId));
        List<HealthComponentView> components = healthCheckRepository.listResults(healthCheckId).stream()
                .map(this::toComponentView)
                .toList();
        return new HealthCheckDetail(
                run.id,
                run.targetId,
                parseStatus(run.overallStatus),
                parseTrigger(run.triggerType),
                run.startedAt,
                run.completedAt,
                run.createdAt,
                components,
                run.summary == null ? Map.of() : Map.copyOf(run.summary));
    }

    public Optional<HealthCheckDetail> latestDetail(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return healthCheckRepository.findLatest(targetId).map(run -> get(targetId, run.id));
    }

    private HealthCheckResultEntity toEntity(String runId, String targetId, HealthComponentResult component) {
        HealthCheckResultEntity entity = new HealthCheckResultEntity();
        entity.id = UUID.randomUUID().toString();
        entity.healthCheckId = runId;
        entity.targetId = targetId;
        entity.checkName = component.name();
        entity.status = component.status() == null ? HealthStatus.UNKNOWN.name() : component.status().name();
        entity.message = sensitiveDataFilter.redactString(component.message());
        entity.details = component.details() == null
                ? null
                : sensitiveDataFilter.redact(new HashMap<>(component.details()));
        entity.durationMs = component.durationMs();
        entity.createdAt = Instant.now();
        return entity;
    }

    private HealthComponentView toComponentView(HealthCheckResultEntity entity) {
        return new HealthComponentView(
                entity.checkName,
                parseStatus(entity.status),
                entity.message,
                entity.durationMs,
                entity.details == null ? Map.of() : Map.copyOf(entity.details));
    }

    private static HealthStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return HealthStatus.UNKNOWN;
        }
        try {
            return HealthStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return HealthStatus.UNKNOWN;
        }
    }

    private static TriggerType parseTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return TriggerType.API;
        }
        try {
            return TriggerType.valueOf(trigger);
        } catch (IllegalArgumentException e) {
            return TriggerType.API;
        }
    }
}
