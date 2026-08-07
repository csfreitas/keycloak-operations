package io.github.keycloakmcp.service;

import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import io.github.keycloakmcp.adapter.keycloak.KeycloakCapabilities;
import io.github.keycloakmcp.adapter.keycloak.KeycloakVersionDetector;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ServerInfoService {

    private final StableAdminApiAdapter adminApi;
    private final KeycloakVersionDetector versionDetector;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public ServerInfoService(
            StableAdminApiAdapter adminApi,
            KeycloakVersionDetector versionDetector,
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            SensitiveDataFilter sensitiveDataFilter,
            AuditService auditService) {
        this.adminApi = adminApi;
        this.versionDetector = versionDetector;
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.auditService = auditService;
    }

    public ServerInfo getServerInfo(String targetId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = targetResolver.require(targetId);
            targetAuthorization.assertAllowed(target, TargetPermission.READ);

            ServerInfoRepresentation representation = adminApi.getServerInfo(target);
            SystemInfoRepresentation systemInfo = representation.getSystemInfo();
            String rawVersion = systemInfo == null ? null : systemInfo.getVersion();
            String version = versionDetector.parseVersion(rawVersion).orElse(rawVersion);
            ServerInfo.Product product = productFromTarget(target.type());
            ServerInfo.Product detected = versionDetector.detectProduct(representation);
            if (detected != ServerInfo.Product.UNKNOWN) {
                product = detected;
            }
            KeycloakCapabilities capabilities = versionDetector.detectCapabilities(representation);

            ServerInfo info = new ServerInfo(
                    product,
                    version,
                    target.keycloak().url(),
                    target.keycloak().authRealm(),
                    capabilities);
            success = true;
            return sensitiveDataFilter.redact(info);
        } finally {
            auditService.logToolInvocation(
                    "ServerInfoService.getServerInfo",
                    targetId,
                    null,
                    System.currentTimeMillis() - start,
                    success);
        }
    }

    private static ServerInfo.Product productFromTarget(TargetType type) {
        if (type == null) {
            return ServerInfo.Product.UNKNOWN;
        }
        return switch (type) {
            case RHBK -> ServerInfo.Product.RHBK;
            case KEYCLOAK -> ServerInfo.Product.KEYCLOAK;
        };
    }
}
