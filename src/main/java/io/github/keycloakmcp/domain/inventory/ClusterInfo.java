package io.github.keycloakmcp.domain.inventory;

/**
 * Cluster-level metadata collected from the Kubernetes/OpenShift API.
 */
public record ClusterInfo(
        /** Distribution: "openshift", "kubernetes", or null if undetermined. */
        String distribution,
        /** Kubernetes/OpenShift server version (e.g. v1.29.0). */
        String version,
        /** Cloud/infra platform (e.g. AWS, GCP, Azure, BareMetal, None). Null if undetermined. */
        String platform,
        /** Total node count; -1 if collection failed. */
        int nodeCount,
        /** Distinct availability zone count; -1 if labels not present. */
        int zoneCount) {
}
