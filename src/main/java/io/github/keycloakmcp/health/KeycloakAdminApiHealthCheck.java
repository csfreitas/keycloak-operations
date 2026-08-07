package io.github.keycloakmcp.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KeycloakAdminApiHealthCheck implements HealthCheck {

    private final StableAdminApiAdapter adminApi;

    @Inject
    public KeycloakAdminApiHealthCheck(StableAdminApiAdapter adminApi) {
        this.adminApi = adminApi;
    }

    @Override
    public String name() {
        return "keycloak.adminApi";
    }

    @Override
    public HealthComponentResult check(Target target) {
        long start = System.currentTimeMillis();
        try {
            ServerInfoRepresentation info = adminApi.getServerInfo(target);
            SystemInfoRepresentation system = info == null ? null : info.getSystemInfo();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("version", system == null ? null : system.getVersion());
            long latency = System.currentTimeMillis() - start;
            details.put("latencyMs", latency);
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.HEALTHY,
                    "Admin API reachable",
                    details,
                    latency);
        } catch (RuntimeException e) {
            return HealthComponentResult.of(
                    name(),
                    HealthStatus.CRITICAL,
                    e.getMessage() == null ? "Admin API unreachable" : e.getMessage(),
                    Map.of(),
                    System.currentTimeMillis() - start);
        }
    }
}
