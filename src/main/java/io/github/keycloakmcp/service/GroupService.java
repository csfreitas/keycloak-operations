package io.github.keycloakmcp.service;

import java.util.List;

import io.github.keycloakmcp.adapter.keycloak.KeycloakRepresentationMapper;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.group.GroupDetails;
import io.github.keycloakmcp.domain.group.GroupSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GroupService {

    private final StableAdminApiAdapter adminApi;
    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SensitiveDataFilter sensitiveDataFilter;
    private final AuditService auditService;

    @Inject
    public GroupService(
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

    public List<GroupSummary> listGroups(String targetId, String realm, Integer first, Integer max) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            List<GroupSummary> groups = adminApi.listGroups(target, realm, true, first, max).stream()
                    .map(KeycloakRepresentationMapper::toGroupSummary)
                    .map(summary -> sensitiveDataFilter.redact(summary))
                    .toList();
            success = true;
            return groups;
        } finally {
            auditService.logToolInvocation(
                    "GroupService.listGroups", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    public GroupDetails getGroup(String targetId, String realm, String groupId) {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Target target = resolve(targetId);
            GroupDetails details = sensitiveDataFilter.redact(
                    KeycloakRepresentationMapper.toGroupDetails(adminApi.getGroup(target, realm, groupId)));
            success = true;
            return details;
        } finally {
            auditService.logToolInvocation(
                    "GroupService.getGroup", targetId, realm, System.currentTimeMillis() - start, success);
        }
    }

    private Target resolve(String targetId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);
        return target;
    }
}
