package io.github.keycloakmcp.service.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.keycloakmcp.assessment.engine.AssessmentEngine;
import io.github.keycloakmcp.assessment.engine.AssessmentResult;
import io.github.keycloakmcp.assessment.engine.Finding;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.persistence.entity.AssessmentFindingEntity;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.github.keycloakmcp.persistence.mapper.AssessmentPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AssessmentRepository;
import io.github.keycloakmcp.persistence.repository.FindingRepository;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AssessmentHistoryService {

    private final AssessmentEngine assessmentEngine;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final AssessmentRepository assessmentRepository;
    private final FindingRepository findingRepository;
    private final AssessmentPersistenceMapper mapper;
    private final OperationalEventBus eventBus;

    @Inject
    public AssessmentHistoryService(
            AssessmentEngine assessmentEngine,
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            AssessmentRepository assessmentRepository,
            FindingRepository findingRepository,
            AssessmentPersistenceMapper mapper,
            OperationalEventBus eventBus) {
        this.assessmentEngine = assessmentEngine;
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.assessmentRepository = assessmentRepository;
        this.findingRepository = findingRepository;
        this.mapper = mapper;
        this.eventBus = eventBus;
    }

    @Transactional
    public AssessmentResult runAndPersist(String targetId, String profile, TriggerType triggerType) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.ASSESS);

        AssessmentResult result = assessmentEngine.assess(target, profile);
        String runId = UUID.randomUUID().toString();
        AssessmentRunEntity run = mapper.toRunEntity(result, runId, triggerType == null ? TriggerType.API : triggerType);
        assessmentRepository.persist(run);
        for (AssessmentFindingEntity finding : mapper.toFindingEntities(result, runId)) {
            findingRepository.persist(finding);
        }
        AssessmentResult persisted = result.withId(runId);
        eventBus.publish(OperationalEvent.of(
                "assessment_completed",
                targetId,
                "Assessment score " + persisted.overallScore(),
                runId));
        return persisted;
    }

    public PageResult<AssessmentRunSummary> list(String targetId, int page, int size) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        PageResult<AssessmentRunEntity> pageResult = assessmentRepository.listByTarget(targetId, page, size);
        List<AssessmentRunSummary> items = pageResult.items().stream().map(mapper::toSummary).toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    public AssessmentRunSummary get(String targetId, String assessmentId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        AssessmentRunEntity entity = assessmentRepository.findByIdForTarget(assessmentId, targetId)
                .orElseThrow(() -> McpException.forNotFound(
                        io.github.keycloakmcp.domain.error.ErrorCode.ASSESSMENT_FAILED,
                        "assessment",
                        assessmentId));
        return mapper.toSummary(entity);
    }

    public AssessmentRunSummary getById(String assessmentId) {
        AssessmentRunEntity entity = assessmentRepository.findByIdOptional(assessmentId)
                .orElseThrow(() -> McpException.forNotFound(
                        io.github.keycloakmcp.domain.error.ErrorCode.ASSESSMENT_FAILED,
                        "assessment",
                        assessmentId));
        Target target = targetResolver.require(entity.targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return mapper.toSummary(entity);
    }

    public Optional<AssessmentRunSummary> latest(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return assessmentRepository.findLatest(targetId).map(mapper::toSummary);
    }

    public PageResult<Finding> findings(
            String targetId,
            Optional<String> lifecycleStatus,
            Optional<String> severity,
            int page,
            int size) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        PageResult<AssessmentFindingEntity> pageResult =
                findingRepository.listByTarget(targetId, lifecycleStatus, severity, page, size);
        List<Finding> items = pageResult.items().stream().map(mapper::toDomainFinding).toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }
}
