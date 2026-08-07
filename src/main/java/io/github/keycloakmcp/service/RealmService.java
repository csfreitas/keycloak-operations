package io.github.keycloakmcp.service;

import java.util.List;

import io.github.keycloakmcp.adapter.keycloak.KeycloakRepresentationMapper;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.realm.RealmDetails;
import io.github.keycloakmcp.domain.realm.RealmSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RealmService {

    private final StableAdminApiAdapter adminApi;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public RealmService(
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

    public List<RealmSummary> listRealms(String targetId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            List<RealmSummary> realms = adminApi.listRealms(target).stream()
                    .map(KeycloakRepresentationMapper::toRealmSummary)
                    .map(summary -> sensitiveDataFilter.redact(summary))
                    .toList();
            success = true;
            return realms;
        } finally {
            auditService.logToolInvocation(
                    "RealmService.listRealms", targetId, null, System.currentTimeMillis() - start, success);
        }
    }

    public RealmDetails getRealm(String targetId, String realm) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            RealmDetails details = sensitiveDataFilter.redact(
                    KeycloakRepresentationMapper.toRealmDetails(adminApi.getRealm(target, realm)));
            success = true;
            return details;
        } finally {
            auditService.logToolInvocation(
                    "RealmService.getRealm", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    private Target resolve(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return target;
    }
}
