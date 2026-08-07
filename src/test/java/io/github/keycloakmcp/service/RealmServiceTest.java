package io.github.keycloakmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.RealmRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
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

@ExtendWith(MockitoExtension.class)
class RealmServiceTest {

    private static final String TARGET_ID = "local-dev";

    @Mock
    private StableAdminApiAdapter adminApi;

    @Mock
    private TargetResolver targetResolver;

    @Mock
    private TargetAuthorizationService targetAuthorization;

    @Mock
    private AuditService auditService;

    private RealmService realmService;
    private Target target;

    @BeforeEach
    void setUp() {
        SensitiveDataFilter filter = new SensitiveDataFilter(new ObjectMapper());
        realmService = new RealmService(adminApi, targetResolver, targetAuthorization, filter, auditService);
        target = sampleTarget();
        when(targetResolver.require(TARGET_ID)).thenReturn(target);
    }

    @Test
    void listRealmsMapsRepresentationsToRealmSummaryList() {
        RealmRepresentation master = new RealmRepresentation();
        master.setRealm("master");
        master.setDisplayName("Master");
        master.setEnabled(true);

        RealmRepresentation demo = new RealmRepresentation();
        demo.setRealm("mcp-demo");
        demo.setDisplayName("MCP Demo Realm");
        demo.setEnabled(true);

        when(adminApi.listRealms(target)).thenReturn(List.of(master, demo));

        List<RealmSummary> realms = realmService.listRealms(TARGET_ID);

        assertThat(realms).hasSize(2);
        assertThat(realms.get(0).realm()).isEqualTo("master");
        assertThat(realms.get(0).displayName()).isEqualTo("Master");
        assertThat(realms.get(0).enabled()).isTrue();
        assertThat(realms.get(1).realm()).isEqualTo("mcp-demo");
        assertThat(realms.get(1).displayName()).isEqualTo("MCP Demo Realm");

        verify(targetAuthorization).assertAllowed(eq(target), eq(TargetPermission.READ));
        verify(auditService).logToolInvocation(
                anyString(),
                eq(TARGET_ID),
                isNull(),
                anyLong(),
                anyBoolean());
    }

    private static Target sampleTarget() {
        return new Target(
                TargetId.of(TARGET_ID),
                "Local Dev",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "keycloak-mcp", "local-dev"),
                null,
                null,
                Map.of());
    }
}
