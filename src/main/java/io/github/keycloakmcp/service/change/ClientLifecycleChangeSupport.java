package io.github.keycloakmcp.service.change;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ChangeDiffEntry;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ClientCreateChangeRequest;
import io.github.keycloakmcp.domain.change.ClientEnabledChangeRequest;
import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;

/** Deterministic, secret-free semantics for Slice 3 client lifecycle changes. */
@ApplicationScoped
public class ClientLifecycleChangeSupport {

    public static final String ENABLED = "enabled";
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._-]{1,255}");

    public PlannedClientLifecycleChange planCreate(ClientCreateChangeRequest request) {
        if (request == null) {
            throw McpException.invalidArgument("client create request must not be null");
        }
        String clientId = required(request.clientId(), "clientId");
        if (!CLIENT_ID.matcher(clientId).matches()) {
            throw McpException.invalidArgument(
                    "clientId must contain only letters, digits, '.', '_' or '-' and be at most 255 characters");
        }
        String name = optionalText(request.name(), "name", 255);
        String description = optionalText(request.description(), "description", 2000);
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        boolean publicClient = request.publicClient() == null || request.publicClient();
        boolean standardFlow = request.standardFlowEnabled() == null || request.standardFlowEnabled();
        boolean directGrants = Boolean.TRUE.equals(request.directAccessGrantsEnabled());
        boolean serviceAccounts = Boolean.TRUE.equals(request.serviceAccountsEnabled());
        if (serviceAccounts && publicClient) {
            throw McpException.invalidArgument("service accounts require a confidential client (publicClient=false)");
        }

        Map<String, Object> desired = new LinkedHashMap<>();
        desired.put("clientId", clientId);
        desired.put("name", name);
        desired.put("description", description);
        desired.put(ENABLED, enabled);
        desired.put("publicClient", publicClient);
        desired.put("standardFlowEnabled", standardFlow);
        desired.put("directAccessGrantsEnabled", directGrants);
        desired.put("serviceAccountsEnabled", serviceAccounts);

        List<ChangeOperation> operations = new ArrayList<>();
        List<ChangeDiffEntry> diff = new ArrayList<>();
        desired.forEach((property, value) -> {
            operations.add(new ChangeOperation(property, ChangeOperationType.CREATE, null, value));
            diff.add(new ChangeDiffEntry(property, DiffKind.ADDED, null, display(value)));
        });
        return new PlannedClientLifecycleChange(
                Map.of("exists", false),
                Collections.unmodifiableMap(new LinkedHashMap<>(desired)),
                List.copyOf(operations),
                List.copyOf(diff));
    }

    public PlannedClientLifecycleChange planEnabled(
            ClientRepresentation current, ClientEnabledChangeRequest request) {
        Objects.requireNonNull(current, "current");
        if (request == null) {
            throw McpException.invalidArgument("client enabled change request must not be null");
        }
        boolean before = Boolean.TRUE.equals(current.isEnabled());
        if (before == request.enabled()) {
            throw McpException.invalidArgument("No effective client enabled-state change detected");
        }
        ChangeOperation operation = new ChangeOperation(
                ENABLED, ChangeOperationType.UPDATE, before, request.enabled());
        ChangeDiffEntry diff = new ChangeDiffEntry(
                ENABLED, DiffKind.CHANGED, String.valueOf(before), String.valueOf(request.enabled()));
        return new PlannedClientLifecycleChange(
                Map.of(ENABLED, before),
                Map.of(ENABLED, request.enabled()),
                List.of(operation),
                List.of(diff));
    }

    public boolean supportsEnabledUpdate(List<ChangeOperation> operations) {
        return operations != null
                && operations.size() == 1
                && ENABLED.equals(operations.get(0).property())
                && operations.get(0).operationType() == ChangeOperationType.UPDATE;
    }

    public boolean denyCreateInProduction(List<ChangeOperation> operations) {
        return operations != null && operations.stream().anyMatch(operation ->
                "directAccessGrantsEnabled".equals(operation.property())
                        && Boolean.TRUE.equals(operation.after()));
    }

    public Map<String, Object> extractEnabledBaseline(ClientRepresentation current) {
        return Map.of(ENABLED, Boolean.TRUE.equals(current.isEnabled()));
    }

    public void applyEnabled(ClientRepresentation current, List<ChangeOperation> operations) {
        if (!supportsEnabledUpdate(operations)) {
            throw McpException.writeNotSupported("Unsupported client lifecycle update");
        }
        current.setEnabled(asBoolean(operations.get(0).after(), ENABLED));
    }

    public ClientRepresentation toCreateRepresentation(Map<String, Object> desired) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(required(asString(desired.get("clientId")), "clientId"));
        client.setName(asString(desired.get("name")));
        client.setDescription(asString(desired.get("description")));
        client.setProtocol("openid-connect");
        client.setEnabled(asBoolean(desired.get(ENABLED), ENABLED));
        client.setPublicClient(asBoolean(desired.get("publicClient"), "publicClient"));
        client.setStandardFlowEnabled(asBoolean(desired.get("standardFlowEnabled"), "standardFlowEnabled"));
        client.setImplicitFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(
                asBoolean(desired.get("directAccessGrantsEnabled"), "directAccessGrantsEnabled"));
        client.setServiceAccountsEnabled(
                asBoolean(desired.get("serviceAccountsEnabled"), "serviceAccountsEnabled"));
        client.setSecret(null);
        return client;
    }

    public List<ChangeDiffEntry> compareCreateDesired(
            ClientRepresentation actual, Map<String, Object> desired) {
        Map<String, Object> observed = createState(actual);
        List<ChangeDiffEntry> mismatches = new ArrayList<>();
        desired.forEach((property, expected) -> {
            Object value = observed.get(property);
            if (!Objects.equals(expected, value)) {
                mismatches.add(new ChangeDiffEntry(
                        property, DiffKind.CHANGED, display(value), display(expected)));
            }
        });
        return mismatches;
    }

    public List<ChangeDiffEntry> compareEnabledDesired(
            ClientRepresentation actual, Map<String, Object> desired) {
        boolean observed = Boolean.TRUE.equals(actual.isEnabled());
        boolean expected = asBoolean(desired.get(ENABLED), ENABLED);
        return observed == expected
                ? List.of()
                : List.of(new ChangeDiffEntry(
                        ENABLED, DiffKind.CHANGED, String.valueOf(observed), String.valueOf(expected)));
    }

    private static Map<String, Object> createState(ClientRepresentation client) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("clientId", client.getClientId());
        state.put("name", client.getName());
        state.put("description", client.getDescription());
        state.put(ENABLED, Boolean.TRUE.equals(client.isEnabled()));
        state.put("publicClient", Boolean.TRUE.equals(client.isPublicClient()));
        state.put("standardFlowEnabled", Boolean.TRUE.equals(client.isStandardFlowEnabled()));
        state.put("directAccessGrantsEnabled", Boolean.TRUE.equals(client.isDirectAccessGrantsEnabled()));
        state.put("serviceAccountsEnabled", Boolean.TRUE.equals(client.isServiceAccountsEnabled()));
        return state;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw McpException.invalidArgument(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw McpException.invalidArgument(field + " is invalid or exceeds " + maxLength + " characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean asBoolean(Object value, String field) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.parseBoolean(text);
        }
        throw McpException.invalidArgument(field + " must be a Boolean");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String display(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record PlannedClientLifecycleChange(
            Map<String, Object> baselineState,
            Map<String, Object> desiredState,
            List<ChangeOperation> operations,
            List<ChangeDiffEntry> diff) {
    }
}
