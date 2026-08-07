package io.github.keycloakmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.RealmRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.realm.RealmSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Isolation: listing realms for target A must use Target A and must not touch Target B.
 */
class MultiTargetIsolationTest {

    private StableAdminApiAdapter adminApi;
    private TargetResolver targetResolver;
    private TargetAuthorizationService targetAuthorization;
    private RealmService realmService;

    private Target targetA;
    private Target targetB;

    @BeforeEach
    void setUp() {
        adminApi = mock(StableAdminApiAdapter.class);
        targetResolver = mock(TargetResolver.class);
        targetAuthorization = mock(TargetAuthorizationService.class);
        SensitiveDataFilter filter = new SensitiveDataFilter(new ObjectMapper());
        AuditService audit = mock(AuditService.class);

        targetA = target("lab-a", "http://kc-a:8080", "cred-a");
        targetB = target("lab-b", "http://kc-b:8080", "cred-b");

        when(targetResolver.require("lab-a")).thenReturn(targetA);
        when(targetResolver.require("lab-b")).thenReturn(targetB);
        when(targetResolver.require("does-not-exist"))
                .thenThrow(McpException.targetNotFound("does-not-exist"));

        RealmRepresentation realmA = new RealmRepresentation();
        realmA.setRealm("company-a");
        realmA.setDisplayName("Company A");
        realmA.setEnabled(true);

        RealmRepresentation realmB = new RealmRepresentation();
        realmB.setRealm("company-b");
        realmB.setDisplayName("Company B");
        realmB.setEnabled(true);

        when(adminApi.listRealms(eq(targetA))).thenReturn(List.of(realmA));
        when(adminApi.listRealms(eq(targetB))).thenReturn(List.of(realmB));

        realmService = new RealmService(adminApi, targetResolver, targetAuthorization, filter, audit);
    }

    @Test
    void listRealmsForTargetADoesNotReturnTargetBRealms() {
        List<RealmSummary> realms = realmService.listRealms("lab-a");

        assertThat(realms).extracting(RealmSummary::realm).containsExactly("company-a");
        assertThat(realms).extracting(RealmSummary::realm).doesNotContain("company-b");
        verify(adminApi).listRealms(targetA);
        verify(targetAuthorization).assertAllowed(targetA, TargetPermission.READ);
    }

    @Test
    void listRealmsForTargetBIsIsolated() {
        List<RealmSummary> realms = realmService.listRealms("lab-b");

        assertThat(realms).extracting(RealmSummary::realm).containsExactly("company-b");
        verify(adminApi).listRealms(targetB);
    }

    @Test
    void unknownTargetReturnsTargetNotFound() {
        assertThatThrownBy(() -> realmService.listRealms("does-not-exist"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.TARGET_NOT_FOUND));
    }

    private static Target target(String id, String url, String credentialRef) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration(url, "master", "keycloak-mcp", credentialRef),
                null,
                null,
                Map.of());
    }
}
