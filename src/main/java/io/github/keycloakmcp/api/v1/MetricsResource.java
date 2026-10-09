package io.github.keycloakmcp.api.v1;

import java.util.List;
import java.util.Locale;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.metrics.MetricsStatusView;
import io.github.keycloakmcp.domain.metrics.PerformanceSummary;
import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.observability.metrics.SemanticMetricResult;
import io.github.keycloakmcp.security.ReadMetadataProjection;
import io.github.keycloakmcp.service.platform.MetricsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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
    ReadMetadataProjection readProjection;

    @GET
    @Path("/status")
    public MetricsStatusView status(@PathParam("targetId") String targetId) {
        return readProjection.project(metricsService.status(targetId), "targetId");
    }

    @GET
    @Path("/summary")
    public PerformanceSummary summary(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return readProjection.project(metricsService.summary(targetId, window), "targetId");
    }

    @GET
    @Path("/http")
    public List<SemanticMetricResult> http(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.HTTP, window);
    }

    @GET
    @Path("/database")
    public List<SemanticMetricResult> database(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.DATABASE, window);
    }

    @GET
    @Path("/jvm")
    public List<SemanticMetricResult> jvm(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.JVM, window);
    }

    @GET
    @Path("/cache")
    public List<SemanticMetricResult> cache(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.CACHE, window);
    }

    @GET
    @Path("/authentication")
    public List<SemanticMetricResult> authentication(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.AUTHENTICATION, window);
    }

    @GET
    @Path("/runtime")
    public List<SemanticMetricResult> runtime(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.RUNTIME, window);
    }

    @GET
    @Path("/cluster")
    public List<SemanticMetricResult> cluster(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return category(targetId, MetricCategory.CLUSTER, window);
    }

    /** Legacy alias for HTTP request-rate oriented views. */
    @GET
    @Path("/requests")
    public List<SemanticMetricResult> requests(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return http(targetId, window);
    }

    /** Legacy alias — latency lives under HTTP category. */
    @GET
    @Path("/latency")
    public List<SemanticMetricResult> latency(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return http(targetId, window);
    }

    /** Legacy alias for database pool metrics. */
    @GET
    @Path("/database-pool")
    public List<SemanticMetricResult> databasePool(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return database(targetId, window);
    }

    /** Legacy alias for runtime/resources. */
    @GET
    @Path("/resources")
    public List<SemanticMetricResult> resources(
            @PathParam("targetId") String targetId, @QueryParam("window") String window) {
        return runtime(targetId, window);
    }

    @GET
    @Path("/category/{category}")
    public List<SemanticMetricResult> categoryPath(
            @PathParam("targetId") String targetId,
            @PathParam("category") String category,
            @QueryParam("window") String window) {
        return category(targetId, parseCategory(category), window);
    }

    private List<SemanticMetricResult> category(String targetId, MetricCategory category, String window) {
        return metricsService.category(targetId, category, window).stream()
                .map(result -> readProjection.project(result, "targetId")).toList();
    }

    private static MetricCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw McpException.invalidArgument("metrics category is required");
        }
        try {
            return MetricCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw McpException.invalidArgument("Unsupported metrics category: " + raw);
        }
    }
}
