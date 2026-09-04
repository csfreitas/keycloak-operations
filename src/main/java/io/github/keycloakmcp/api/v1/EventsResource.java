package io.github.keycloakmcp.api.v1;

import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.service.platform.OperationalEventBus;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetRegistry;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * SSE endpoint for bounded operational UI events (assessment/health/heartbeat).
 * Does not stream raw Prometheus samples.
 */
@Path("/api/v1/events")
public class EventsResource {

    @Inject
    OperationalEventBus eventBus;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TargetAuthorizationService authorization;

    @Inject
    TargetRegistry targetRegistry;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> events() {
        authorization.assertSession();
        // Resolve the grant snapshot in the request context, never from a later event publisher's identity.
        var readableTargets = targetRegistry.list().stream()
                .filter(t -> authorization.isAllowed(t, TargetPermission.READ))
                .map(t -> t.id().value()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return Multi.createBy().concatenating().streams(
                Multi.createFrom().item(toJson(OperationalEvent.of("hello", null, "connected", null))),
                eventBus.events()
                        .filter(event -> isVisible(event, readableTargets))
                        // Reconnect periodically to authenticate again and refresh grants.
                        .select().first(java.time.Duration.ofMinutes(5))
                        .map(this::toJson));
    }

    static boolean isVisible(OperationalEvent event, java.util.Set<String> readableTargets) {
        return event.targetId() != null ? readableTargets.contains(event.targetId())
                : "heartbeat".equals(event.type()) && "heartbeat".equals(event.message())
                        && event.relatedId() == null;
    }

    private String toJson(OperationalEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"serialization_failed\"}";
        }
    }
}
