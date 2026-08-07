package io.github.keycloakmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.user.UserDetails;
import io.github.keycloakmcp.domain.user.UserSummary;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String TARGET_ID = "local-dev";

    @Mock
    private StableAdminApiAdapter adminApi;

    @Mock
    private TargetResolver targetResolver;

    @Mock
    private TargetAuthorizationService targetAuthorization;

    @Mock
    private AuditService auditService;

    private UserService userService;
    private Target target;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                adminApi,
                targetResolver,
                targetAuthorization,
                new SensitiveDataFilter(new ObjectMapper()),
                auditService);
        target = sampleTarget();
        when(targetResolver.require(TARGET_ID)).thenReturn(target);
    }

    @Test
    void searchUsersMapsToUserSummaryList() {
        UserRepresentation alice = new UserRepresentation();
        alice.setId("u-alice");
        alice.setUsername("alice");
        alice.setFirstName("Alice");
        alice.setLastName("Demo");
        alice.setEmail("alice@example.com");
        alice.setEnabled(true);

        when(adminApi.searchUsers(target, "mcp-demo", "alice", 0, 20)).thenReturn(List.of(alice));

        List<UserSummary> users = userService.searchUsers(TARGET_ID, "mcp-demo", "alice", 0, 20);

        assertThat(users).hasSize(1);
        assertThat(users.get(0).id()).isEqualTo("u-alice");
        assertThat(users.get(0).username()).isEqualTo("alice");
        assertThat(users.get(0).email()).isEqualTo("alice@example.com");
        assertThat(users.get(0).enabled()).isTrue();
        verify(adminApi).searchUsers(target, "mcp-demo", "alice", 0, 20);
    }

    @Test
    void getUserMapsToUserDetails() {
        UserRepresentation bob = new UserRepresentation();
        bob.setId("u-bob");
        bob.setUsername("bob");
        bob.setFirstName("Bob");
        bob.setLastName("Admin");
        bob.setEmail("bob@example.com");
        bob.setEnabled(true);
        bob.setEmailVerified(true);
        bob.setRequiredActions(List.of());
        bob.setCreatedTimestamp(1_700_000_000_000L);

        when(adminApi.getUser(target, "mcp-demo", "u-bob")).thenReturn(bob);

        UserDetails details = userService.getUser(TARGET_ID, "mcp-demo", "u-bob");

        assertThat(details.id()).isEqualTo("u-bob");
        assertThat(details.username()).isEqualTo("bob");
        assertThat(details.emailVerified()).isTrue();
        assertThat(details.createdTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(details.requiredActions()).isEmpty();
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
