package io.github.keycloakmcp.domain.report;

import java.time.Instant;

/** Describes the limits of report reproducibility without claiming an atomic snapshot. */
public record ReportProvenance(
        Instant collectionStartedAt,
        Instant collectionCompletedAt,
        String collectionMode,
        String bundledRuleCatalogSha256,
        boolean retainedEvidenceReplayAvailable) {
}
