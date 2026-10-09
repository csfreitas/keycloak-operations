package io.github.keycloakmcp.api.v1;

import java.util.List;

import io.github.keycloakmcp.domain.configuration.ConfigurationObservation;
import io.github.keycloakmcp.domain.configuration.ConfigurationScope;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Channel;
import io.github.keycloakmcp.service.configuration.ConfigurationReadService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/configuration-reads")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigurationReadResource {
    @Inject ConfigurationReadService service;

    @GET
    public List<ConfigurationScope> list() { return service.list(Channel.REST); }

    @GET
    @Path("/{scopeId}")
    public ConfigurationObservation read(@PathParam("scopeId") String scopeId) { return service.read(scopeId, Channel.REST); }
}
