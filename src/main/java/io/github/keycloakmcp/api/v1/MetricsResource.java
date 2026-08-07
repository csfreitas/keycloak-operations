package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.observability.metrics.MetricsResult;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.MetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Semantic metrics endpoints. Does not accept PromQL from clients.
 */
@Path("/api/v1/targets/{targetId}/metrics")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsResource {

    @Inject
    MetricsService metricsService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    @Path("/requests")
    public MetricsResult requests(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(metricsService.requests(targetId));
    }

    @GET
    @Path("/latency")
    public MetricsResult latency(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(metricsService.latency(targetId));
    }

    @GET
    @Path("/jvm")
    public MetricsResult jvm(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(metricsService.jvm(targetId));
    }

    @GET
    @Path("/database-pool")
    public MetricsResult databasePool(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(metricsService.databasePool(targetId));
    }

    @GET
    @Path("/resources")
    public MetricsResult resources(@PathParam("targetId") String targetId) {
        return sensitiveDataFilter.redact(metricsService.resources(targetId));
    }
}
