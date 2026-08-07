package io.github.keycloakmcp.service.platform;

/**
 * Scheduled platform operations. Implementations must use distributed locking in HA.
 */
public interface ScheduledOperationService {

    void runScheduledHealthChecks();

    void runScheduledSnapshots();
}
