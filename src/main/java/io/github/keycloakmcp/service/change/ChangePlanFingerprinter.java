package io.github.keycloakmcp.service.change;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.github.keycloakmcp.domain.change.ChangeDiffEntry;
import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChangePlanFingerprinter {

    public String fingerprintPlan(
            String targetId,
            String realm,
            String resourceType,
            String resourceId,
            String operation,
            List<ChangeOperation> operations) {
        if (operations.stream().allMatch(ChangePlanFingerprinter::hasLegacyScalarValues)) {
            return fingerprintLegacyPlan(targetId, realm, resourceType, resourceId, operation, operations);
        }
        String canonical = "target=" + nullToEmpty(targetId)
                + "|realm=" + nullToEmpty(realm)
                + "|type=" + nullToEmpty(resourceType)
                + "|resource=" + nullToEmpty(resourceId)
                + "|op=" + nullToEmpty(operation)
                + "|ops=v2:" + operations.stream()
                        .sorted((a, b) -> a.property().compareTo(b.property()))
                        .map(o -> canonical(o.property()) + "=" + canonical(o.before()) + "->" + canonical(o.after()))
                        .collect(Collectors.joining(";"));
        return sha256(canonical);
    }

    public String fingerprintBaseline(Map<String, Object> baselineState) {
        Map<String, Object> sorted = new TreeMap<>();
        if (baselineState != null) {
            baselineState.forEach(sorted::put);
        }
        if (sorted.values().stream().allMatch(ChangePlanFingerprinter::isLegacyScalar)) {
            String legacyCanonical = sorted.entrySet().stream()
                    .map(e -> e.getKey() + "=" + legacyString(e.getValue()))
                    .collect(Collectors.joining("|"));
            return sha256(legacyCanonical);
        }
        String canonical = sorted.entrySet().stream()
                .map(e -> canonical(e.getKey()) + "=" + canonical(e.getValue()))
                .collect(Collectors.joining("|"));
        return sha256("v2:" + canonical);
    }

    public String fingerprintDiff(List<ChangeDiffEntry> diff) {
        String canonical = diff.stream()
                .sorted((a, b) -> a.property().compareTo(b.property()))
                .map(d -> d.property() + ":" + d.kind() + ":" + nullToEmpty(d.before()) + "->" + nullToEmpty(d.after()))
                .collect(Collectors.joining("|"));
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw McpException.internal("SHA-256 not available", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String fingerprintLegacyPlan(
            String targetId,
            String realm,
            String resourceType,
            String resourceId,
            String operation,
            List<ChangeOperation> operations) {
        String canonical = "target=" + nullToEmpty(targetId)
                + "|realm=" + nullToEmpty(realm)
                + "|type=" + nullToEmpty(resourceType)
                + "|resource=" + nullToEmpty(resourceId)
                + "|op=" + nullToEmpty(operation)
                + "|ops=" + operations.stream()
                        .sorted((a, b) -> a.property().compareTo(b.property()))
                        .map(o -> o.property() + "=" + legacyString(o.before()) + "->" + legacyString(o.after()))
                        .collect(Collectors.joining(";"));
        return sha256(canonical);
    }

    private static boolean hasLegacyScalarValues(ChangeOperation operation) {
        return isLegacyScalar(operation.before()) && isLegacyScalar(operation.after());
    }

    private static boolean isLegacyScalar(Object value) {
        return value == null || value instanceof String;
    }

    private static String legacyString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return "s:" + string.length() + ":" + string;
        }
        if (value instanceof Number number) {
            return "n:" + number;
        }
        if (value instanceof Boolean bool) {
            return "b:" + bool;
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), nested));
            return "m:{" + sorted.entrySet().stream()
                    .map(entry -> canonical(entry.getKey()) + "=" + canonical(entry.getValue()))
                    .collect(Collectors.joining(",")) + "}";
        }
        if (value instanceof Collection<?> collection) {
            return "c:[" + collection.stream().map(ChangePlanFingerprinter::canonical)
                    .collect(Collectors.joining(",")) + "]";
        }
        if (value.getClass().isArray()) {
            StringBuilder out = new StringBuilder("a:[");
            for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(canonical(Array.get(value, i)));
            }
            return out.append(']').toString();
        }
        return canonical(String.valueOf(value));
    }
}
