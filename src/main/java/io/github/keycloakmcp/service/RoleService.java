package io.github.keycloakmcp.service;

import java.util.List;

import io.github.keycloakmcp.adapter.keycloak.KeycloakRepresentationMapper;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.role.RoleDetails;
import io.github.keycloakmcp.domain.role.RoleSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RoleService {

    private final StableAdminApiAdapter adminApi;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public RoleService(
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

    public List<RoleSummary> listRoles(String targetId, String realm, Integer first, Integer max) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            List<RoleSummary> roles = adminApi.listRealmRoles(target, realm, true, first, max).stream()
                    .map(KeycloakRepresentationMapper::toRoleSummary)
                    .map(summary -> sensitiveDataFilter.redact(summary))
                    .toList();
            success = true;
            return roles;
        } finally {
            auditService.logToolInvocation(
                    "RoleService.listRoles", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    public RoleDetails getRole(String targetId, String realm, String roleName) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            RoleDetails details = sensitiveDataFilter.redact(
                    KeycloakRepresentationMapper.toRoleDetails(adminApi.getRealmRole(target, realm, roleName)));
            success = true;
            return details;
        } finally {
            auditService.logToolInvocation(
                    "RoleService.getRole", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    private Target resolve(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return target;
    }
}
