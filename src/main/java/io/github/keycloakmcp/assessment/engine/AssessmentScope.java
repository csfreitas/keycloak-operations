package io.github.keycloakmcp.assessment.engine;

/**
 * Explicit assessment scope. Milestone 0.5 primarily runs TARGET-scoped assessments.
 */
public record AssessmentScope(
        AssessmentScopeType type,
        String targetId,
        String realm,
        String clientId) {

    public static AssessmentScope target(String targetId) {
        return new AssessmentScope(AssessmentScopeType.TARGET, targetId, null, null);
    }

    public static AssessmentScope realm(String targetId, String realm) {
        return new AssessmentScope(AssessmentScopeType.REALM, targetId, realm, null);
    }

    public static AssessmentScope client(String targetId, String realm, String clientId) {
        return new AssessmentScope(AssessmentScopeType.CLIENT, targetId, realm, clientId);
    }
}
