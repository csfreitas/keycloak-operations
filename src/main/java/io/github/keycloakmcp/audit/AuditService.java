package io.github.keycloakmcp.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private static final Pattern CONFIGURATION_FINGERPRINT = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern CONFIGURATION_SCOPE = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern CONFIGURATION_TARGET = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern CONFIGURATION_OBSERVATION = Pattern.compile(
            "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final Set<AuditSource> CONFIGURATION_SOURCES = Set.of(AuditSource.REST, AuditSource.MCP, AuditSource.WEB);
    private static final Set<String> CONFIGURATION_STATUSES = Set.of(
            "SUCCESS", "FAILURE", "DENIED", "UNAVAILABLE", "COMPLETE", "PARTIAL");

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
        String correlationId = requestId == null ? newRequestId() : requestId;
        String safeRequestId = sensitiveDataFilter.redactString(correlationId);

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
                targetId == null || targetId.isBlank() || "-".equals(targetId) ? null : targetId,
                safeTool,
                success ? "SUCCESS" : "FAILURE",
                durationMs,
                params,
                correlationId);
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

    /**
     * Attribution-only audit for restricted configuration reads. Callers supply validated,
     * canonical fingerprints, never principal claims, source facts or provider diagnostics.
     * These fixed metadata fields survive METADATA mode; persistence remains best-effort.
     */
    public void recordConfigurationRead(
            AuditSource source,
            String operation,
            String targetId,
            String scopeId,
            String actorFingerprint,
            String clientFingerprint,
            String status,
            long durationMs,
            String observationId) {
        String tool = switch (operation == null ? "" : operation) {
            case "configuration-scopes" -> "keycloak_list_configuration_scopes";
            case "configuration-read" -> "keycloak_read_configuration";
            default -> null;
        };
        if (tool == null || source == null || !CONFIGURATION_SOURCES.contains(source)
                || status == null || !CONFIGURATION_STATUSES.contains(status) || durationMs < 0
                || !matches(CONFIGURATION_FINGERPRINT, actorFingerprint)
                || !matches(CONFIGURATION_FINGERPRINT, clientFingerprint)
                || scopeId != null && !matches(CONFIGURATION_SCOPE, scopeId)
                || targetId != null && !matches(CONFIGURATION_TARGET, targetId)
                || observationId != null && !matches(CONFIGURATION_OBSERVATION, observationId)) {
            throw new IllegalArgumentException("Invalid configuration audit metadata");
        }
        if (!platformConfig.audit().enabled()) return;

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("schemaVersion", "1.0");
        metadata.put("actorFingerprint", actorFingerprint);
        metadata.put("clientFingerprint", clientFingerprint);
        if (scopeId != null) metadata.put("scopeId", scopeId);
        auditEventPersister.persist(source, tool, targetId, operation, status, durationMs,
                observationId == null ? newRequestId() : observationId, null, Map.copyOf(metadata));
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private Map<String, Object> filterParams(Map<String, Object> params) {
        AuditMode mode = resolveMode();
        if (params == null || params.isEmpty() || mode == AuditMode.METADATA) {
            return null;
        }
        return sensitiveDataFilter.redactMetadata(new HashMap<>(params));
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
