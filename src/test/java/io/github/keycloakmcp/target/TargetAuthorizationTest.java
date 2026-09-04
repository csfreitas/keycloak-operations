package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.config.PlatformAuthorizationConfig;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class TargetAuthorizationTest {

    private McpRuntimeConfig runtimeConfig;
    private TargetAuthorizationService authz;
    private PlatformAuthorizationConfig config;
    private SecurityIdentity identity;

    @BeforeEach
    void setUp() {
        runtimeConfig = mock(McpRuntimeConfig.class);
        when(runtimeConfig.readOnly()).thenReturn(true);
        config = mock(PlatformAuthorizationConfig.class);
        identity = mock(SecurityIdentity.class);
        when(config.mode()).thenReturn("local-lab");
        when(config.grants()).thenReturn(Map.of());
        authz = new TargetAuthorizationService(runtimeConfig, config, identity);
    }

    @Test
    void allowsReadAssessAndPlanOnEnabledTarget() {
        Target target = sample(true);
        authz.assertAllowed(target, TargetPermission.READ);
        authz.assertAllowed(target, TargetPermission.ASSESS);
        authz.assertAllowed(target, TargetPermission.PLAN);
    }

    @Test
    void deniesWriteWhenReadOnly() {
        Target target = sample(true);
        assertThatThrownBy(() -> authz.assertAllowed(target, TargetPermission.WRITE))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.TARGET_NOT_AUTHORIZED));
    }

    @Test
    void localLabUsesExplicitUnauthenticatedActorAndStillBlocksApproval() {
        assertThat(authz.currentActor()).isEqualTo("local-lab");
        assertThatThrownBy(() -> authz.assertAllowed(sample(true), TargetPermission.APPROVE))
                .isInstanceOf(McpException.class);
    }

    @Test
    void authenticatedModeDeniesAnonymousAndEmptyGrants() {
        when(config.mode()).thenReturn("authenticated");
        when(identity.isAnonymous()).thenReturn(true);
        assertThatThrownBy(() -> authz.assertAllowed(sample(true), TargetPermission.READ))
                .isInstanceOf(McpException.class);
        assertThatThrownBy(authz::currentActor).isInstanceOf(McpException.class);
        authenticated("operator");
        assertThat(authz.isAllowed(sample(true), TargetPermission.READ)).isFalse();
    }

    @Test
    void grantsAreExactByRoleTargetAndPermissionWithoutAdministrativeInheritance() {
        authenticated("operator");
        grant("assessor", Set.of("lab-a"), Set.of(TargetPermission.READ, TargetPermission.ASSESS));
        when(identity.hasRole("assessor")).thenReturn(true);
        assertThat(authz.isAllowed(sample(true), TargetPermission.READ)).isTrue();
        assertThat(authz.isAllowed(sample(true), TargetPermission.ASSESS)).isTrue();
        assertThat(authz.isAllowed(sample(true), TargetPermission.PLAN)).isFalse();
        assertThat(authz.isAllowed(sample("lab-b", true), TargetPermission.READ)).isFalse();
        when(identity.hasRole("assessor")).thenReturn(false);
        assertThat(authz.isAllowed(sample(true), TargetPermission.READ)).isFalse();
        grant("admin", Set.of("lab-a"), Set.of(TargetPermission.ADMIN));
        when(identity.hasRole("admin")).thenReturn(true);
        assertThat(authz.isAllowed(sample(true), TargetPermission.READ)).isFalse();
    }

    @Test
    void writeGrantCannotApproveAndApprovalGrantCannotWrite() {
        authenticated("operator");
        when(runtimeConfig.readOnly()).thenReturn(false);
        when(identity.hasRole("operator")).thenReturn(true);
        grant("operator", Set.of("lab-a"), Set.of(TargetPermission.WRITE));
        assertThat(authz.isAllowed(sample(true), TargetPermission.WRITE)).isTrue();
        assertThat(authz.isAllowed(sample(true), TargetPermission.APPROVE)).isFalse();
        grant("operator", Set.of("lab-a"), Set.of(TargetPermission.APPROVE));
        assertThat(authz.isAllowed(sample(true), TargetPermission.APPROVE)).isTrue();
        assertThat(authz.isAllowed(sample(true), TargetPermission.WRITE)).isFalse();
        when(runtimeConfig.readOnly()).thenReturn(true);
        assertThat(authz.isAllowed(sample(true), TargetPermission.APPROVE)).isFalse();
    }

    @Test
    void wildcardTargetsAndUnknownModesNeverGrantAccess() {
        authenticated("operator");
        when(identity.hasRole("operator")).thenReturn(true);
        grant("operator", Set.of("*"), Set.of(TargetPermission.READ));
        assertThat(authz.isAllowed(sample(true), TargetPermission.READ)).isFalse();
        when(config.mode()).thenReturn("local_lab_typo");
        assertThatThrownBy(() -> authz.assertAllowed(sample(true), TargetPermission.READ))
                .isInstanceOf(McpException.class);
    }

    @Test
    void actorComesFromValidatedPrincipalAndJwtIssuerSubject() {
        authenticated("operator");
        assertThat(authz.currentActor()).isEqualTo("operator");
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getName()).thenReturn("display-name");
        when(jwt.getIssuer()).thenReturn("https://idp.example/realms/operations");
        when(jwt.getSubject()).thenReturn("subject-123");
        when(identity.getPrincipal()).thenReturn(jwt);
        assertThat(authz.currentActor()).isEqualTo("https://idp.example/realms/operations#subject-123");
        when(jwt.getSubject()).thenReturn(null);
        assertThatThrownBy(authz::currentActor).isInstanceOf(McpException.class);
    }

    private void authenticated(String name) {
        when(config.mode()).thenReturn("authenticated");
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(() -> name);
    }

    private void grant(String role, Set<String> targets, Set<TargetPermission> permissions) {
        PlatformAuthorizationConfig.Grant grant = mock(PlatformAuthorizationConfig.Grant.class);
        when(grant.targets()).thenReturn(targets);
        when(grant.permissions()).thenReturn(permissions);
        when(config.grants()).thenReturn(Map.of(role, grant));
    }

    @Test
    void deniesDisabledTarget() {
        Target target = sample(false);
        assertThatThrownBy(() -> authz.assertAllowed(target, TargetPermission.READ))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> {
                    ErrorCode code = ((McpException) ex).getCode();
                    assertThat(code).isIn(ErrorCode.TARGET_DISABLED, ErrorCode.TARGET_NOT_AUTHORIZED);
                });
    }

    private static Target sample(boolean enabled) {
        return sample("lab-a", enabled);
    }

    private static Target sample(String id, boolean enabled) {
        return new Target(
                TargetId.of(id),
                "Lab A",
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                enabled,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "mcp", "cred-a"),
                null,
                null,
                Map.of());
    }
}
