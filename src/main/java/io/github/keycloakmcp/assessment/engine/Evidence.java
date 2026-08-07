package io.github.keycloakmcp.assessment.engine;

import java.time.Instant;

public record Evidence(
        String targetId,
        String source,
        String category,
        String key,
        Object value,
        Instant collectedAt,
        EvidenceSubject subject) {

    public Evidence(
            String targetId,
            String source,
            String category,
            String key,
            Object value,
            Instant collectedAt) {
        this(targetId, source, category, key, value, collectedAt, null);
    }

    public Evidence withSubject(EvidenceSubject subject) {
        return new Evidence(targetId, source, category, key, value, collectedAt, subject);
    }
}
