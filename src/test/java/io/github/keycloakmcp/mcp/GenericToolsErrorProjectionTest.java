package io.github.keycloakmcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;

import io.github.keycloakmcp.assessment.profile.ProfileRegistry;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpError;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.mcp.assessment.AssessmentTools;
import io.github.keycloakmcp.mcp.client.ClientTools;
import io.github.keycloakmcp.mcp.group.GroupTools;
import io.github.keycloakmcp.mcp.inventory.InventoryTools;
import io.github.keycloakmcp.mcp.metrics.MetricsTools;
import io.github.keycloakmcp.mcp.realm.RealmTools;
import io.github.keycloakmcp.mcp.role.RoleTools;
import io.github.keycloakmcp.mcp.server.ServerInfoTools;
import io.github.keycloakmcp.mcp.target.TargetTools;
import io.github.keycloakmcp.mcp.user.UserTools;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.observability.metrics.MetricCategory;
import io.github.keycloakmcp.security.ToolAuthorization;
import io.github.keycloakmcp.service.ClientService;
import io.github.keycloakmcp.service.GroupService;
import io.github.keycloakmcp.service.RealmService;
import io.github.keycloakmcp.service.RoleService;
import io.github.keycloakmcp.service.ServerInfoService;
import io.github.keycloakmcp.service.UserService;
import io.github.keycloakmcp.service.platform.InventoryService;
import io.github.keycloakmcp.service.platform.MetricsService;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetRegistry;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/** Exercises the real per-family invocation boundary, with synthetic failures only. */
@QuarkusTest
class GenericToolsErrorProjectionTest {
    private static final String TARGET = "target-a";
    private static final String REALM = "realm-a";
    private static final String INTERNAL = "INTERNAL_ERROR: tool operation failed";

    @Inject RealmTools realmTools;
    @Inject ClientTools clientTools;
    @Inject GroupTools groupTools;
    @Inject RoleTools roleTools;
    @Inject UserTools userTools;
    @Inject ServerInfoTools serverTools;
    @Inject InventoryTools inventoryTools;
    @Inject MetricsTools metricsTools;
    @Inject AssessmentTools assessmentTools;
    @Inject TargetTools targetTools;

    @InjectMock RealmService realms;
    @InjectMock ClientService clients;
    @InjectMock GroupService groups;
    @InjectMock RoleService roles;
    @InjectMock UserService users;
    @InjectMock ServerInfoService server;
    @InjectMock InventoryService inventory;
    @InjectMock MetricsService semanticMetrics;
    @InjectMock ProfileRegistry profiles;
    @InjectMock TargetRegistry targets;
    @InjectMock TargetAuthorizationService targetAuthorization;
    @InjectMock ToolAuthorization authorization;
    @InjectMock AuditService audit;
    @InjectMock McpMetrics metrics;

    @BeforeEach
    void resetCollaborators() {
        reset(realms, clients, groups, roles, users, server, inventory, semanticMetrics,
                profiles, targets, targetAuthorization, authorization, audit, metrics);
    }

    @ParameterizedTest(name = "{0}: credential format {1}")
    @MethodSource("credentialFailures")
    void domainMessagesAreFilteredWithoutCauseOrSuppressedFailures(
            Family family, String message, String expected) {
        McpException failure = new McpException(McpError.of(ErrorCode.TARGET_NOT_AUTHORIZED, message),
                new IllegalStateException("password=nested-cause-canary"));
        failure.addSuppressed(new IllegalStateException("token=suppressed-canary"));
        stub(family, failure);

        ToolCallException projected = failed(family);

        assertThat(projected).hasMessage("TARGET_NOT_AUTHORIZED: " + expected).hasNoCause();
        assertThat(projected.getSuppressed()).isEmpty();
        assertThat(failure.getMessage()).isEqualTo(message);
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void safeDomainCodeAndExplanationRemainUseful(Family family) {
        stub(family, McpException.of(ErrorCode.INVALID_ARGUMENT, "Token lifespan policy requires a bounded window"));

        assertThat(failed(family)).hasMessage("INVALID_ARGUMENT: Token lifespan policy requires a bounded window")
                .hasNoCause();
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void missingDomainMessageUsesStableCodeFallback(Family family) {
        McpException failure = new McpException(McpError.of(ErrorCode.KEYCLOAK_UNAVAILABLE, "unused")) {
            @Override
            public String getMessage() {
                return null;
            }
        };
        stub(family, failure);

        assertThat(failed(family)).hasMessage("KEYCLOAK_UNAVAILABLE: KEYCLOAK_UNAVAILABLE").hasNoCause();
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void prebuiltToolFailuresAreCopiedWithoutRawCauseOrSuppressedFailures(Family family) {
        ToolCallException failure = new ToolCallException("INVALID_ARGUMENT: password=synthetic-canary",
                new IllegalStateException("password=nested-cause-canary"));
        failure.addSuppressed(new IllegalStateException("token=suppressed-canary"));
        stub(family, failure);

        ToolCallException projected = failed(family);

        assertThat(projected).isNotSameAs(failure)
                .hasMessage("INVALID_ARGUMENT: password=[REDACTED]").hasNoCause();
        assertThat(projected.getSuppressed()).isEmpty();
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void missingPrebuiltToolMessageUsesFixedFallback(Family family) {
        stub(family, new ToolCallException((String) null));

        assertThat(failed(family)).hasMessage(INTERNAL).hasNoCause();
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void arbitraryRuntimeFailuresUseFixedDiagnosticEvenForUnrecognizedText(Family family) {
        stub(family, new IllegalStateException("unlabeled-synthetic-canary",
                McpException.internal("password=nested-cause-canary")));

        assertThat(failed(family)).hasMessage(INTERNAL).hasNoCause();
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void checkedProviderFailuresUseFixedDiagnostic(Family family) {
        stub(family, new Exception("unlabeled-checked-canary"));

        assertThat(failed(family)).hasMessage(INTERNAL).hasNoCause();
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void readOnlyGateStillPrecedesProviderAndItsDenialIsFiltered(Family family) {
        doThrow(McpException.authorizationFailed("Operation denied: token=synthetic-canary"))
                .when(authorization).assertReadOnlyOperation(family.toolName);

        assertThat(failed(family)).hasMessage("AUTHORIZATION_FAILED: Operation denied: token=[REDACTED]")
                .hasNoCause();
        verifyNoInteractions(realms, clients, groups, roles, users, server, inventory, semanticMetrics,
                profiles, targets, targetAuthorization);
        verifyOutcome(family, false);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void successfulReadKeepsSuccessAuditAndMetrics(Family family) {
        stub(family, null);

        Object result = invoke(family);

        if (family != Family.SERVER && family != Family.INVENTORY) {
            assertThat(result).isEqualTo(List.of());
        } else {
            assertThat(result).isNull();
        }
        verifyOutcome(family, true);
    }

    @Test
    void invalidMetricsCategoryDoesNotEchoCredentialsOrReachProvider() {
        ToolCallException projected = assertThrows(ToolCallException.class,
                () -> metricsTools.keycloakGetMetrics(TARGET, "password=synthetic-canary", ""));

        assertThat(projected).hasMessage("INVALID_ARGUMENT: Unsupported metrics category: password=[REDACTED]");
        verifyNoInteractions(semanticMetrics);
    }

    @Test
    void invalidTargetFilterDoesNotEchoCredentialsOrReachRegistry() {
        ToolCallException projected = assertThrows(ToolCallException.class,
                () -> targetTools.keycloakFindTargets("password=synthetic-canary", ""));

        assertThat(projected).hasMessage("INVALID_ARGUMENT: Invalid product filter: password=[REDACTED]");
        verifyNoInteractions(targets);
    }

    private ToolCallException failed(Family family) {
        return assertThrows(ToolCallException.class, () -> invoke(family));
    }

    private Object invoke(Family family) {
        return switch (family) {
            case REALM -> realmTools.keycloakListRealms(TARGET);
            case CLIENT -> clientTools.keycloakListClients(TARGET, REALM);
            case GROUP -> groupTools.keycloakListGroups(TARGET, REALM, 0, 100);
            case ROLE -> roleTools.keycloakListRoles(TARGET, REALM, 0, 100);
            case USER -> userTools.keycloakSearchUsers(TARGET, REALM, "user", 0, 50);
            case SERVER -> serverTools.keycloakServerInfo(TARGET);
            case INVENTORY -> inventoryTools.keycloakGetInventory(TARGET);
            case METRICS -> metricsTools.keycloakGetMetrics(TARGET, "JVM", "");
            case ASSESSMENT -> assessmentTools.keycloakListAssessmentProfiles();
            case TARGET -> targetTools.keycloakListTargets();
        };
    }

    private void stub(Family family, Exception failure) {
        Answer<Object> answer = invocation -> {
            if (failure != null) {
                throw failure;
            }
            return family == Family.SERVER || family == Family.INVENTORY ? null : List.of();
        };
        switch (family) {
            case REALM -> doAnswer(answer).when(realms).listRealms(TARGET);
            case CLIENT -> doAnswer(answer).when(clients).listClients(TARGET, REALM);
            case GROUP -> doAnswer(answer).when(groups).listGroups(TARGET, REALM, 0, 100);
            case ROLE -> doAnswer(answer).when(roles).listRoles(TARGET, REALM, 0, 100);
            case USER -> doAnswer(answer).when(users).searchUsers(TARGET, REALM, "user", 0, 50);
            case SERVER -> doAnswer(answer).when(server).getServerInfo(TARGET);
            case INVENTORY -> doAnswer(answer).when(inventory).collect(TARGET);
            case METRICS -> doAnswer(answer).when(semanticMetrics).category(TARGET, MetricCategory.JVM, null);
            case ASSESSMENT -> doAnswer(answer).when(profiles).all();
            case TARGET -> doAnswer(answer).when(targets).list();
        }
    }

    private void verifyOutcome(Family family, boolean success) {
        verify(authorization).assertReadOnlyOperation(family.toolName);
        verify(metrics).recordToolInvocation(eq(family.toolName), anyLong(), eq(success));
        verify(audit).logToolInvocation(eq(family.toolName), eq(family.target), eq(family.realm),
                anyLong(), eq(success));
    }

    private static Stream<Arguments> credentialFailures() {
        return Arrays.stream(Family.values()).flatMap(family -> Stream.of(
                Arguments.of(family, "password=synthetic-canary", "password=[REDACTED]"),
                Arguments.of(family, "Bearer synthetic-canary", "Bearer [REDACTED]"),
                Arguments.of(family, "https://operator:synthetic-canary@example.test", "https://[REDACTED]@example.test"),
                Arguments.of(family, "eyJhbGciOiJub25lIn0.eyJzdWIiOiJjYW5hcnkifQ.synthetic", "[REDACTED]")));
    }

    enum Family {
        REALM("keycloak_list_realms", GenericToolsErrorProjectionTest.TARGET, null),
        CLIENT("keycloak_list_clients", GenericToolsErrorProjectionTest.TARGET, GenericToolsErrorProjectionTest.REALM),
        GROUP("keycloak_list_groups", GenericToolsErrorProjectionTest.TARGET, GenericToolsErrorProjectionTest.REALM),
        ROLE("keycloak_list_roles", GenericToolsErrorProjectionTest.TARGET, GenericToolsErrorProjectionTest.REALM),
        USER("keycloak_search_users", GenericToolsErrorProjectionTest.TARGET, GenericToolsErrorProjectionTest.REALM),
        SERVER("keycloak_server_info", GenericToolsErrorProjectionTest.TARGET, null),
        INVENTORY("keycloak_get_inventory", GenericToolsErrorProjectionTest.TARGET, null),
        METRICS("keycloak_get_metrics", GenericToolsErrorProjectionTest.TARGET, null),
        ASSESSMENT("keycloak_list_assessment_profiles", null, null),
        TARGET("keycloak_list_targets", null, null);

        final String toolName;
        final String target;
        final String realm;

        Family(String toolName, String target, String realm) {
            this.toolName = toolName;
            this.target = target;
            this.realm = realm;
        }
    }
}
