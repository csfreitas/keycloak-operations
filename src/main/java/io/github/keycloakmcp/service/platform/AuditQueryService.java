package io.github.keycloakmcp.service.platform;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.AuditEventSummary;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuditQueryService {

    private final AuditRepository auditRepository;
    private final PlatformPersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final TargetAuthorizationService authorization;
    private final TargetRegistry targetRegistry;
    private final TargetResolver targetResolver;

    @Inject
    public AuditQueryService(
            AuditRepository auditRepository,
            PlatformPersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter,
            TargetAuthorizationService authorization,
            TargetRegistry targetRegistry,
            TargetResolver targetResolver) {
        this.auditRepository = auditRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.authorization = authorization;
        this.targetRegistry = targetRegistry;
        this.targetResolver = targetResolver;
    }

    public PageResult<AuditEventSummary> list(
            Optional<String> targetId,
            Optional<String> source,
            int page,
            int size) {
        authorization.assertSession();
        java.util.Set<String> targetIds;
        if (targetId.isPresent() && !targetId.get().isBlank()) {
            var target = targetResolver.require(targetId.get());
            authorization.assertAllowed(target, TargetPermission.READ);
            targetIds = java.util.Set.of(target.id().value());
        } else {
            targetIds = targetRegistry.list().stream()
                    .filter(t -> authorization.isAllowed(t, TargetPermission.READ))
                    .map(t -> t.id().value()).collect(java.util.stream.Collectors.toSet());
        }
        // Scope in the database, before pagination and count; never reveal global/unbound audit events.
        var pageResult = auditRepository.listForTargets(targetIds, source, page, size);
        List<AuditEventSummary> items = pageResult.items().stream()
                .map(mapper::toAuditSummary)
                .map(sensitiveDataFilter::redact)
                .toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }
}
