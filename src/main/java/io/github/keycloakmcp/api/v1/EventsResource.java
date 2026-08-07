package io.github.keycloakmcp.api.v1;

import java.time.Duration;

import org.jboss.resteasy.reactive.RestStreamElementType;

import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Minimal SSE endpoint for UI event streaming (hello + heartbeat comments).
 */
@Path("/api/v1/events")
public class EventsResource {

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> events() {
        return Multi.createBy().concatenating().streams(
                Multi.createFrom().item("hello"),
                Multi.createFrom().ticks().every(Duration.ofSeconds(15))
                        .onOverflow().drop()
                        .map(tick -> "heartbeat"));
    }
}
