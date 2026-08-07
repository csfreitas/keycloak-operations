package io.github.keycloakmcp.assessment.engine;

import java.time.Instant;

public record Evidence(
        String targetId,
        String source,
        String category,
        String key,
        Object value,
        Instant collectedAt) {
}
