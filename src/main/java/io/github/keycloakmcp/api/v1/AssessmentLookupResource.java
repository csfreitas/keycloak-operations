package io.github.keycloakmcp.api.v1;

import io.github.keycloakmcp.domain.platform.AssessmentRunSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.AssessmentHistoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Global assessment lookup by id (still authorized via the assessment's target).
 */
@Path("/api/v1/assessments")
@Produces(MediaType.APPLICATION_JSON)
public class AssessmentLookupResource {

    @Inject
    AssessmentHistoryService assessmentHistoryService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    @Path("/{id}")
    public AssessmentRunSummary get(@PathParam("id") String id) {
        return sensitiveDataFilter.redact(assessmentHistoryService.getById(id));
    }
}
