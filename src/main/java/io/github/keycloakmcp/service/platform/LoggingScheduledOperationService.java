package io.github.keycloakmcp.service.platform;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stub scheduler — logs only. Before enabling real work in HA, add leader election /
 * distributed locking so only one replica runs each job.
 */
@ApplicationScoped
public class LoggingScheduledOperationService implements ScheduledOperationService {

    private static final Logger LOG = Logger.getLogger(LoggingScheduledOperationService.class);

    @Override
    @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void runScheduledHealthChecks() {
        LOG.debug("Scheduled health checks stub — enable with HA locking before production use");
    }

    @Override
    @Scheduled(every = "6h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void runScheduledSnapshots() {
        LOG.debug("Scheduled snapshots stub — enable with HA locking before production use");
    }
}
