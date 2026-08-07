package io.github.keycloakmcp.domain.platform;

import java.time.Instant;

public record SnapshotSummary(
        String id,
        String targetId,
        String snapshotHash,
        Instant createdAt) {
}
