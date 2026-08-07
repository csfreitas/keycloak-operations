package io.github.keycloakmcp.service.change;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ChangeDiffEntry;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Proof-of-concept semantic client configuration mutations for milestone 0.8.
 * Allowlisted non-sensitive properties only.
 */
@ApplicationScoped
public class ClientConfigChangeSupport {

    public static final Set<String> ALLOWED_PROPERTIES = Set.of(
            "name",
            "description",
            "pkceCodeChallengeMethod");

    private static final String PKCE_ATTR = "pkce.code.challenge.method";
    private static final Set<String> FORBIDDEN = Set.of(
            "secret", "clientSecret", "password", "privateKey", "credentials");

    private final SensitiveDataFilter sensitiveDataFilter;

    @Inject
    public ClientConfigChangeSupport(SensitiveDataFilter sensitiveDataFilter) {
        this.sensitiveDataFilter = sensitiveDataFilter;
    }

    public Map<String, Object> extractBaseline(ClientRepresentation representation) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("name", representation.getName());
        baseline.put("description", representation.getDescription());
        baseline.put("pkceCodeChallengeMethod", readPkce(representation));
        return sanitizeState(baseline);
    }

    public Map<String, Object> sanitizeDesiredState(Map<String, Object> desiredState) {
        if (desiredState == null || desiredState.isEmpty()) {
            throw McpException.invalidArgument("desiredState must not be empty");
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : desiredState.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw McpException.invalidArgument("desiredState keys must not be blank");
            }
            if (FORBIDDEN.stream().anyMatch(f -> f.equalsIgnoreCase(key))
                    || sensitiveDataFilter.isSensitiveKey(key)) {
                throw McpException.invalidArgument("Sensitive property not allowed in change request: " + key);
            }
            String canonical = canonicalizeProperty(key);
            if (!ALLOWED_PROPERTIES.contains(canonical)) {
                throw McpException.invalidArgument(
                        "Property not allowlisted for 0.8 client config updates: " + key
                                + " (allowed: " + ALLOWED_PROPERTIES + ")");
            }
            Object value = entry.getValue();
            if (value != null && !(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw McpException.invalidArgument("desiredState values must be scalar for property: " + key);
            }
            sanitized.put(canonical, value == null ? null : String.valueOf(value));
        }
        return sanitizeState(sanitized);
    }

    public PlannedClientChange plan(ClientRepresentation current, Map<String, Object> desiredState) {
        Map<String, Object> baseline = extractBaseline(current);
        Map<String, Object> desired = sanitizeDesiredState(desiredState);
        List<ChangeOperation> operations = new java.util.ArrayList<>();
        List<ChangeDiffEntry> diff = new java.util.ArrayList<>();

        for (Map.Entry<String, Object> entry : desired.entrySet()) {
            String property = entry.getKey();
            String after = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            String before = baseline.get(property) == null ? null : String.valueOf(baseline.get(property));
            if (Objects.equals(before, after)) {
                diff.add(new ChangeDiffEntry(property, DiffKind.UNCHANGED, before, after));
                continue;
            }
            DiffKind kind;
            if (before == null || before.isBlank()) {
                kind = DiffKind.ADDED;
            } else if (after == null || after.isBlank()) {
                kind = DiffKind.REMOVED;
            } else {
                kind = DiffKind.CHANGED;
            }
            operations.add(new ChangeOperation(property, ChangeOperationType.UPDATE, before, after));
            diff.add(new ChangeDiffEntry(property, kind, before, after));
        }
        if (operations.isEmpty()) {
            throw McpException.invalidArgument("No effective changes detected for desiredState");
        }
        return new PlannedClientChange(baseline, desired, operations, diff);
    }

    public void applyToRepresentation(ClientRepresentation representation, List<ChangeOperation> operations) {
        for (ChangeOperation op : operations) {
            switch (op.property()) {
                case "name" -> representation.setName(op.after());
                case "description" -> representation.setDescription(op.after());
                case "pkceCodeChallengeMethod" -> {
                    Map<String, String> attrs = representation.getAttributes();
                    if (attrs == null) {
                        attrs = new LinkedHashMap<>();
                        representation.setAttributes(attrs);
                    } else {
                        attrs = new LinkedHashMap<>(attrs);
                        representation.setAttributes(attrs);
                    }
                    if (op.after() == null || op.after().isBlank()) {
                        attrs.remove(PKCE_ATTR);
                    } else {
                        attrs.put(PKCE_ATTR, op.after());
                    }
                }
                default -> throw McpException.writeNotSupported("Unsupported client property: " + op.property());
            }
        }
    }

    public List<ChangeDiffEntry> compareDesired(ClientRepresentation actual, Map<String, Object> desiredState) {
        Map<String, Object> current = extractBaseline(actual);
        List<ChangeDiffEntry> mismatches = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : desiredState.entrySet()) {
            String property = entry.getKey();
            String expected = entry.getValue() == null ? null : String.valueOf(entry.getValue());
            String observed = current.get(property) == null ? null : String.valueOf(current.get(property));
            if (!Objects.equals(expected, observed)) {
                mismatches.add(new ChangeDiffEntry(property, DiffKind.CHANGED, observed, expected));
            }
        }
        return mismatches;
    }

    public String canonicalizeProperty(String property) {
        if ("pkce.code.challenge.method".equalsIgnoreCase(property)
                || "pkce_code_challenge_method".equalsIgnoreCase(property)) {
            return "pkceCodeChallengeMethod";
        }
        for (String allowed : ALLOWED_PROPERTIES) {
            if (allowed.equalsIgnoreCase(property)) {
                return allowed;
            }
        }
        return property;
    }

    private Map<String, Object> sanitizeState(Map<String, Object> state) {
        @SuppressWarnings("unchecked")
        Map<String, Object> redacted = sensitiveDataFilter.redact(new LinkedHashMap<>(state));
        // Drop any residual sensitive keys defensively.
        Set<String> keys = new LinkedHashSet<>(redacted.keySet());
        for (String key : keys) {
            if (sensitiveDataFilter.isSensitiveKey(key)) {
                redacted.remove(key);
            }
        }
        // Preserve null values (Map.copyOf forbids them).
        return Collections.unmodifiableMap(new LinkedHashMap<>(redacted));
    }

    private static String readPkce(ClientRepresentation representation) {
        if (representation.getAttributes() == null) {
            return null;
        }
        return representation.getAttributes().get(PKCE_ATTR);
    }

    public record PlannedClientChange(
            Map<String, Object> baselineState,
            Map<String, Object> desiredState,
            List<ChangeOperation> operations,
            List<ChangeDiffEntry> diff) {
    }
}
