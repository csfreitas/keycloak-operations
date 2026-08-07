package io.github.keycloakmcp.service.change;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
        String canonical = "target=" + nullToEmpty(targetId)
                + "|realm=" + nullToEmpty(realm)
                + "|type=" + nullToEmpty(resourceType)
                + "|resource=" + nullToEmpty(resourceId)
                + "|op=" + nullToEmpty(operation)
                + "|ops=" + operations.stream()
                        .sorted((a, b) -> a.property().compareTo(b.property()))
                        .map(o -> o.property() + "=" + nullToEmpty(o.before()) + "->" + nullToEmpty(o.after()))
                        .collect(Collectors.joining(";"));
        return sha256(canonical);
    }

    public String fingerprintBaseline(Map<String, Object> baselineState) {
        Map<String, Object> sorted = new TreeMap<>();
        if (baselineState != null) {
            baselineState.forEach((k, v) -> sorted.put(k, v == null ? "" : String.valueOf(v)));
        }
        String canonical = sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
        return sha256(canonical);
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
}
