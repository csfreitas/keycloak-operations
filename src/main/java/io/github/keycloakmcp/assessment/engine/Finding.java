package io.github.keycloakmcp.assessment.engine;

import java.util.List;
import java.util.Map;

public record Finding(
        String targetId,
        String id,
        String title,
        String category,
        Severity severity,
        FindingStatus status,
        String description,
        Map<String, Object> evidence,
        String impact,
        String recommendation,
        List<String> references,
        EvidenceSubject subject) {

    public Finding(
            String targetId,
            String id,
            String title,
            String category,
            Severity severity,
            FindingStatus status,
            String description,
            Map<String, Object> evidence,
            String impact,
            String recommendation,
            List<String> references) {
        this(
                targetId,
                id,
                title,
                category,
                severity,
                status,
                description,
                evidence,
                impact,
                recommendation,
                references,
                null);
    }

    public Finding withSubject(EvidenceSubject subject) {
        return new Finding(
                targetId,
                id,
                title,
                category,
                severity,
                status,
                description,
                evidence,
                impact,
                recommendation,
                references,
                subject);
    }

    public Finding withTargetId(String targetId) {
        return new Finding(
                targetId,
                id,
                title,
                category,
                severity,
                status,
                description,
                evidence,
                impact,
                recommendation,
                references,
                subject);
    }
}
