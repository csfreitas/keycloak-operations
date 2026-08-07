package io.github.keycloakmcp.api.v1;

import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.platform.OperationalEvent;
import io.github.keycloakmcp.service.platform.OperationalEventBus;
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

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> events() {
        return Multi.createBy().concatenating().streams(
                Multi.createFrom().item(toJson(OperationalEvent.of("hello", null, "connected", null))),
                eventBus.events().map(this::toJson));
    }

    private String toJson(OperationalEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"serialization_failed\"}";
        }
    }
}
