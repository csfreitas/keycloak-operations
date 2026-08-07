package io.github.keycloakmcp.service;

import java.util.List;

import io.github.keycloakmcp.adapter.keycloak.KeycloakRepresentationMapper;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.user.UserDetails;
import io.github.keycloakmcp.domain.user.UserSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserService {

    private final StableAdminApiAdapter adminApi;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public UserService(
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

    public List<UserSummary> searchUsers(String targetId, String realm, String search, Integer first, Integer max) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            List<UserSummary> users = adminApi.searchUsers(target, realm, search, first, max).stream()
                    .map(KeycloakRepresentationMapper::toUserSummary)
                    .map(summary -> sensitiveDataFilter.redact(summary))
                    .toList();
            success = true;
            return users;
        } finally {
            auditService.logToolInvocation(
                    "UserService.searchUsers", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    public UserDetails getUser(String targetId, String realm, String userId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            UserDetails details = sensitiveDataFilter.redact(
                    KeycloakRepresentationMapper.toUserDetails(adminApi.getUser(target, realm, userId)));
            success = true;
            return details;
        } finally {
            auditService.logToolInvocation(
                    "UserService.getUser", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    private Target resolve(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return target;
    }
}
