package io.github.keycloakmcp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.github.keycloakmcp.adapter.keycloak.KeycloakVersionDetector;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.audit.AuditService;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import io.github.keycloakmcp.target.TargetType;

class ReadServiceMetadataTest {
    private static final String ID = "read-metadata-target";
    private static final String CANARY = "synthetic-read-canary";
    private static final String PROSE = "Review password policy and token lifespan";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private final SensitiveDataFilter filter = new SensitiveDataFilter(json);
    private StableAdminApiAdapter admin;
    private TargetResolver resolver;
    private TargetAuthorizationService authorization;
    private AuditService audit;
    private Target target;
    private List<Object> observations;

    enum Read { REALM_LIST, REALM_DETAIL, CLIENT_LIST, CLIENT_DETAIL, USER_LIST, USER_DETAIL,
        GROUP_LIST, GROUP_DETAIL, ROLE_LIST, ROLE_DETAIL, SERVER }

    @BeforeEach
    void setUp() {
        admin = mock(StableAdminApiAdapter.class);
        resolver = mock(TargetResolver.class);
        authorization = mock(TargetAuthorizationService.class);
        audit = mock(AuditService.class);
        target = new Target(TargetId.of(ID), "Read fixture", TargetType.KEYCLOAK, TargetEnvironment.DEV,
                true, new KeycloakTargetConfiguration("https://fixture.example", "master", "reader", "fixture"),
                null, null, Map.of());
        when(resolver.require(ID)).thenReturn(target);
    }

    static Stream<Arguments> unsafeReads() {
        return Stream.of(Read.values()).flatMap(read -> Stream.of(
                "password=" + CANARY,
                "Authorization: Bearer " + CANARY,
                "https://example.test/callback?access_token=" + CANARY)
                .map(value -> Arguments.of(read, value)));
    }

    @ParameterizedTest
    @MethodSource("unsafeReads")
    void filtersReadCopiesWithoutChangingProviderObservations(Read read, String text) {
        prepare(text);
        JsonNode before = json.valueToTree(observations);
        Object result = invoke(read);
        assertThat(json.valueToTree(result).toString()).doesNotContain(CANARY).contains("[REDACTED]");
        assertThat(json.valueToTree(observations).equals(before)).as("raw observations unchanged").isTrue();
        verify(authorization).assertAllowed(target, TargetPermission.READ);
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void ordinaryTextAndTypedFactsRemainAvailable(Read read) {
        prepare(PROSE);
        JsonNode result = json.valueToTree(invoke(read));
        assertThat(result.toString()).contains(PROSE).doesNotContain("[REDACTED]");
        JsonNode item = result.isArray() ? result.get(0) : result;
        if (item.has("id")) assertThat(item.get("id").asText()).isEqualTo("resource-id");
        if (item.has("enabled")) assertThat(item.get("enabled").booleanValue()).isFalse();
        if (item.has("subGroupCount")) assertThat(item.get("subGroupCount").longValue()).isZero();
        if (item.has("createdTimestamp")) assertThat(item.get("createdTimestamp").longValue()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Read.class)
    void deniedTargetNeverReachesProvider(Read read) {
        doThrow(McpException.targetUnauthorized(ID)).when(authorization).assertAllowed(target, TargetPermission.READ);
        assertThatThrownBy(() -> invoke(read)).isInstanceOf(McpException.class);
        verifyNoInteractions(admin);
    }

    @Test
    void credentialShapedProviderIdentifierIsLossyButNeverReusedAsLookupState() {
        prepare(PROSE);
        ClientRepresentation client = (ClientRepresentation) observations.get(1);
        client.setId("password=" + CANARY);
        JsonNode result = json.valueToTree(invoke(Read.CLIENT_DETAIL));
        assertThat(result.get("id").asText()).contains("[REDACTED]").doesNotContain(CANARY);
        assertThat(client.getId()).isEqualTo("password=" + CANARY);
        verify(admin).findClientByClientId(target, "realm", "client");
    }

    private void prepare(String text) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("realm"); realm.setDisplayName(text); realm.setEnabled(false);
        realm.setPasswordPolicy("length(12) and digits(1)");
        ClientRepresentation client = new ClientRepresentation();
        client.setId("resource-id"); client.setClientId("client"); client.setName(text);
        client.setDescription(text); client.setEnabled(false);
        client.setRedirectUris(List.of(text)); client.setWebOrigins(List.of(text));
        UserRepresentation user = new UserRepresentation();
        user.setId("resource-id"); user.setUsername("user"); user.setFirstName(text);
        user.setEnabled(false); user.setCreatedTimestamp(0L); user.setRequiredActions(List.of(text));
        GroupRepresentation group = new GroupRepresentation();
        group.setId("resource-id"); group.setName(text); group.setPath("/group"); group.setSubGroupCount(0L);
        RoleRepresentation role = new RoleRepresentation();
        role.setId("resource-id"); role.setName("role"); role.setDescription(text); role.setComposite(false);
        ServerInfoRepresentation server = new ServerInfoRepresentation();
        SystemInfoRepresentation system = new SystemInfoRepresentation();
        system.setVersion(text); server.setSystemInfo(system);
        observations = List.of(realm, client, user, group, role, server);
        when(admin.listRealms(target)).thenReturn(List.of(realm));
        when(admin.getRealm(target, "realm")).thenReturn(realm);
        when(admin.listClients(target, "realm", true)).thenReturn(List.of(client));
        when(admin.findClientByClientId(target, "realm", "client")).thenReturn(client);
        when(admin.searchUsers(target, "realm", null, 0, 10)).thenReturn(List.of(user));
        when(admin.getUser(target, "realm", "resource-id")).thenReturn(user);
        when(admin.listGroups(target, "realm", true, 0, 10)).thenReturn(List.of(group));
        when(admin.getGroup(target, "realm", "resource-id")).thenReturn(group);
        when(admin.listRealmRoles(target, "realm", true, 0, 10)).thenReturn(List.of(role));
        when(admin.getRealmRole(target, "realm", "role")).thenReturn(role);
        when(admin.getServerInfo(target)).thenReturn(server);
    }

    private Object invoke(Read read) {
        return switch (read) {
            case REALM_LIST -> new RealmService(admin, resolver, authorization, filter, audit).listRealms(ID);
            case REALM_DETAIL -> new RealmService(admin, resolver, authorization, filter, audit).getRealm(ID, "realm");
            case CLIENT_LIST -> new ClientService(admin, resolver, authorization, filter, audit).listClients(ID, "realm");
            case CLIENT_DETAIL -> new ClientService(admin, resolver, authorization, filter, audit).getClient(ID, "realm", "client");
            case USER_LIST -> new UserService(admin, resolver, authorization, filter, audit).searchUsers(ID, "realm", null, 0, 10);
            case USER_DETAIL -> new UserService(admin, resolver, authorization, filter, audit).getUser(ID, "realm", "resource-id");
            case GROUP_LIST -> new GroupService(admin, resolver, authorization, filter, audit).listGroups(ID, "realm", 0, 10);
            case GROUP_DETAIL -> new GroupService(admin, resolver, authorization, filter, audit).getGroup(ID, "realm", "resource-id");
            case ROLE_LIST -> new RoleService(admin, resolver, authorization, filter, audit).listRoles(ID, "realm", 0, 10);
            case ROLE_DETAIL -> new RoleService(admin, resolver, authorization, filter, audit).getRole(ID, "realm", "role");
            case SERVER -> new ServerInfoService(admin, new KeycloakVersionDetector(), resolver, authorization, filter, audit).getServerInfo(ID);
        };
    }
}
