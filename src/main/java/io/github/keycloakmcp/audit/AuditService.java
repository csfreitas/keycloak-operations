package io.github.keycloakmcp.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.PlatformConfig;
import io.github.keycloakmcp.domain.platform.AuditMode;
import io.github.keycloakmcp.domain.platform.AuditSource;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);

    private final SensitiveDataFilter sensitiveDataFilter;
    private final PlatformConfig platformConfig;
    private final AuditEventPersister auditEventPersister;

    @Inject
    public AuditService(
            SensitiveDataFilter sensitiveDataFilter,
            PlatformConfig platformConfig,
            AuditEventPersister auditEventPersister) {
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.platformConfig = platformConfig;
        this.auditEventPersister = auditEventPersister;
    }

    public String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public void logToolInvocation(
            String requestId,
            String tool,
            String targetId,
            String realm,
            long durationMs,
            boolean success) {
        String safeTool = sensitiveDataFilter.redactString(tool);
        String safeTarget = sensitiveDataFilter.redactString(targetId);
        String safeRealm = sensitiveDataFilter.redactString(realm);
        String safeRequestId = requestId == null ? newRequestId() : requestId;

        LOG.infof(
                "mcp_audit timestamp=%s requestId=%s tool=%s targetId=%s realm=%s durationMs=%d success=%s",
                Instant.now(),
                safeRequestId,
                safeTool,
                safeTarget == null || safeTarget.isBlank() ? "-" : safeTarget,
                safeRealm == null ? "-" : safeRealm,
                durationMs,
                success);

        Map<String, Object> params = new HashMap<>();
        if (realm != null) {
            params.put("realm", realm);
        }
        record(
                AuditSource.MCP,
                safeTool,
                safeTarget == null || safeTarget.isBlank() || "-".equals(safeTarget) ? null : safeTarget,
                safeTool,
                success ? "SUCCESS" : "FAILURE",
                durationMs,
                params,
                safeRequestId);
    }

    public void logToolInvocation(
            String tool,
            String targetId,
            String realm,
            long durationMs,
            boolean success) {
        logToolInvocation(newRequestId(), tool, targetId, realm, durationMs, success);
    }

    /**
     * Backward-compatible overload without targetId (logs {@code targetId=-}).
     */
    public void logToolInvocation(String tool, String realm, long durationMs, boolean success) {
        logToolInvocation(tool, "-", realm, durationMs, success);
    }

    /**
     * Persist (when enabled) an audit event. Params are filtered according to
     * {@link PlatformConfig.Audit#mode()}.
     */
    public void record(
            AuditSource source,
            String tool,
            String targetId,
            String operation,
            String status,
            long durationMs,
            Map<String, Object> sanitizedParams) {
        record(source, tool, targetId, operation, status, durationMs, sanitizedParams, newRequestId());
    }

    public void record(
            AuditSource source,
            String tool,
            String targetId,
            String operation,
            String status,
            long durationMs,
            Map<String, Object> params,
            String traceId) {
        if (!platformConfig.audit().enabled()) {
            return;
        }
        Map<String, Object> filtered = filterParams(params);
        auditEventPersister.persist(
                source,
                tool,
                targetId,
                operation,
                status,
                durationMs,
                traceId,
                filtered,
                Map.of("mode", resolveMode().name()));
    }

    private Map<String, Object> filterParams(Map<String, Object> params) {
        AuditMode mode = resolveMode();
        if (params == null || params.isEmpty() || mode == AuditMode.METADATA) {
            return null;
        }
        return sensitiveDataFilter.redact(new HashMap<>(params));
    }

    private AuditMode resolveMode() {
        String raw = platformConfig.audit().mode();
        if (raw == null || raw.isBlank()) {
            return AuditMode.SANITIZED;
        }
        try {
            return AuditMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AuditMode.SANITIZED;
        }
    }
}
