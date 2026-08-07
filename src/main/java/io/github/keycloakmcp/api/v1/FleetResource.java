package io.github.keycloakmcp.api.v1;

import java.util.List;

import io.github.keycloakmcp.domain.platform.FleetItem;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.service.platform.FleetService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/fleet")
@Produces(MediaType.APPLICATION_JSON)
public class FleetResource {

    @Inject
    FleetService fleetService;

    @Inject
    SensitiveDataFilter sensitiveDataFilter;

    @GET
    public List<FleetItem> fleet() {
        return sensitiveDataFilter.redact(fleetService.fleet());
    }
}
