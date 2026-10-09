package io.github.keycloakmcp.discovery;

import java.util.List;
import io.github.keycloakmcp.target.InfrastructureType;

/**
 * Result of an environment discovery probe.
 * <p>
 * The {@code targetId} field identifies the target that was probed (null for global/fallback probes).
 * {@code clusterVersion} and {@code clusterPlatform} are enriched when the cluster API is reachable.
 * {@code configuredType} is operator intent, not observed runtime. API advertisement
 * in {@code apiCapabilities} is independent of resource authorization and collection success.
 */
public record EnvironmentInfo(
        RuntimeType runtime,
        DetectionConfidence confidence,
        String platform,
        String namespace,
        List<String> evidence,
        String targetId,
        String clusterVersion,
        String clusterPlatform,
        InfrastructureType configuredType,
        ClusterApiCapabilities apiCapabilities) {

    public EnvironmentInfo {
        apiCapabilities = apiCapabilities == null ? ClusterApiCapabilities.unknown() : apiCapabilities;
    }

    /** Legacy callers did not retain an API-group observation; do not infer one from runtime. */
    public EnvironmentInfo(RuntimeType runtime, DetectionConfidence confidence, String platform, String namespace,
            List<String> evidence, String targetId, String clusterVersion, String clusterPlatform) {
        this(runtime, confidence, platform, namespace, evidence, targetId, clusterVersion, clusterPlatform,
                null, ClusterApiCapabilities.unknown());
    }

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
