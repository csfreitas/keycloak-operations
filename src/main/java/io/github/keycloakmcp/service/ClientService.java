package io.github.keycloakmcp.service;

import java.util.List;

import io.github.keycloakmcp.adapter.keycloak.KeycloakRepresentationMapper;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.client.ClientDetails;
import io.github.keycloakmcp.domain.client.ClientSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClientService {

    private final StableAdminApiAdapter adminApi;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public ClientService(
            StableAdminApiAdapter adminApi,
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            SensitiveDataFilter sensitiveDataFilter,
            AuditService auditService) {
        this.adminApi = adminApi;
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.sensitiveDataFilter = sensitiveDataFilter;
        this.auditService = auditService;
    }

    public List<ClientSummary> listClients(String targetId, String realm) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            List<ClientSummary> clients = adminApi.listClients(target, realm, true).stream()
                    .map(KeycloakRepresentationMapper::toClientSummary)
                    .map(summary -> sensitiveDataFilter.redact(summary))
                    .toList();
            success = true;
            return clients;
        } finally {
            auditService.logToolInvocation(
                    "ClientService.listClients", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    public ClientDetails getClient(String targetId, String realm, String clientId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            ClientDetails details = sensitiveDataFilter.redact(
                    KeycloakRepresentationMapper.toClientDetails(
                            adminApi.findClientByClientId(target, realm, clientId)));
            success = true;
            return details;
        } finally {
            auditService.logToolInvocation(
                    "ClientService.getClient", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    private Target resolve(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return target;
    }
}
