package io.github.keycloakmcp.api.v1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import io.github.keycloakmcp.domain.platform.OperationalEvent;
import org.junit.jupiter.api.Test;

class EventsAuthorizationTest {
    @Test
    void forwardsOnlyReadableTargetEventsAndSafeHeartbeats() {
        Set<String> readable = Set.of("target-a");
        assertThat(EventsResource.isVisible(OperationalEvent.of("assessment", "target-a", "done", "a1"), readable)).isTrue();
        assertThat(EventsResource.isVisible(OperationalEvent.of("assessment", "target-b", "done", "b1"), readable)).isFalse();
        assertThat(EventsResource.isVisible(OperationalEvent.of("audit", null, "global-data", "g1"), readable)).isFalse();
        assertThat(EventsResource.isVisible(OperationalEvent.of("heartbeat", null, "heartbeat", null), readable)).isTrue();
        assertThat(EventsResource.isVisible(OperationalEvent.of("heartbeat", null, "secret", null), readable)).isFalse();
    }
}
