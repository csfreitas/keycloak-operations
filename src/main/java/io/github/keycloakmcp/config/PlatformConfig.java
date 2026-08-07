package io.github.keycloakmcp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Operations platform settings (persistence, audit, retention).
 * <p>
 * Retention defaults are documentation-oriented; a future job will enforce them.
 * PostgreSQL stores operational state — it is not a time-series database.
 */
@ConfigMapping(prefix = "platform")
public interface PlatformConfig {

    /**
     * Target registry mode: {@code configuration}, {@code database}, or {@code composite} (default).
     */
    @WithName("target-registry")
    @WithDefault("composite")
    String targetRegistry();

    Audit audit();

    Retention retention();

    interface Audit {
        @WithDefault("true")
        boolean enabled();

        /** METADATA | SANITIZED | FULL */
        @WithDefault("SANITIZED")
        String mode();
    }

    interface Retention {
        /** Days to retain assessment runs/findings (default 90). */
        @WithName("assessment-days")
        @WithDefault("90")
        int assessmentDays();

        /** Days to retain health check history (default 30). */
        @WithName("health-check-days")
        @WithDefault("30")
        int healthCheckDays();

        /** Days to retain audit events (default 180). */
        @WithName("audit-days")
        @WithDefault("180")
        int auditDays();

        /** Days to retain environment snapshots (default 60). */
        @WithName("snapshot-days")
        @WithDefault("60")
        int snapshotDays();
    }
}
