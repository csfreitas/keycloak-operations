package io.github.keycloakmcp.collector.keycloak;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.service.RealmService;
import io.github.keycloakmcp.service.ServerInfoService;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class KeycloakEvidenceCollector implements EvidenceCollector {

    private final ServerInfoService serverInfoService;
    private final RealmService realmService;

    @Inject
    public KeycloakEvidenceCollector(ServerInfoService serverInfoService, RealmService realmService) {
        this.serverInfoService = serverInfoService;
        this.realmService = realmService;
    }

    @Override
    public String source() {
        return "keycloak";
    }

    @Override
    public List<Evidence> collect(Target target) {
        String targetId = target.id().value();
        Instant now = Instant.now();
        List<Evidence> evidence = new ArrayList<>();
        ServerInfo serverInfo = serverInfoService.getServerInfo(targetId);
        evidence.add(new Evidence(targetId, source(), "server", "keycloak.version", serverInfo.version(), now));
        evidence.add(new Evidence(targetId, source(), "server", "keycloak.product", serverInfo.product().name(), now));
        evidence.add(new Evidence(
                targetId,
                source(),
                "realm",
                "keycloak.realm.count",
                realmService.listRealms(targetId).size(),
                now));
        return List.copyOf(evidence);
    }
}
