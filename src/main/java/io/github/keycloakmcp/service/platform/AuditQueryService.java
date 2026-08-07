package io.github.keycloakmcp.service.platform;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.AuditEventSummary;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuditQueryService {

    private final AuditRepository auditRepository;
    private final PlatformPersistenceMapper mapper;
    private final SensitiveDataFilter sensitiveDataFilter;

    @Inject
    public AuditQueryService(
            AuditRepository auditRepository,
            PlatformPersistenceMapper mapper,
            SensitiveDataFilter sensitiveDataFilter) {
        this.auditRepository = auditRepository;
        this.mapper = mapper;
        this.sensitiveDataFilter = sensitiveDataFilter;
    }

    public PageResult<AuditEventSummary> list(
            Optional<String> targetId,
            Optional<String> source,
            int page,
            int size) {
        var pageResult = auditRepository.list(targetId, source, page, size);
        List<AuditEventSummary> items = pageResult.items().stream()
                .map(mapper::toAuditSummary)
                .map(sensitiveDataFilter::redact)
                .toList();
        return new PageResult<>(items, pageResult.page(), pageResult.size(), pageResult.total());
    }
}
