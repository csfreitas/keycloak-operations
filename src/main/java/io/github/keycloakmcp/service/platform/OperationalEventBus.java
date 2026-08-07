package io.github.keycloakmcp.service.platform;

import java.time.Duration;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-process fan-out for operational SSE events (assessment/health/target status).
 */
@ApplicationScoped
public class OperationalEventBus {

    private static final Logger LOG = Logger.getLogger(OperationalEventBus.class);

    private final BroadcastProcessor<OperationalEvent> processor = BroadcastProcessor.create();

    public void publish(OperationalEvent event) {
        if (event == null) {
            return;
        }
        try {
            processor.onNext(event);
        } catch (RuntimeException e) {
            LOG.debugf(e, "Failed to publish operational event type=%s", event.type());
        }
    }

    public Multi<OperationalEvent> events() {
        Multi<OperationalEvent> heartbeats = Multi.createFrom().ticks().every(Duration.ofSeconds(15))
                .onOverflow().drop()
                .map(tick -> OperationalEvent.of("heartbeat", null, "heartbeat", null));
        return Multi.createBy().merging().streams(processor.toHotStream(), heartbeats);
    }
}
