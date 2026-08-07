package io.github.keycloakmcp.service.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.domain.platform.HealthCheckSummary;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.HealthCheckResultEntity;
import io.github.keycloakmcp.persistence.entity.HealthCheckRunEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.HealthCheckRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.ServerInfoService;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Lightweight health checks (Admin API reachability + optional discovery signals).
 * Distinct from full assessments.
 */
@ApplicationScoped
public class HealthCheckService {

    private static final Logger LOG = Logger.getLogger(HealthCheckService.class);

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final ServerInfoService serverInfoService;
    private final HealthCheckRepository healthCheckRepository;
    private final PlatformPersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;

    @Inject
    public HealthCheckService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            ServerInfoService serverInfoService,
            HealthCheckRepository healthCheckRepository,
            PlatformPersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.serverInfoService = serverInfoService;
        this.healthCheckRepository = healthCheckRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
    }

    @Transactional
    public HealthCheckSummary run(String targetId, TriggerType triggerType) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        Instant started = Instant.now();
        String runId = UUID.randomUUID().toString();
        List<HealthCheckResultEntity> results = new ArrayList<>();
        HealthStatus overall = HealthStatus.HEALTHY;

        try {
            ServerInfo info = serverInfoService.getServerInfo(targetId);
            results.add(result(runId, targetId, "keycloak.serverInfo", HealthStatus.HEALTHY,
                    "Server info reachable", Map.of(
                            "product", info.product() == null ? "UNKNOWN" : info.product().name(),
                            "version", info.version() == null ? "" : info.version())));
        } catch (RuntimeException e) {
            LOG.warnf(e, "Health check serverInfo failed for target=%s", targetId);
            overall = HealthStatus.CRITICAL;
            results.add(result(runId, targetId, "keycloak.serverInfo", HealthStatus.CRITICAL,
                    e.getMessage(), Map.of()));
        }

        if (target.hasInfrastructure()) {
            results.add(result(runId, targetId, "infrastructure.configured", HealthStatus.HEALTHY,
                    "Infrastructure configuration present",
                    Map.of("type", target.infrastructureTypeOrNone().name())));
        } else {
            results.add(result(runId, targetId, "infrastructure.configured", HealthStatus.UNKNOWN,
                    "No infrastructure configuration", Map.of()));
        }

        Instant completed = Instant.now();
        HealthCheckRunEntity run = new HealthCheckRunEntity();
        run.id = runId;
        run.targetId = targetId;
        run.overallStatus = overall.name();
        run.triggerType = triggerType == null ? TriggerType.API.name() : triggerType.name();
        Map<String, Object> summary = new HashMap<>();
        summary.put("resultCount", results.size());
        summary.put("overallStatus", overall.name());
        run.summary = sensitiveDataFilter.redact(summary);
        run.startedAt = started;
        run.completedAt = completed;
        run.createdAt = Instant.now();

        healthCheckRepository.persistRunWithResults(run, results);
        return mapper.toHealthSummary(run);
    }

    public PageResult<HealthCheckSummary> list(String targetId, int page, int size) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        var pageResult = healthCheckRepository.listByTarget(targetId, page, size);
        List<HealthCheckSummary> items = pageResult.items().stream().map(mapper::toHealthSummary).toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    public java.util.Optional<HealthCheckSummary> latest(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return healthCheckRepository.findLatest(targetId).map(mapper::toHealthSummary);
    }

    private HealthCheckResultEntity result(
            String runId,
            String targetId,
            String name,
            HealthStatus status,
            String message,
            Map<String, Object> details) {
        HealthCheckResultEntity entity = new HealthCheckResultEntity();
        entity.id = UUID.randomUUID().toString();
        entity.healthCheckId = runId;
        entity.targetId = targetId;
        entity.checkName = name;
        entity.status = status.name();
        entity.message = sensitiveDataFilter.redactString(message);
        entity.details = details == null ? null : sensitiveDataFilter.redact(new HashMap<>(details));
        entity.createdAt = Instant.now();
        return entity;
    }
}
