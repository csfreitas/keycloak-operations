package io.github.keycloakmcp.api.v1;

import java.util.List;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/assessment-profiles")
@Produces(MediaType.APPLICATION_JSON)
public class AssessmentProfileResource {

    @Inject
    ProfileRegistry profileRegistry;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public List<AssessmentProfile> list() {
        return sensitiveDataFilter.redact(List.copyOf(profileRegistry.all()));
    }
}
