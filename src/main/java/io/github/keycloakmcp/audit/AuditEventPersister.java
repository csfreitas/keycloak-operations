package io.github.keycloakmcp.audit;

import java.util.Map;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.github.keycloakmcp.persistence.mapper.PlatformPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.AuditRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Separate bean so {@code @Transactional} applies when called from {@link AuditService}.
 */
@ApplicationScoped
public class AuditEventPersister {

    private static final Logger LOG = Logger.getLogger(AuditEventPersister.class);

    private final AuditRepository auditRepository;
    private final PlatformPersistenceMapper platformMapper;

    @Inject
    public AuditEventPersister(AuditRepository auditRepository, PlatformPersistenceMapper platformMapper) {
        this.auditRepository = auditRepository;
        this.platformMapper = platformMapper;
    }

    @Transactional
    public void persist(
            AuditSource source,
            String tool,
            String targetId,
            String operation,
            String status,
            Long durationMs,
            String traceId,
            Map<String, Object> params,
            Map<String, Object> metadata) {
        try {
            AuditEventEntity entity = platformMapper.newAuditEvent(
                    source, tool, targetId, operation, status, durationMs, traceId, params, metadata);
            auditRepository.persist(entity);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Failed to persist audit event tool=%s targetId=%s", tool, targetId);
        }
    }
}
