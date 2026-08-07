package io.github.keycloakmcp.domain.platform;

/**
 * Lifecycle status of a persisted finding (distinct from assessment engine
 * {@link io.github.keycloakmcp.assessment.engine.FindingStatus}).
 */
public enum FindingLifecycleStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    ACCEPTED_RISK,
    SUPPRESSED
}
