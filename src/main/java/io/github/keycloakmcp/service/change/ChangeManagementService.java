package io.github.keycloakmcp.service.change;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ChangePolicyDecision;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ChangeResourceType;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.change.ChangeVerificationResult;
import io.github.keycloakmcp.domain.change.ClientCreateChangeRequest;
import io.github.keycloakmcp.domain.change.ClientEnabledChangeRequest;
import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.persistence.mapper.ChangePersistenceMapper;
import io.github.keycloakmcp.persistence.repository.ChangeRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.change.ChangePolicyEvaluator.PolicyResult;
import io.github.keycloakmcp.service.change.ClientConfigChangeSupport.PlannedClientChange;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ChangeManagementService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final StableAdminApiAdapter adminApi;
    private final ClientConfigChangeSupport clientConfigChangeSupport;
    private final ClientUrlSettingsChangeSupport clientUrlSettingsChangeSupport;
    private final ClientSecuritySettingsChangeSupport clientSecuritySettingsChangeSupport;
    private final ClientLifecycleChangeSupport clientLifecycleChangeSupport;
    private final ChangeRiskClassifier riskClassifier;
    private final ChangePolicyEvaluator policyEvaluator;
    private final ChangePlanFingerprinter fingerprinter;
    private final ChangeRepository changeRepository;
    private final ChangePersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public ChangeManagementService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            StableAdminApiAdapter adminApi,
            ClientConfigChangeSupport clientConfigChangeSupport,
            ClientUrlSettingsChangeSupport clientUrlSettingsChangeSupport,
            ClientSecuritySettingsChangeSupport clientSecuritySettingsChangeSupport,
            ClientLifecycleChangeSupport clientLifecycleChangeSupport,
            ChangeRiskClassifier riskClassifier,
            ChangePolicyEvaluator policyEvaluator,
            ChangePlanFingerprinter fingerprinter,
            ChangeRepository changeRepository,
            ChangePersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter,
            AuditService auditService) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.adminApi = adminApi;
        this.clientConfigChangeSupport = clientConfigChangeSupport;
        this.clientUrlSettingsChangeSupport = clientUrlSettingsChangeSupport;
        this.clientSecuritySettingsChangeSupport = clientSecuritySettingsChangeSupport;
        this.clientLifecycleChangeSupport = clientLifecycleChangeSupport;
        this.riskClassifier = riskClassifier;
        this.policyEvaluator = policyEvaluator;
        this.fingerprinter = fingerprinter;
        this.changeRepository = changeRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.auditService = auditService;
    }

    @Transactional
    public ChangeRecord planClientUpdate(
            String targetId,
            String realm,
            String clientId,
            Map<String, Object> desiredState,
            String actor,
            String idempotencyKey) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId, TargetPermission.PLAN);
            Map<String, Object> normalizedDesired = clientConfigChangeSupport.sanitizeDesiredState(desiredState);
            Optional<ChangeRecordEntity> existing = findIdempotent(
                    target, idempotencyKey, realm, clientId, ChangeOperationType.UPDATE, normalizedDesired);
            if (existing.isPresent()) {
                success = true;
                return mapper.toDomain(existing.get());
            }

            ClientRepresentation current = adminApi.findClientByClientId(target, realm, clientId);
            PlannedClientChange planned = clientConfigChangeSupport.plan(current, desiredState);
            ChangeRisk risk = riskClassifier.classify(planned.operations());
            PolicyResult policy = policyEvaluator.evaluate(
                    target.environment(), ChangeOperationType.UPDATE, risk, false);
            if (policy.decision() == ChangePolicyDecision.DENY) {
                throw McpException.policyDenied(policy.reason());
            }

            String planFingerprint = fingerprinter.fingerprintPlan(
                    target.id().value(),
                    realm,
                    ChangeResourceType.CLIENT.name(),
                    clientId,
                    ChangeOperationType.UPDATE.name(),
                    planned.operations());
            String baselineFingerprint = fingerprinter.fingerprintBaseline(planned.baselineState());

            Instant now = Instant.now();
            ChangeRecordEntity entity = new ChangeRecordEntity();
            entity.id = UUID.randomUUID().toString();
            entity.targetId = target.id().value();
            entity.environment = target.environment().name();
            entity.resourceType = ChangeResourceType.CLIENT.name();
            entity.resourceId = clientId;
            entity.realm = realm;
            entity.operation = ChangeOperationType.UPDATE.name();
            entity.risk = risk.name();
            entity.policyDecision = policy.decision().name();
            entity.policyReason = policy.reason();
            entity.requiresApproval = policy.requiresApproval();
            entity.status = policy.requiresApproval()
                    ? ChangeStatus.WAITING_APPROVAL.name()
                    : ChangeStatus.APPROVED.name();
            entity.planFingerprint = planFingerprint;
            entity.baselineFingerprint = baselineFingerprint;
            if (!policy.requiresApproval()) {
                entity.approvalFingerprint = planFingerprint;
                entity.approvedBy = "POLICY_AUTO";
                entity.approvedAt = now;
            }
            entity.desiredState = planned.desiredState();
            entity.baselineState = planned.baselineState();
            entity.diffJson = mapper.fromDiff(planned.diff());
            entity.operationsJson = mapper.fromOperations(planned.operations());
            entity.actor = targetAuthorization.currentActor();
            entity.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                    ? null
                    : idempotencyKey.trim();
            entity.createdAt = now;
            entity.updatedAt = now;
            stampSafetyContext(entity, target);
            changeRepository.persist(entity);

            auditChange("change.plan", entity, true, Map.of(
                    "risk", risk.name(),
                    "policy", policy.decision().name(),
                    "planFingerprint", planFingerprint));
            success = true;
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.planClientUpdate",
                    targetId,
                    realm,
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    @Transactional
    public ChangeRecord planClientUrlUpdate(ClientUrlChangeRequest request) {
        if (request == null) {
            throw McpException.invalidArgument("client URL change request must not be null");
        }
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(request.targetId(), TargetPermission.PLAN);
            Optional<ChangeRecordEntity> existing = findIdempotent(
                    target, request.idempotencyKey(), request.realm(), request.clientId(),
                    ChangeOperationType.UPDATE, clientUrlSettingsChangeSupport.desiredState(request));
            if (existing.isPresent()) {
                success = true;
                return mapper.toDomain(existing.get());
            }

            ClientRepresentation current = adminApi.findClientByClientId(
                    target, request.realm(), request.clientId());
            var planned = clientUrlSettingsChangeSupport.plan(current, request);
            ChangeRisk risk = riskClassifier.classifyClientUrls(planned.operations());
            PolicyResult policy = policyEvaluator.evaluateClientUrls(
                    target.environment(),
                    risk,
                    clientUrlSettingsChangeSupport.denyInProduction(planned.operations()));
            if (policy.decision() == ChangePolicyDecision.DENY) {
                throw McpException.policyDenied(policy.reason());
            }

            String planFingerprint = fingerprinter.fingerprintPlan(
                    target.id().value(),
                    request.realm(),
                    ChangeResourceType.CLIENT.name(),
                    request.clientId(),
                    ChangeOperationType.UPDATE.name(),
                    planned.operations());
            String baselineFingerprint = fingerprinter.fingerprintBaseline(planned.baselineState());

            Instant now = Instant.now();
            ChangeRecordEntity entity = new ChangeRecordEntity();
            entity.id = UUID.randomUUID().toString();
            entity.targetId = target.id().value();
            entity.environment = target.environment().name();
            entity.resourceType = ChangeResourceType.CLIENT.name();
            entity.resourceId = request.clientId();
            entity.realm = request.realm();
            entity.operation = ChangeOperationType.UPDATE.name();
            entity.risk = risk.name();
            entity.policyDecision = policy.decision().name();
            entity.policyReason = policy.reason();
            entity.requiresApproval = policy.requiresApproval();
            entity.status = policy.requiresApproval()
                    ? ChangeStatus.WAITING_APPROVAL.name()
                    : ChangeStatus.APPROVED.name();
            entity.planFingerprint = planFingerprint;
            entity.baselineFingerprint = baselineFingerprint;
            if (!policy.requiresApproval()) {
                entity.approvalFingerprint = planFingerprint;
                entity.approvedBy = "POLICY_AUTO";
                entity.approvedAt = now;
            }
            entity.desiredState = planned.desiredState();
            entity.baselineState = planned.baselineState();
            entity.diffJson = mapper.fromDiff(planned.diff());
            entity.operationsJson = mapper.fromOperations(planned.operations());
            entity.actor = targetAuthorization.currentActor();
            entity.idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                    ? null
                    : request.idempotencyKey().trim();
            entity.createdAt = now;
            entity.updatedAt = now;
            stampSafetyContext(entity, target);
            changeRepository.persist(entity);

            auditChange("change.plan.client_urls", entity, true, Map.of(
                    "risk", risk.name(),
                    "policy", policy.decision().name(),
                    "planFingerprint", planFingerprint));
            success = true;
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.planClientUrlUpdate",
                    request.targetId(),
                    request.realm(),
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    @Transactional
    public ChangeRecord planClientSecurityUpdate(ClientSecurityChangeRequest request) {
        if (request == null) {
            throw McpException.invalidArgument("client security change request must not be null");
        }
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(request.targetId(), TargetPermission.PLAN);
            Optional<ChangeRecordEntity> existing = findIdempotent(
                    target, request.idempotencyKey(), request.realm(), request.clientId(),
                    ChangeOperationType.UPDATE, ClientSecuritySettingsChangeSupport.desiredState(request));
            if (existing.isPresent()) {
                success = true;
                return mapper.toDomain(existing.get());
            }

            ClientRepresentation current = adminApi.findClientByClientId(
                    target, request.realm(), request.clientId());
            var planned = clientSecuritySettingsChangeSupport.plan(current, request);
            ChangeRisk risk = riskClassifier.classifyClientSecurity(planned.operations());
            PolicyResult policy = policyEvaluator.evaluateClientSecurity(
                    target.environment(),
                    risk,
                    clientSecuritySettingsChangeSupport.denyInProduction(planned.operations()));
            if (policy.decision() == ChangePolicyDecision.DENY) {
                throw McpException.policyDenied(policy.reason());
            }

            String planFingerprint = fingerprinter.fingerprintPlan(
                    target.id().value(),
                    request.realm(),
                    ChangeResourceType.CLIENT.name(),
                    request.clientId(),
                    ChangeOperationType.UPDATE.name(),
                    planned.operations());
            String baselineFingerprint = fingerprinter.fingerprintBaseline(planned.baselineState());

            Instant now = Instant.now();
            ChangeRecordEntity entity = new ChangeRecordEntity();
            entity.id = UUID.randomUUID().toString();
            entity.targetId = target.id().value();
            entity.environment = target.environment().name();
            entity.resourceType = ChangeResourceType.CLIENT.name();
            entity.resourceId = request.clientId();
            entity.realm = request.realm();
            entity.operation = ChangeOperationType.UPDATE.name();
            entity.risk = risk.name();
            entity.policyDecision = policy.decision().name();
            entity.policyReason = policy.reason();
            entity.requiresApproval = policy.requiresApproval();
            entity.status = policy.requiresApproval()
                    ? ChangeStatus.WAITING_APPROVAL.name()
                    : ChangeStatus.APPROVED.name();
            entity.planFingerprint = planFingerprint;
            entity.baselineFingerprint = baselineFingerprint;
            if (!policy.requiresApproval()) {
                entity.approvalFingerprint = planFingerprint;
                entity.approvedBy = "POLICY_AUTO";
                entity.approvedAt = now;
            }
            entity.desiredState = planned.desiredState();
            entity.baselineState = planned.baselineState();
            entity.diffJson = mapper.fromDiff(planned.diff());
            entity.operationsJson = mapper.fromOperations(planned.operations());
            entity.actor = targetAuthorization.currentActor();
            entity.idempotencyKey = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                    ? null
                    : request.idempotencyKey().trim();
            entity.createdAt = now;
            entity.updatedAt = now;
            stampSafetyContext(entity, target);
            changeRepository.persist(entity);

            auditChange("change.plan.client_security", entity, true, Map.of(
                    "risk", risk.name(),
                    "policy", policy.decision().name(),
                    "planFingerprint", planFingerprint));
            success = true;
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.planClientSecurityUpdate",
                    request.targetId(),
                    request.realm(),
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    @Transactional
    public ChangeRecord planClientCreate(ClientCreateChangeRequest request) {
        if (request == null) {
            throw McpException.invalidArgument("client create request must not be null");
        }
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(request.targetId(), TargetPermission.PLAN);
            var planned = clientLifecycleChangeSupport.planCreate(request);
            Optional<ChangeRecordEntity> existing = findIdempotent(
                    target, request.idempotencyKey(), request.realm(), request.clientId().trim(),
                    ChangeOperationType.CREATE, planned.desiredState());
            if (existing.isPresent()) {
                success = true;
                return mapper.toDomain(existing.get());
            }
            if (adminApi.clientExists(target, request.realm(), request.clientId().trim())) {
                throw McpException.changeConflict("client already exists: " + request.clientId().trim());
            }
            ChangeRisk risk = riskClassifier.classifyClientCreate(planned.operations());
            PolicyResult policy = policyEvaluator.evaluateClientCreate(
                    target.environment(),
                    risk,
                    clientLifecycleChangeSupport.denyCreateInProduction(planned.operations()));
            ChangeRecordEntity entity = persistPlan(
                    target,
                    request.realm(),
                    request.clientId().trim(),
                    ChangeOperationType.CREATE,
                    planned.baselineState(),
                    planned.desiredState(),
                    planned.operations(),
                    planned.diff(),
                    risk,
                    policy,
                    request.actor(),
                    request.idempotencyKey());
            auditChange("change.plan.client_create", entity, true, Map.of(
                    "risk", risk.name(),
                    "policy", policy.decision().name(),
                    "planFingerprint", entity.planFingerprint));
            success = true;
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.planClientCreate",
                    request.targetId(),
                    request.realm(),
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    @Transactional
    public ChangeRecord planClientEnabledUpdate(ClientEnabledChangeRequest request) {
        if (request == null) {
            throw McpException.invalidArgument("client enabled change request must not be null");
        }
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(request.targetId(), TargetPermission.PLAN);
            Optional<ChangeRecordEntity> existing = findIdempotent(
                    target, request.idempotencyKey(), request.realm(), request.clientId(),
                    ChangeOperationType.UPDATE, Map.of(ClientLifecycleChangeSupport.ENABLED, request.enabled()));
            if (existing.isPresent()) {
                success = true;
                return mapper.toDomain(existing.get());
            }
            ClientRepresentation current = adminApi.findClientByClientId(
                    target, request.realm(), request.clientId());
            var planned = clientLifecycleChangeSupport.planEnabled(current, request);
            ChangeRisk risk = riskClassifier.classifyClientEnabled(planned.operations());
            PolicyResult policy = policyEvaluator.evaluate(
                    target.environment(), ChangeOperationType.UPDATE, risk, false);
            ChangeRecordEntity entity = persistPlan(
                    target,
                    request.realm(),
                    request.clientId(),
                    ChangeOperationType.UPDATE,
                    planned.baselineState(),
                    planned.desiredState(),
                    planned.operations(),
                    planned.diff(),
                    risk,
                    policy,
                    request.actor(),
                    request.idempotencyKey());
            auditChange("change.plan.client_enabled", entity, true, Map.of(
                    "risk", risk.name(),
                    "policy", policy.decision().name(),
                    "planFingerprint", entity.planFingerprint));
            success = true;
            return mapper.toDomain(entity);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.planClientEnabledUpdate",
                    request.targetId(),
                    request.realm(),
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    public ChangeRecord getChange(String changeId) {
        ChangeRecordEntity entity = requireEntity(changeId);
        // READ on the owning target
        resolve(entity.targetId, TargetPermission.READ);
        return sensitiveDataFilter.redact(mapper.toDomain(entity));
    }

    public PageResult<ChangeRecord> listChanges(
            Optional<String> targetId, Optional<String> status, int page, int size) {
        if (targetId.isEmpty() || targetId.get().isBlank()) {
            throw McpException.invalidArgument("targetId is required when listing changes");
        }
        resolve(targetId.get(), TargetPermission.READ);
        PageResult<ChangeRecordEntity> pageResult = changeRepository.list(targetId, status, page, size);
        List<ChangeRecord> items = pageResult.items().stream()
                .map(mapper::toDomain)
                .map(sensitiveDataFilter::redact)
                .toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    @Transactional
    public ChangeRecord approve(String changeId, String approver) {
        ChangeRecordEntity entity = requireEntityForUpdate(changeId);
        Target target = resolve(entity.targetId, TargetPermission.APPROVE);
        validateSafetyContext(entity, target);
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.APPROVED) {
            return mapper.toDomain(entity);
        }
        if (status == ChangeStatus.REJECTED) {
            throw McpException.approvalInvalid("rejected change cannot be approved: " + changeId);
        }
        if (status == ChangeStatus.APPLIED || status == ChangeStatus.VERIFIED || status == ChangeStatus.APPLYING) {
            throw McpException.changeAlreadyApplied(changeId);
        }
        if (status != ChangeStatus.WAITING_APPROVAL && status != ChangeStatus.PLANNED) {
            throw McpException.approvalInvalid("change is not waiting for approval: " + status);
        }
        if (entity.planFingerprint == null || entity.planFingerprint.isBlank()) {
            throw McpException.approvalInvalid("change has no plan fingerprint");
        }
        entity.status = ChangeStatus.APPROVED.name();
        entity.approvedBy = targetAuthorization.currentActor();
        entity.approvedAt = Instant.now();
        entity.approvalFingerprint = entity.planFingerprint;
        entity.updatedAt = Instant.now();
        auditChange("change.approve", entity, true, Map.of("approvedBy", entity.approvedBy));
        return mapper.toDomain(entity);
    }

    @Transactional
    public ChangeRecord reject(String changeId, String rejector, String reason) {
        ChangeRecordEntity entity = requireEntityForUpdate(changeId);
        resolve(entity.targetId, TargetPermission.APPROVE);
        ChangeStatus status = ChangeStatus.valueOf(entity.status);
        if (status == ChangeStatus.APPLIED || status == ChangeStatus.VERIFIED || status == ChangeStatus.APPLYING) {
            throw McpException.changeAlreadyApplied(changeId);
        }
        if (status == ChangeStatus.REJECTED) {
            return mapper.toDomain(entity);
        }
        entity.status = ChangeStatus.REJECTED.name();
        entity.rejectedBy = targetAuthorization.currentActor();
        entity.rejectedAt = Instant.now();
        entity.rejectionReason = reason;
        entity.updatedAt = Instant.now();
        auditChange("change.reject", entity, true, Map.of("rejectedBy", entity.rejectedBy));
        return mapper.toDomain(entity);
    }

    @Transactional(dontRollbackOn = McpException.class)
    public ChangeRecord apply(String changeId, String actor) {
        long start = System.currentTimeMillis();
        boolean success = false;
        ChangeRecordEntity entity = requireEntityForUpdate(changeId);
        try {
            Target target = resolve(entity.targetId, TargetPermission.WRITE);
            ChangeStatus status = ChangeStatus.valueOf(entity.status);

            if (status == ChangeStatus.VERIFIED || status == ChangeStatus.APPLIED) {
                success = true;
                return mapper.toDomain(entity);
            }
            if (status == ChangeStatus.REJECTED) {
                throw McpException.policyDenied("rejected change cannot be applied");
            }
            validateSafetyContext(entity, target);
            if (entity.policyDecision != null
                    && ChangePolicyDecision.valueOf(entity.policyDecision) == ChangePolicyDecision.DENY) {
                throw McpException.policyDenied(entity.policyReason);
            }
            if (entity.requiresApproval) {
                if (status != ChangeStatus.APPROVED) {
                    throw McpException.changeNotApproved(changeId);
                }
                if (entity.approvalFingerprint == null
                        || !entity.approvalFingerprint.equals(entity.planFingerprint)) {
                    throw McpException.approvalInvalid(
                            "approval fingerprint does not match plan fingerprint (replan required)");
                }
            } else if (status != ChangeStatus.APPROVED && status != ChangeStatus.PLANNED) {
                throw McpException.changeNotApproved(changeId);
            }

            entity.status = ChangeStatus.APPLYING.name();
            entity.updatedAt = Instant.now();

            if (!ChangeResourceType.CLIENT.name().equals(entity.resourceType)) {
                throw McpException.writeNotSupported("resource type not supported in 0.8: " + entity.resourceType);
            }

            List<ChangeOperation> operations = mapper.toDomain(entity).operations();
            if (ChangeOperationType.CREATE.name().equals(entity.operation)) {
                applyClientCreate(entity, target);
                success = ChangeStatus.VERIFIED.name().equals(entity.status);
                return mapper.toDomain(entity);
            }

            ClientRepresentation current =
                    adminApi.findClientByClientId(target, entity.realm, entity.resourceId);
            boolean clientUrlChange = clientUrlSettingsChangeSupport.supports(operations);
            boolean clientSecurityChange = clientSecuritySettingsChangeSupport.supports(
                    operations, entity.baselineState);
            boolean clientEnabledChange = clientLifecycleChangeSupport.supportsEnabledUpdate(operations);
            Map<String, Object> liveBaseline;
            if (clientUrlChange) {
                liveBaseline = clientUrlSettingsChangeSupport.extractBaseline(
                        current, entity.baselineState.keySet());
            } else if (clientSecurityChange) {
                liveBaseline = clientSecuritySettingsChangeSupport.extractBaseline(
                        current, entity.baselineState.keySet());
            } else if (clientEnabledChange) {
                liveBaseline = clientLifecycleChangeSupport.extractEnabledBaseline(current);
            } else {
                liveBaseline = clientConfigChangeSupport.extractBaseline(current);
            }
            String liveBaselineFingerprint = fingerprinter.fingerprintBaseline(liveBaseline);
            if (entity.baselineFingerprint != null
                    && !entity.baselineFingerprint.equals(liveBaselineFingerprint)) {
                entity.status = ChangeStatus.FAILED.name();
                entity.resultMessage = "REPLAN_REQUIRED: target resource changed since planning";
                entity.updatedAt = Instant.now();
                auditChange("change.apply.conflict", entity, false, Map.of("reason", "REPLAN_REQUIRED"));
                throw McpException.changeConflict(
                        "REPLAN_REQUIRED: resource changed since plan was created for change " + changeId);
            }

            if (clientUrlChange) {
                clientUrlSettingsChangeSupport.applyToRepresentation(current, operations);
            } else if (clientSecurityChange) {
                clientSecuritySettingsChangeSupport.applyToRepresentation(current, operations);
            } else if (clientEnabledChange) {
                clientLifecycleChangeSupport.applyEnabled(current, operations);
            } else {
                clientConfigChangeSupport.applyToRepresentation(current, operations);
            }
            // Never send secret fields back even if present on the representation.
            current.setSecret(null);
            adminApi.updateClient(target, entity.realm, current);

            entity.appliedAt = Instant.now();
            entity.status = ChangeStatus.APPLIED.name();
            entity.resultMessage = "Applied by " + targetAuthorization.currentActor();
            entity.updatedAt = Instant.now();

            ChangeVerificationResult verification = verifyEntity(entity, target);
            if (verification.verified()) {
                entity.status = ChangeStatus.VERIFIED.name();
            } else {
                entity.status = ChangeStatus.FAILED.name();
                entity.resultMessage = verification.message();
            }
            success = verification.verified();
            auditChange("change.apply", entity, success, Map.of(
                    "verification", entity.verificationStatus == null ? "" : entity.verificationStatus));
            if (!verification.verified()) {
                throw McpException.verificationFailed(verification.message());
            }
            return mapper.toDomain(entity);
        } catch (McpException e) {
            if (ChangeStatus.APPLYING.name().equals(entity.status)) {
                entity.status = ChangeStatus.FAILED.name();
                entity.resultMessage = e.getCode() + ": " + e.getMessage();
                entity.updatedAt = Instant.now();
                auditChange("change.apply.failed", entity, false, Map.of("errorCode", e.getCode().name()));
            }
            throw e;
        } catch (RuntimeException e) {
            if (ChangeStatus.APPLYING.name().equals(entity.status)) {
                entity.status = ChangeStatus.FAILED.name();
                entity.resultMessage = "INTERNAL_ERROR: apply failed";
                entity.updatedAt = Instant.now();
                auditChange("change.apply.failed", entity, false, Map.of("errorCode", "INTERNAL_ERROR"));
            }
            throw McpException.internal("Change apply failed", e);
        } finally {
            auditService.logToolInvocation(
                    "ChangeManagementService.apply",
                    entity.targetId,
                    entity.realm,
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    @Transactional
    public ChangeRecord verify(String changeId) {
        ChangeRecordEntity entity = requireEntityForUpdate(changeId);
        Target target = resolve(entity.targetId, TargetPermission.READ);
        if (entity.status.equals(ChangeStatus.REJECTED.name())) {
            throw McpException.invalidArgument("cannot verify rejected change");
        }
        ChangeVerificationResult result = verifyEntity(entity, target);
        if (result.verified()
                && (entity.status.equals(ChangeStatus.APPLIED.name())
                        || entity.status.equals(ChangeStatus.VERIFIED.name()))) {
            entity.status = ChangeStatus.VERIFIED.name();
        } else if (!result.verified()
                && (entity.status.equals(ChangeStatus.APPLIED.name())
                        || entity.status.equals(ChangeStatus.VERIFIED.name())
                        || entity.status.equals(ChangeStatus.FAILED.name()))) {
            entity.status = ChangeStatus.FAILED.name();
        }
        entity.updatedAt = Instant.now();
        auditChange("change.verify", entity, result.verified(), Map.of(
                "verification", result.status()));
        return mapper.toDomain(entity);
    }

    private ChangeVerificationResult verifyEntity(ChangeRecordEntity entity, Target target) {
        ClientRepresentation actual =
                adminApi.findClientByClientId(target, entity.realm, entity.resourceId);
        List<ChangeOperation> operations = mapper.toDomain(entity).operations();
        List<io.github.keycloakmcp.domain.change.ChangeDiffEntry> mismatches;
        if (ChangeOperationType.CREATE.name().equals(entity.operation)) {
            mismatches = clientLifecycleChangeSupport.compareCreateDesired(actual, entity.desiredState);
        } else if (clientUrlSettingsChangeSupport.supports(operations)) {
            mismatches = clientUrlSettingsChangeSupport.compareDesired(actual, entity.desiredState);
        } else if (clientSecuritySettingsChangeSupport.supports(operations, entity.baselineState)) {
            mismatches = clientSecuritySettingsChangeSupport.compareDesired(actual, entity.desiredState);
        } else if (clientLifecycleChangeSupport.supportsEnabledUpdate(operations)) {
            mismatches = clientLifecycleChangeSupport.compareEnabledDesired(actual, entity.desiredState);
        } else {
            mismatches = clientConfigChangeSupport.compareDesired(actual, entity.desiredState);
        }
        ChangeVerificationResult result = mismatches.isEmpty()
                ? ChangeVerificationResult.verified("Desired state confirmed by read-back")
                : ChangeVerificationResult.failed(
                        "Desired state mismatch after read-back for: "
                                + mismatches.stream()
                                        .map(io.github.keycloakmcp.domain.change.ChangeDiffEntry::property)
                                        .sorted()
                                        .collect(java.util.stream.Collectors.joining(", ")),
                        mismatches);
        entity.verificationStatus = result.status();
        entity.verificationMessage = result.message();
        entity.verificationJson = mapper.fromDiff(result.mismatches());
        return result;
    }

    private void applyClientCreate(ChangeRecordEntity entity, Target target) {
        if (adminApi.clientExists(target, entity.realm, entity.resourceId)) {
            entity.status = ChangeStatus.FAILED.name();
            entity.resultMessage = "REPLAN_REQUIRED: client now exists";
            entity.updatedAt = Instant.now();
            auditChange("change.apply.conflict", entity, false, Map.of("reason", "REPLAN_REQUIRED"));
            throw McpException.changeConflict(
                    "REPLAN_REQUIRED: client exists since plan was created for change " + entity.id);
        }
        String liveBaselineFingerprint = fingerprinter.fingerprintBaseline(Map.of("exists", false));
        if (!liveBaselineFingerprint.equals(entity.baselineFingerprint)) {
            throw McpException.changeConflict("REPLAN_REQUIRED: client creation baseline changed");
        }
        ClientRepresentation representation =
                clientLifecycleChangeSupport.toCreateRepresentation(entity.desiredState);
        representation.setSecret(null);
        adminApi.createClient(target, entity.realm, representation);
        entity.appliedAt = Instant.now();
        entity.status = ChangeStatus.APPLIED.name();
        entity.updatedAt = Instant.now();
        ChangeVerificationResult verification = verifyEntity(entity, target);
        entity.status = verification.verified() ? ChangeStatus.VERIFIED.name() : ChangeStatus.FAILED.name();
        entity.resultMessage = verification.message();
        auditChange("change.apply.client_create", entity, verification.verified(), Map.of(
                "verification", entity.verificationStatus == null ? "" : entity.verificationStatus));
        if (!verification.verified()) {
            throw McpException.verificationFailed(verification.message());
        }
    }

    private Optional<ChangeRecordEntity> findIdempotent(
            Target target, String idempotencyKey, String realm, String resourceId,
            ChangeOperationType operation, Map<String, Object> desired) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        if (idempotencyKey.trim().length() > 128) {
            throw McpException.invalidArgument("idempotencyKey must be at most 128 characters");
        }
        Optional<ChangeRecordEntity> existing =
                changeRepository.findByIdempotency(target.id().value(), idempotencyKey.trim());
        if (existing.isPresent()) {
            ChangeRecordEntity entity = existing.get();
            if (!Objects.equals(realm, entity.realm)
                    || !Objects.equals(resourceId, entity.resourceId)
                    || !operation.name().equals(entity.operation)
                    || !Objects.equals(desired, entity.desiredState)) {
                throw McpException.changeConflict(
                        "idempotencyKey already belongs to a different change request");
            }
        }
        return existing;
    }

    private ChangeRecordEntity persistPlan(
            Target target,
            String realm,
            String resourceId,
            ChangeOperationType operation,
            Map<String, Object> baseline,
            Map<String, Object> desired,
            List<ChangeOperation> operations,
            List<io.github.keycloakmcp.domain.change.ChangeDiffEntry> diff,
            ChangeRisk risk,
            PolicyResult policy,
            String actor,
            String idempotencyKey) {
        if (policy.decision() == ChangePolicyDecision.DENY) {
            throw McpException.policyDenied(policy.reason());
        }
        String planFingerprint = fingerprinter.fingerprintPlan(
                target.id().value(), realm, ChangeResourceType.CLIENT.name(), resourceId,
                operation.name(), operations);
        String baselineFingerprint = fingerprinter.fingerprintBaseline(baseline);
        Instant now = Instant.now();
        ChangeRecordEntity entity = new ChangeRecordEntity();
        entity.id = UUID.randomUUID().toString();
        entity.targetId = target.id().value();
        entity.environment = target.environment().name();
        entity.resourceType = ChangeResourceType.CLIENT.name();
        entity.resourceId = resourceId;
        entity.realm = realm;
        entity.operation = operation.name();
        entity.risk = risk.name();
        entity.policyDecision = policy.decision().name();
        entity.policyReason = policy.reason();
        entity.requiresApproval = policy.requiresApproval();
        entity.status = policy.requiresApproval()
                ? ChangeStatus.WAITING_APPROVAL.name()
                : ChangeStatus.APPROVED.name();
        entity.planFingerprint = planFingerprint;
        entity.baselineFingerprint = baselineFingerprint;
        if (!policy.requiresApproval()) {
            entity.approvalFingerprint = planFingerprint;
            entity.approvedBy = "POLICY_AUTO";
            entity.approvedAt = now;
        }
        entity.desiredState = desired;
        entity.baselineState = baseline;
        entity.diffJson = mapper.fromDiff(diff);
        entity.operationsJson = mapper.fromOperations(operations);
        entity.actor = targetAuthorization.currentActor();
        entity.idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? null
                : idempotencyKey.trim();
        entity.createdAt = now;
        entity.updatedAt = now;
        stampSafetyContext(entity, target);
        changeRepository.persist(entity);
        return entity;
    }

    private ChangeRecordEntity requireEntityForUpdate(String changeId) {
        if (changeId == null || changeId.isBlank()) {
            throw McpException.invalidArgument("changeId must not be blank");
        }
        return changeRepository.findByIdForUpdate(changeId.trim())
                .orElseThrow(() -> McpException.changeNotFound(changeId));
    }

    private void stampSafetyContext(ChangeRecordEntity entity, Target target) {
        entity.policyRevision = ChangePolicyEvaluator.REVISION;
        entity.targetContextFingerprint = targetContextFingerprint(target);
        entity.integrityFingerprint = integrityFingerprint(entity);
    }

    private String targetContextFingerprint(Target target) {
        Map<String, Object> context = new HashMap<>();
        context.put("targetId", target.id().value());
        context.put("environment", target.environment().name());
        context.put("type", target.type().name());
        context.put("url", target.keycloak().url());
        context.put("authRealm", target.keycloak().authRealm());
        context.put("clientId", target.keycloak().clientId());
        context.put("credentialRef", target.keycloak().credentialRef());
        return fingerprinter.fingerprintContext(context);
    }

    private String integrityFingerprint(ChangeRecordEntity entity) {
        Map<String, Object> context = new HashMap<>();
        context.put("targetId", entity.targetId);
        context.put("targetContext", entity.targetContextFingerprint);
        context.put("environment", entity.environment);
        context.put("realm", entity.realm);
        context.put("resourceType", entity.resourceType);
        context.put("resourceId", entity.resourceId);
        context.put("operation", entity.operation);
        context.put("operations", entity.operationsJson);
        context.put("desired", entity.desiredState);
        context.put("baseline", entity.baselineState);
        context.put("baselineFingerprint", entity.baselineFingerprint);
        context.put("planFingerprint", entity.planFingerprint);
        context.put("risk", entity.risk);
        context.put("policyDecision", entity.policyDecision);
        context.put("requiresApproval", entity.requiresApproval);
        context.put("policyRevision", entity.policyRevision);
        return fingerprinter.fingerprintContext(context);
    }

    private void validateSafetyContext(ChangeRecordEntity entity, Target target) {
        if (!ChangePolicyEvaluator.REVISION.equals(entity.policyRevision)
                || !Objects.equals(targetContextFingerprint(target), entity.targetContextFingerprint)) {
            throw McpException.changeConflict("REPLAN_REQUIRED: target or policy context changed or is unavailable");
        }
        if (!Objects.equals(entity.integrityFingerprint, integrityFingerprint(entity))) {
            throw McpException.approvalInvalid("REPLAN_REQUIRED: stored plan integrity does not match approval context");
        }
        List<ChangeOperation> operations = mapper.toDomain(entity).operations();
        PolicyResult currentPolicy;
        ChangeRisk currentRisk;
        if (ChangeOperationType.CREATE.name().equals(entity.operation)) {
            currentRisk = riskClassifier.classifyClientCreate(operations);
            currentPolicy = policyEvaluator.evaluateClientCreate(target.environment(), currentRisk,
                    clientLifecycleChangeSupport.denyCreateInProduction(operations));
        } else if (clientUrlSettingsChangeSupport.supports(operations)) {
            currentRisk = riskClassifier.classifyClientUrls(operations);
            currentPolicy = policyEvaluator.evaluateClientUrls(target.environment(), currentRisk,
                    clientUrlSettingsChangeSupport.denyInProduction(operations));
        } else if (clientSecuritySettingsChangeSupport.supports(operations, entity.baselineState)) {
            currentRisk = riskClassifier.classifyClientSecurity(operations);
            currentPolicy = policyEvaluator.evaluateClientSecurity(target.environment(), currentRisk,
                    clientSecuritySettingsChangeSupport.denyInProduction(operations));
        } else if (clientLifecycleChangeSupport.supportsEnabledUpdate(operations)) {
            currentRisk = riskClassifier.classifyClientEnabled(operations);
            currentPolicy = policyEvaluator.evaluate(target.environment(), ChangeOperationType.UPDATE, currentRisk, false);
        } else {
            if (operations.isEmpty() || operations.stream().anyMatch(
                    op -> !ClientConfigChangeSupport.ALLOWED_PROPERTIES.contains(op.property()))) {
                throw McpException.writeNotSupported("REPLAN_REQUIRED: unsupported legacy change operations");
            }
            currentRisk = riskClassifier.classify(operations);
            currentPolicy = policyEvaluator.evaluate(target.environment(), ChangeOperationType.UPDATE, currentRisk, false);
        }
        if (currentPolicy.decision() == ChangePolicyDecision.DENY) {
            throw McpException.policyDenied(currentPolicy.reason());
        }
        if (!currentRisk.name().equals(entity.risk)
                || !currentPolicy.decision().name().equals(entity.policyDecision)
                || currentPolicy.requiresApproval() != entity.requiresApproval) {
            throw McpException.changeConflict("REPLAN_REQUIRED: effective risk or approval policy changed");
        }
    }

    private ChangeRecordEntity requireEntity(String changeId) {
        if (changeId == null || changeId.isBlank()) {
            throw McpException.invalidArgument("changeId must not be blank");
        }
        return changeRepository.findByIdOptional(changeId.trim())
                .orElseThrow(() -> McpException.changeNotFound(changeId));
    }

    private Target resolve(String targetId, TargetPermission permission) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, permission);
        return target;
    }

    private void auditChange(String operation, ChangeRecordEntity entity, boolean success, Map<String, Object> extra) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("changeId", entity.id);
        metadata.put("resourceType", entity.resourceType);
        metadata.put("resourceId", entity.resourceId);
        metadata.put("status", entity.status);
        metadata.put("risk", entity.risk);
        metadata.put("policyDecision", entity.policyDecision);
        metadata.put("realm", entity.realm);
        if (extra != null) {
            metadata.putAll(extra);
        }
        auditService.record(
                AuditSource.SYSTEM,
                operation,
                entity.targetId,
                operation,
                success ? "SUCCESS" : "FAILURE",
                0L,
                sensitiveDataFilter.redact(metadata));
    }
}
