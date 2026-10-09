package io.github.keycloakmcp.service.change;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.keycloak.representations.idm.ClientRepresentation;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Admission never substitutes redaction markers into authoritative change state. */
@ApplicationScoped
public class ChangeMetadataGuard {
    private final SensitiveDataFilter filter;
    private final ObjectMapper objectMapper;

    @Inject
    public ChangeMetadataGuard(SensitiveDataFilter filter, ObjectMapper objectMapper) {
        this.filter = filter;
        this.objectMapper = objectMapper;
    }

    public void requireSafeInput(Object... values) {
        if (containsSensitiveContent(values)) {
            throw McpException.invalidArgument("Sensitive content is not allowed in change metadata");
        }
    }

    public void requireSafeState(Object... values) {
        if (containsSensitiveContent(values)) {
            throw unsafeState();
        }
    }

    public void requireSafePlan(ChangeRecordEntity entity) {
        requireSafeState(entity.realm, entity.resourceId, entity.idempotencyKey,
                entity.baselineState, entity.desiredState, entity.operationsJson, entity.diffJson);
    }

    public void requireSafeObserved(ClientRepresentation client) {
        if (!isSafeObserved(client)) {
            throw unsafeState();
        }
    }

    public boolean isSafeObserved(ClientRepresentation client) {
        if (client == null) {
            return false;
        }
        Map<String, Object> metadata = asMap(client);
        // These known credential fields are excluded from observation/plan hashes and
        // explicitly cleared before sending. Other metadata must be safe unchanged:
        // an update resubmits the entire provider representation, including attributes.
        metadata.remove("secret");
        metadata.remove("registrationAccessToken");
        return !containsSensitiveContent(metadata);
    }

    public ChangeRecord outward(ChangeRecord record) {
        Map<String, Object> original = asMap(record);
        Map<String, Object> safe = filter.redactMetadata(original);
        // Canonical correlation/provenance identities are not descriptive metadata.
        // Authorization and integrity always consume the original entity, not this view.
        for (String field : List.of("changeId", "targetId", "actor", "approvedBy", "rejectedBy")) {
            if (original.containsKey(field)) {
                safe.put(field, original.get(field));
            }
        }
        return objectMapper.convertValue(safe, ChangeRecord.class);
    }

    private boolean containsSensitiveContent(Object... values) {
        for (Object value : values) {
            Object json = objectMapper.convertValue(value, Object.class);
            if (!Objects.equals(json, filter.redactMetadata(json))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return new LinkedHashMap<>(objectMapper.convertValue(value, Map.class));
    }

    private static McpException unsafeState() {
        return McpException.changeConflict(
                "REPLAN_REQUIRED: sensitive change metadata must be removed before planning or applying");
    }
}
