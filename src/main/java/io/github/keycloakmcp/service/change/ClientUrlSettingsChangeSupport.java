package io.github.keycloakmcp.service.change;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ChangeDiffEntry;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;

/** Deterministic validation, normalization, diff, apply, and verification for client URL sets. */
@ApplicationScoped
public class ClientUrlSettingsChangeSupport {

    public static final String REDIRECT_URIS = "redirectUris";
    public static final String WEB_ORIGINS = "webOrigins";
    public static final Set<String> SUPPORTED_PROPERTIES = Set.of(REDIRECT_URIS, WEB_ORIGINS);
    public static final int MAX_ENTRIES_PER_FIELD = 100;
    public static final int MAX_ENTRY_LENGTH = 2048;
    public static final int MAX_TOTAL_LENGTH = 32_768;

    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern PARENT_DIRECTORY = Pattern.compile(
            "(?i)(^|/)(?:\\.\\.|%2e%2e|\\.%2e|%2e\\.)(?:/|$)");

    /** Normalize request intent without consulting live state, including idempotent retries. */
    public Map<String, Object> desiredState(ClientUrlChangeRequest request) {
        if (request == null || (request.redirectUris() == null && request.webOrigins() == null)) {
            throw McpException.invalidArgument("at least one of redirectUris or webOrigins must be provided");
        }
        validateRequestTotalLength(request);
        Map<String, Object> desired = new LinkedHashMap<>();
        if (request.redirectUris() != null) {
            desired.put(REDIRECT_URIS, normalizeDesired(request.redirectUris(), UrlKind.REDIRECT_URI));
        }
        if (request.webOrigins() != null) {
            desired.put(WEB_ORIGINS, normalizeDesired(request.webOrigins(), UrlKind.WEB_ORIGIN));
        }
        return immutableState(desired);
    }

    public PlannedClientUrlChange plan(ClientRepresentation current, ClientUrlChangeRequest request) {
        Objects.requireNonNull(current, "current");
        if (request == null) {
            throw McpException.invalidArgument("client URL change request must not be null");
        }
        if (request.redirectUris() == null && request.webOrigins() == null) {
            throw McpException.invalidArgument("at least one of redirectUris or webOrigins must be provided");
        }
        validateRequestTotalLength(request);

        Map<String, Object> baseline = new LinkedHashMap<>();
        Map<String, Object> desired = new LinkedHashMap<>();
        List<ChangeOperation> operations = new ArrayList<>();
        List<ChangeDiffEntry> diff = new ArrayList<>();

        if (request.redirectUris() != null) {
            planProperty(
                    REDIRECT_URIS,
                    normalizeObserved(current.getRedirectUris()),
                    normalizeDesired(request.redirectUris(), UrlKind.REDIRECT_URI),
                    baseline,
                    desired,
                    operations,
                    diff);
        }
        if (request.webOrigins() != null) {
            planProperty(
                    WEB_ORIGINS,
                    normalizeObserved(current.getWebOrigins()),
                    normalizeDesired(request.webOrigins(), UrlKind.WEB_ORIGIN),
                    baseline,
                    desired,
                    operations,
                    diff);
        }

        if (operations.isEmpty()) {
            throw McpException.invalidArgument("No effective client URL changes detected");
        }
        return new PlannedClientUrlChange(
                immutableState(baseline),
                immutableState(desired),
                List.copyOf(operations),
                List.copyOf(diff));
    }

    public Map<String, Object> extractBaseline(ClientRepresentation representation, Collection<String> properties) {
        Objects.requireNonNull(representation, "representation");
        Map<String, Object> baseline = new LinkedHashMap<>();
        if (properties.contains(REDIRECT_URIS)) {
            baseline.put(REDIRECT_URIS, normalizeObserved(representation.getRedirectUris()));
        }
        if (properties.contains(WEB_ORIGINS)) {
            baseline.put(WEB_ORIGINS, normalizeObserved(representation.getWebOrigins()));
        }
        return immutableState(baseline);
    }

    public void applyToRepresentation(ClientRepresentation representation, List<ChangeOperation> operations) {
        Objects.requireNonNull(representation, "representation");
        for (ChangeOperation operation : operations) {
            List<String> desired = asStringList(operation.after(), operation.property());
            switch (operation.property()) {
                case REDIRECT_URIS -> representation.setRedirectUris(new ArrayList<>(desired));
                case WEB_ORIGINS -> representation.setWebOrigins(new ArrayList<>(desired));
                default -> throw McpException.writeNotSupported(
                        "Unsupported client URL property: " + operation.property());
            }
        }
    }

    public List<ChangeDiffEntry> compareDesired(
            ClientRepresentation actual,
            Map<String, Object> desiredState) {
        List<ChangeDiffEntry> mismatches = new ArrayList<>();
        for (Map.Entry<String, Object> entry : desiredState.entrySet()) {
            String property = entry.getKey();
            List<String> expected = asStringList(entry.getValue(), property);
            List<String> observed = switch (property) {
                case REDIRECT_URIS -> normalizeObserved(actual.getRedirectUris());
                case WEB_ORIGINS -> normalizeObserved(actual.getWebOrigins());
                default -> throw McpException.writeNotSupported(
                        "Unsupported client URL verification property: " + property);
            };
            addSetDiff(property, observed, expected, mismatches);
        }
        return List.copyOf(mismatches);
    }

    public boolean supports(List<ChangeOperation> operations) {
        return operations != null
                && !operations.isEmpty()
                && operations.stream().allMatch(operation -> SUPPORTED_PROPERTIES.contains(operation.property()));
    }

    public boolean denyInProduction(List<ChangeOperation> operations) {
        for (ChangeOperation operation : operations) {
            Set<String> before = new LinkedHashSet<>(asStringList(operation.before(), operation.property()));
            for (String added : asStringList(operation.after(), operation.property())) {
                if (!before.contains(added) && isProductionDeniedValue(operation.property(), added)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isLoopbackHttp(String value) {
        try {
            URI uri = new URI(stripPathWildcard(value));
            if (!"http".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            return "localhost".equals(normalized)
                    || "::1".equals(normalized)
                    || normalized.startsWith("127.");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static boolean containsPathWildcard(String value) {
        return value != null && value.endsWith("/*");
    }

    private void planProperty(
            String property,
            List<String> before,
            List<String> after,
            Map<String, Object> baseline,
            Map<String, Object> desired,
            List<ChangeOperation> operations,
            List<ChangeDiffEntry> diff) {
        baseline.put(property, before);
        desired.put(property, after);
        if (before.equals(after)) {
            return;
        }
        operations.add(new ChangeOperation(property, ChangeOperationType.UPDATE, before, after));
        addSetDiff(property, before, after, diff);
    }

    private static void addSetDiff(
            String property,
            List<String> before,
            List<String> after,
            List<ChangeDiffEntry> diff) {
        Set<String> beforeSet = new TreeSet<>(before);
        Set<String> afterSet = new TreeSet<>(after);
        beforeSet.stream()
                .filter(value -> !afterSet.contains(value))
                .forEach(value -> diff.add(new ChangeDiffEntry(property, DiffKind.REMOVED, value, null)));
        afterSet.stream()
                .filter(value -> !beforeSet.contains(value))
                .forEach(value -> diff.add(new ChangeDiffEntry(property, DiffKind.ADDED, null, value)));
    }

    private static List<String> normalizeDesired(List<String> values, UrlKind kind) {
        if (values.size() > MAX_ENTRIES_PER_FIELD) {
            throw McpException.invalidArgument(
                    kind.label + " exceeds maximum entries: " + MAX_ENTRIES_PER_FIELD);
        }
        int totalLength = 0;
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            validateCommon(value, kind);
            if (kind == UrlKind.REDIRECT_URI) {
                validateRedirectUri(value);
            } else {
                validateWebOrigin(value);
            }
            totalLength += value.length();
            if (totalLength > MAX_TOTAL_LENGTH) {
                throw McpException.invalidArgument(
                        kind.label + " exceeds maximum total length: " + MAX_TOTAL_LENGTH);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeObserved(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private static void validateCommon(String value, UrlKind kind) {
        if (value == null || value.isBlank()) {
            throw McpException.invalidArgument(kind.label + " entries must not be null or blank");
        }
        if (!value.equals(value.trim())) {
            throw McpException.invalidArgument(kind.label + " entries must not contain surrounding whitespace");
        }
        if (value.length() > MAX_ENTRY_LENGTH) {
            throw McpException.invalidArgument(
                    kind.label + " entry exceeds maximum length: " + MAX_ENTRY_LENGTH);
        }
        if (CONTROL_CHARACTER.matcher(value).find()) {
            throw McpException.invalidArgument(kind.label + " entries must not contain control characters");
        }
        if ("*".equals(value)) {
            throw McpException.invalidArgument("Full wildcard is not allowed for " + kind.label);
        }
    }

    private static void validateRedirectUri(String value) {
        boolean pathWildcard = containsPathWildcard(value);
        if (value.indexOf('*') >= 0 && !pathWildcard) {
            throw McpException.invalidArgument("Redirect URI wildcard is allowed only as a trailing path /*");
        }
        URI uri = parseUri(stripPathWildcard(value), "redirect URI");
        validateAbsoluteHttpUri(uri, "redirect URI");
        if (uri.getRawFragment() != null) {
            throw McpException.invalidArgument("Redirect URI must not contain a fragment");
        }
        rejectUnsafeParts(uri, "Redirect URI");
    }

    private static void validateWebOrigin(String value) {
        if ("+".equals(value)) {
            return;
        }
        if (value.indexOf('*') >= 0) {
            throw McpException.invalidArgument("Web Origin wildcards are not supported by this operation");
        }
        URI uri = parseUri(value, "Web Origin");
        validateAbsoluteHttpUri(uri, "Web Origin");
        rejectUnsafeParts(uri, "Web Origin");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw McpException.invalidArgument("Web Origin must not contain query or fragment components");
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            throw McpException.invalidArgument("Web Origin must not contain a non-root path");
        }
    }

    private static URI parseUri(String value, String label) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw McpException.invalidArgument("Malformed " + label + ": " + value);
        }
    }

    private static void validateAbsoluteHttpUri(URI uri, String label) {
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || scheme == null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            throw McpException.invalidArgument(label + " must be an absolute HTTP(S) URI");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw McpException.invalidArgument(label + " must contain a valid host");
        }
    }

    private static void rejectUnsafeParts(URI uri, String label) {
        if (uri.getRawUserInfo() != null) {
            throw McpException.invalidArgument(label + " must not contain user-information");
        }
        String rawPath = uri.getRawPath();
        if (rawPath != null
                && (PARENT_DIRECTORY.matcher(rawPath).find()
                        || rawPath.toLowerCase(Locale.ROOT).contains("%2e"))) {
            throw McpException.invalidArgument(label + " must not contain parent-directory traversal");
        }
    }

    private static void validateRequestTotalLength(ClientUrlChangeRequest request) {
        int total = totalLength(request.redirectUris()) + totalLength(request.webOrigins());
        if (total > MAX_TOTAL_LENGTH) {
            throw McpException.invalidArgument(
                    "client URL request exceeds maximum total length: " + MAX_TOTAL_LENGTH);
        }
    }

    private static int totalLength(List<String> values) {
        if (values == null) {
            return 0;
        }
        long total = values.stream().filter(Objects::nonNull).mapToLong(String::length).sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static boolean isProductionDeniedValue(String property, String value) {
        if (WEB_ORIGINS.equals(property) && "+".equals(value)) {
            return true;
        }
        if (containsPathWildcard(value)) {
            return true;
        }
        try {
            URI uri = new URI(stripPathWildcard(value));
            return "http".equalsIgnoreCase(uri.getScheme()) && !isLoopbackHttp(value);
        } catch (URISyntaxException e) {
            return true;
        }
    }

    private static String stripPathWildcard(String value) {
        return containsPathWildcard(value) ? value.substring(0, value.length() - 1) : value;
    }

    private static List<String> asStringList(Object value, String property) {
        if (!(value instanceof Collection<?> collection)) {
            throw McpException.internal("Structured collection expected for client URL property: " + property);
        }
        List<String> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof String string)) {
                throw McpException.internal("String collection expected for client URL property: " + property);
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> immutableState(Map<String, Object> state) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    private enum UrlKind {
        REDIRECT_URI("redirectUris"),
        WEB_ORIGIN("webOrigins");

        private final String label;

        UrlKind(String label) {
            this.label = label;
        }
    }

    public record PlannedClientUrlChange(
            Map<String, Object> baselineState,
            Map<String, Object> desiredState,
            List<ChangeOperation> operations,
            List<ChangeDiffEntry> diff) {
    }
}
