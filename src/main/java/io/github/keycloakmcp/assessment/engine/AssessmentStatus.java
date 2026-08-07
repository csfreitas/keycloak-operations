package io.github.keycloakmcp.assessment.engine;

/**
 * Overall assessment execution status (distinct from finding severity/status).
 */
public enum AssessmentStatus {
    /** All essential evidence sources were collected. */
    COMPLETE,
    /** Some optional or permission-gated evidence was unavailable. */
    PARTIAL,
    /** Assessment could not be performed meaningfully. */
    FAILED
}
