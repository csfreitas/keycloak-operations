package io.github.keycloakmcp.service.change;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ChangeDiffEntry;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;

/** Deterministic validation, diff, apply, and verification for client security and flow settings. */
@ApplicationScoped
public class ClientSecuritySettingsChangeSupport {

    public static final String PKCE = "pkceCodeChallengeMethod";
    public static final String STANDARD_FLOW = "standardFlowEnabled";
    public static final String IMPLICIT_FLOW = "implicitFlowEnabled";
    public static final String DIRECT_ACCESS_GRANTS = "directAccessGrantsEnabled";
    public static final String SERVICE_ACCOUNTS = "serviceAccountsEnabled";
    public static final String PUBLIC_CLIENT = "publicClient";
    public static final String PKCE_S256 = "S256";
    public static final String PKCE_NONE = "NONE";

    public static final Set<String> SUPPORTED_PROPERTIES = Set.of(
            PKCE,
            STANDARD_FLOW,
            IMPLICIT_FLOW,
            DIRECT_ACCESS_GRANTS,
            SERVICE_ACCOUNTS,
            PUBLIC_CLIENT);

    private static final String PKCE_ATTR = "pkce.code.challenge.method";

    public PlannedClientSecurityChange plan(
            ClientRepresentation current,
            ClientSecurityChangeRequest request) {
        Objects.requireNonNull(current, "current");
        if (request == null) {
            throw McpException.invalidArgument("client security change request must not be null");
        }
        Map<String, Object> desired = desiredState(request);
        if (desired.isEmpty()) {
            throw McpException.invalidArgument("at least one client security or flow setting must be provided");
        }

        Map<String, Object> baseline = extractBaseline(current, desired.keySet());
        validateEffectiveCombination(current, desired);
        List<ChangeOperation> operations = new ArrayList<>();
        List<ChangeDiffEntry> diff = new ArrayList<>();
        for (Map.Entry<String, Object> entry : desired.entrySet()) {
            String property = entry.getKey();
            Object before = baseline.get(property);
            Object after = entry.getValue();
            if (Objects.equals(before, after)) {
                diff.add(new ChangeDiffEntry(property, DiffKind.UNCHANGED, display(before), display(after)));
                continue;
            }
            operations.add(new ChangeOperation(property, ChangeOperationType.UPDATE, before, after));
            diff.add(new ChangeDiffEntry(property, DiffKind.CHANGED, display(before), display(after)));
        }
        if (operations.isEmpty()) {
            throw McpException.invalidArgument("No effective client security or flow changes detected");
        }
        return new PlannedClientSecurityChange(
                immutableState(baseline),
                immutableState(desired),
                List.copyOf(operations),
                List.copyOf(diff));
    }

    public Map<String, Object> extractBaseline(
            ClientRepresentation representation,
            Collection<String> properties) {
        Objects.requireNonNull(representation, "representation");
        Map<String, Object> baseline = new LinkedHashMap<>();
        for (String property : SUPPORTED_PROPERTIES.stream().sorted().toList()) {
            if (properties.contains(property)) {
                baseline.put(property, readProperty(representation, property));
            }
        }
        return immutableState(baseline);
    }

    public void applyToRepresentation(
            ClientRepresentation representation,
            List<ChangeOperation> operations) {
        Objects.requireNonNull(representation, "representation");
        for (ChangeOperation operation : operations) {
            switch (operation.property()) {
                case PKCE -> applyPkce(representation, asString(operation.after(), PKCE));
                case STANDARD_FLOW -> representation.setStandardFlowEnabled(asBoolean(operation.after(), STANDARD_FLOW));
                case IMPLICIT_FLOW -> representation.setImplicitFlowEnabled(asBoolean(operation.after(), IMPLICIT_FLOW));
                case DIRECT_ACCESS_GRANTS -> representation.setDirectAccessGrantsEnabled(
                        asBoolean(operation.after(), DIRECT_ACCESS_GRANTS));
                case SERVICE_ACCOUNTS -> representation.setServiceAccountsEnabled(
                        asBoolean(operation.after(), SERVICE_ACCOUNTS));
                case PUBLIC_CLIENT -> representation.setPublicClient(asBoolean(operation.after(), PUBLIC_CLIENT));
                default -> throw McpException.writeNotSupported(
                        "Unsupported client security property: " + operation.property());
            }
        }
    }

    public List<ChangeDiffEntry> compareDesired(
            ClientRepresentation actual,
            Map<String, Object> desiredState) {
        List<ChangeDiffEntry> mismatches = new ArrayList<>();
        for (Map.Entry<String, Object> entry : desiredState.entrySet()) {
            String property = entry.getKey();
            Object expected = normalizePersisted(property, entry.getValue());
            Object observed = readProperty(actual, property);
            if (!Objects.equals(expected, observed)) {
                mismatches.add(new ChangeDiffEntry(
                        property,
                        DiffKind.CHANGED,
                        display(observed),
                        display(expected)));
            }
        }
        return List.copyOf(mismatches);
    }

    public boolean supports(List<ChangeOperation> operations, Map<String, Object> baselineState) {
        if (operations == null
                || operations.isEmpty()
                || operations.stream().anyMatch(operation -> !SUPPORTED_PROPERTIES.contains(operation.property()))) {
            return false;
        }
        // A PKCE-only legacy 0.8 scalar plan must continue through ClientConfigChangeSupport.
        // Typed Slice 2 plans normalize the absent state to the explicit NONE sentinel.
        return operations.stream().anyMatch(operation -> !PKCE.equals(operation.property()))
                || PKCE_NONE.equals(baselineState == null ? null : baselineState.get(PKCE))
                || operations.stream().anyMatch(operation -> PKCE_NONE.equals(operation.after()));
    }

    /** Unsafe weakening transitions are denied by default for production. */
    public boolean denyInProduction(List<ChangeOperation> operations) {
        return operations.stream().anyMatch(operation -> switch (operation.property()) {
            case PKCE -> PKCE_S256.equals(operation.before()) && PKCE_NONE.equals(operation.after());
            case IMPLICIT_FLOW, DIRECT_ACCESS_GRANTS, PUBLIC_CLIENT -> becameEnabled(operation);
            default -> false;
        });
    }

    private static Map<String, Object> desiredState(ClientSecurityChangeRequest request) {
        Map<String, Object> desired = new LinkedHashMap<>();
        if (request.pkceCodeChallengeMethod() != null) {
            desired.put(PKCE, normalizePkce(request.pkceCodeChallengeMethod()));
        }
        putIfPresent(desired, STANDARD_FLOW, request.standardFlowEnabled());
        putIfPresent(desired, IMPLICIT_FLOW, request.implicitFlowEnabled());
        putIfPresent(desired, DIRECT_ACCESS_GRANTS, request.directAccessGrantsEnabled());
        putIfPresent(desired, SERVICE_ACCOUNTS, request.serviceAccountsEnabled());
        putIfPresent(desired, PUBLIC_CLIENT, request.publicClient());
        return desired;
    }

    private static void validateEffectiveCombination(
            ClientRepresentation current,
            Map<String, Object> desired) {
        boolean publicClient = desired.containsKey(PUBLIC_CLIENT)
                ? (Boolean) desired.get(PUBLIC_CLIENT)
                : Boolean.TRUE.equals(current.isPublicClient());
        boolean serviceAccounts = desired.containsKey(SERVICE_ACCOUNTS)
                ? (Boolean) desired.get(SERVICE_ACCOUNTS)
                : Boolean.TRUE.equals(current.isServiceAccountsEnabled());
        if (publicClient && serviceAccounts) {
            throw McpException.invalidArgument(
                    "serviceAccountsEnabled requires a confidential client (publicClient=false)");
        }
    }

    private static Object readProperty(ClientRepresentation representation, String property) {
        return switch (property) {
            case PKCE -> readPkce(representation);
            case STANDARD_FLOW -> Boolean.TRUE.equals(representation.isStandardFlowEnabled());
            case IMPLICIT_FLOW -> Boolean.TRUE.equals(representation.isImplicitFlowEnabled());
            case DIRECT_ACCESS_GRANTS -> Boolean.TRUE.equals(representation.isDirectAccessGrantsEnabled());
            case SERVICE_ACCOUNTS -> Boolean.TRUE.equals(representation.isServiceAccountsEnabled());
            case PUBLIC_CLIENT -> Boolean.TRUE.equals(representation.isPublicClient());
            default -> throw McpException.writeNotSupported(
                    "Unsupported client security baseline property: " + property);
        };
    }

    private static Object normalizePersisted(String property, Object value) {
        if (PKCE.equals(property)) {
            return normalizePkce(asString(value, property));
        }
        return asBoolean(value, property);
    }

    private static String normalizePkce(String value) {
        if (value == null || value.isBlank()) {
            throw McpException.invalidArgument("pkceCodeChallengeMethod must be S256 or NONE");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PKCE_S256.equals(normalized) && !PKCE_NONE.equals(normalized)) {
            throw McpException.invalidArgument("pkceCodeChallengeMethod must be S256 or NONE");
        }
        return normalized;
    }

    private static String readPkce(ClientRepresentation representation) {
        if (representation.getAttributes() == null) {
            return PKCE_NONE;
        }
        String value = representation.getAttributes().get(PKCE_ATTR);
        return value == null || value.isBlank() ? PKCE_NONE : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void applyPkce(ClientRepresentation representation, String value) {
        Map<String, String> attributes = representation.getAttributes() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(representation.getAttributes());
        if (PKCE_NONE.equals(normalizePkce(value))) {
            // Keycloak treats an omitted attribute in a client PUT as "leave unchanged".
            // The admin-client serializer omits null map values, while an explicit empty
            // value reaches Keycloak and is normalized there by removing the attribute.
            attributes.put(PKCE_ATTR, "");
        } else {
            attributes.put(PKCE_ATTR, PKCE_S256);
        }
        representation.setAttributes(attributes);
    }

    private static void putIfPresent(Map<String, Object> values, String property, Boolean value) {
        if (value != null) {
            values.put(property, value);
        }
    }

    private static Boolean asBoolean(Object value, String property) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string
                && ("true".equalsIgnoreCase(string) || "false".equalsIgnoreCase(string))) {
            return Boolean.valueOf(string);
        }
        throw McpException.invalidArgument(property + " must be a boolean");
    }

    private static String asString(Object value, String property) {
        if (value instanceof String string) {
            return string;
        }
        throw McpException.invalidArgument(property + " must be a string");
    }

    private static boolean becameEnabled(ChangeOperation operation) {
        return !Boolean.TRUE.equals(operation.before()) && Boolean.TRUE.equals(operation.after());
    }

    private static String display(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Object> immutableState(Map<String, Object> state) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    public record PlannedClientSecurityChange(
            Map<String, Object> baselineState,
            Map<String, Object> desiredState,
            List<ChangeOperation> operations,
            List<ChangeDiffEntry> diff) {
    }
}
