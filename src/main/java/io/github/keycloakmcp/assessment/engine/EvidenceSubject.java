package io.github.keycloakmcp.assessment.engine;

/**
 * Identifies the resource an evidence item refers to (beyond the target).
 */
public record EvidenceSubject(
        SubjectType type,
        String id,
        String displayName) {

    public static EvidenceSubject target(String targetId) {
        return new EvidenceSubject(SubjectType.TARGET, targetId, targetId);
    }

    public static EvidenceSubject realm(String realm) {
        return new EvidenceSubject(SubjectType.REALM, realm, realm);
    }

    public static EvidenceSubject client(String clientId) {
        return new EvidenceSubject(SubjectType.CLIENT, clientId, clientId);
    }
}
