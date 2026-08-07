package io.github.keycloakmcp.domain.platform;

import java.time.Instant;
import java.util.Map;

/** Environment snapshot including sanitized summary payload for UI. */
public record SnapshotDetail(
        String id,
        String targetId,
        String snapshotHash,
        Instant createdAt,
        Map<String, Object> summary) {
}
