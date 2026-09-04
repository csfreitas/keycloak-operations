package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.domain.platform.TriggerType;
import io.github.keycloakmcp.domain.report.OperationsReport;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.OperationsReportService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Generates a sanitized point-in-time operations report for a registered target. */
@Path("/api/v1/targets/{targetId}/operations-reports")
@Produces(MediaType.APPLICATION_JSON)
public class OperationsReportResource {

    @Inject
    OperationsReportService operationsReportService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @POST
    public OperationsReport generate(
            @PathParam("targetId") String targetId,
            @QueryParam("profile") String profile,
            @QueryParam("metricsWindow") String metricsWindow) {
        return sensitiveDataFilter.redact(
                operationsReportService.generate(targetId, profile, metricsWindow, TriggerType.API));
    }
}
