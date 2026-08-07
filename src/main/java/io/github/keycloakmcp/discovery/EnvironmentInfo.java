package io.github.keycloakmcp.discovery;

import java.util.List;

/**
 * Result of an environment discovery probe.
 * <p>
 * The {@code targetId} field identifies the target that was probed (null for global/fallback probes).
 * {@code clusterVersion} and {@code clusterPlatform} are enriched when the cluster API is reachable.
 */
public record EnvironmentInfo(
        RuntimeType runtime,
        DetectionConfidence confidence,
        String platform,
        String namespace,
        List<String> evidence,
        String targetId,
        String clusterVersion,
        String clusterPlatform) {

    /** Compact constructor for backward-compatible callers (targetId, clusterVersion, clusterPlatform = null). */
    public EnvironmentInfo(
            RuntimeType runtime,
            DetectionConfidence confidence,
            String platform,
            String namespace,
            List<String> evidence) {
        this(runtime, confidence, platform, namespace, evidence, null, null, null);
    }
}
