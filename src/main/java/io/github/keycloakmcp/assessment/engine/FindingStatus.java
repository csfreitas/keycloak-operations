package io.github.keycloakmcp.assessment.engine;

public enum FindingStatus {
    OPEN,
    PASS,
    WARNING,
    FAIL,
    /** Rule could not be evaluated because required evidence is missing. Does not affect score. */
    NOT_EVALUATED,
    /** Rule intentionally skipped (appliesWhen mismatch). */
    SKIPPED
}
