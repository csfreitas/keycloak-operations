package io.github.keycloakmcp.security;

import static io.github.keycloakmcp.security.ConfigurationReadBoundaryTestProfile.CLIENT_BETA_ID;
import static io.github.keycloakmcp.security.ConfigurationReadBoundaryTestProfile.REALM_ALPHA_ID;
import static io.github.keycloakmcp.security.ConfigurationReadBoundaryTestProfile.REALM_BETA_ID;
import static io.github.keycloakmcp.security.ConfigurationReadBoundaryTestProfile.TARGET;
import static io.github.keycloakmcp.security.ConfigurationReadFixtureIdentityAugmentor.CLIENT_ATTRIBUTE;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;

/** HTTP and MCP policy boundary with synthetic trusted JWT identities and a mocked provider only. */
@QuarkusTest
@TestProfile(ConfigurationReadBoundaryTestProfile.class)
class ConfigurationReadBoundaryTest {
    private static final String BASE = "/api/v1/configuration-reads";
    private static final String CANARY = "configuration-boundary-private-canary";

    @Inject ObjectMapper mapper;
    @InjectMock StableAdminApiAdapter adapter;

    @BeforeEach
    void fixtures() {
        reset(adapter);
        RealmRepresentation alpha = new RealmRepresentation();
        alpha.setId(REALM_ALPHA_ID);
        alpha.setRealm("realm-a");
        alpha.setRegistrationAllowed(false);
        alpha.setVerifyEmail(true);
        alpha.setResetPasswordAllowed(true);
        alpha.setDisplayName(CANARY);
        alpha.setAttributes(Map.of("internal-note", CANARY));
        RealmRepresentation beta = new RealmRepresentation();
        beta.setId(REALM_BETA_ID);
        beta.setRealm("realm-b");
        ClientRepresentation client = new ClientRepresentation();
        client.setId(CLIENT_BETA_ID);
        client.setClientId(CANARY);
        client.setEnabled(true);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(true);
        client.setSecret(CANARY);
        client.setAttributes(Map.of("internal-note", CANARY));
        when(adapter.getRealm(any(), eq("realm-a"))).thenReturn(alpha);
        when(adapter.getRealm(any(), eq("realm-b"))).thenReturn(beta);
        when(adapter.getClient(any(), eq("realm-b"), eq(CLIENT_BETA_ID))).thenReturn(client);
    }

    @Test
    void anonymousCannotDiscoverOrReadConfigurationScopes() {
        given().get(BASE).then().statusCode(anyOf(equalTo(401), equalTo(403)));
        given().get(BASE + "/realm-alpha").then().statusCode(anyOf(equalTo(401), equalTo(403)));
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "operator-console"))
    void operatorAlphaOnlyDiscoversAndReadsItsOwnRealmFields() throws Exception {
        String scopes = given().get(BASE).then().statusCode(200).body("$", hasSize(1))
                .header("Cache-Control", equalTo("no-store")).header("Vary", containsString("Authorization"))
                .body("[0].scopeId", equalTo("realm-alpha")).body("[0].targetId", equalTo(TARGET))
                .extract().asString();
        assertThat(scopes).doesNotContain("client-beta", "realm-b", REALM_ALPHA_ID, CANARY);
        JsonNode observation = readRest("realm-alpha");
        assertThat(observation.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(observation.path("observationId").asText()).isNotBlank();
        assertThat(observation.path("collectedAt").asText()).isNotBlank();
        assertThat(observation.path("source").asText()).isEqualTo("KEYCLOAK_ADMIN_API");
        assertThat(observation.path("productVersion").asText()).isEqualTo("UNKNOWN");
        assertThat(observation.path("status").asText()).isEqualTo("COMPLETE");
        assertThat(observation.path("facts")).isEqualTo(mapper.readTree("{\"registrationAllowed\":false,\"verifyEmail\":true}"));
        assertThat(observation.path("missingFields")).isEmpty();
        assertThat(observation.toString()).doesNotContain(CANARY, "resetPasswordAllowed", REALM_ALPHA_ID);
        verify(adapter).getRealm(any(), eq("realm-a"));
        verifyNoMoreInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "beta", roles = "scope-beta", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "operator-console"))
    void operatorBetaHasDifferentClientScopeOnTheSameTarget() throws Exception {
        String scopes = given().get(BASE).then().statusCode(200).body("$", hasSize(1))
                .body("[0].scopeId", equalTo("client-beta")).body("[0].targetId", equalTo(TARGET))
                .extract().asString();
        assertThat(scopes).doesNotContain("realm-alpha", "realm-a", CLIENT_BETA_ID, CANARY);
        JsonNode observation = readRest("client-beta");
        assertThat(observation.path("facts")).isEqualTo(mapper.readTree("{\"enabled\":true,\"publicClient\":false}"));
        assertThat(observation.path("scope").path("realm").asText()).isEqualTo("realm-b");
        assertThat(observation.toString()).doesNotContain(CANARY, "directAccessGrantsEnabled", CLIENT_BETA_ID, REALM_BETA_ID);
        verify(adapter, times(2)).getRealm(any(), eq("realm-b"));
        verify(adapter).getClient(any(), eq("realm-b"), eq(CLIENT_BETA_ID));
        verifyNoMoreInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "operator-console"))
    void forbiddenExistingAndUnknownScopesAreIndistinguishableBeforeProviderAccess() {
        String denied = given().get(BASE + "/client-beta").then().statusCode(403)
                .header("Cache-Control", equalTo("no-store")).header("Vary", containsString("Authorization"))
                .extract().asString();
        String absent = given().get(BASE + "/unknown-scope").then().statusCode(403)
                .header("Cache-Control", equalTo("no-store")).header("Vary", containsString("Authorization"))
                .extract().asString();
        assertThat(denied).isEqualTo(absent).doesNotContain("client-beta", "realm-b", "unknown-scope", CANARY);
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "beta", roles = "scope-beta", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "operator-console"))
    void operatorBetaCannotReadAlphaScope() {
        given().get(BASE + "/realm-alpha").then().statusCode(403);
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "legacy", roles = "legacy-reader", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "dual-client"))
    void legacyReadGrantDoesNotBecomeAConfigurationScopeGrant() throws Exception {
        given().get("/api/v1/targets/" + TARGET).then().statusCode(200);
        given().get(BASE).then().statusCode(200).body("$", hasSize(0));
        given().get(BASE + "/realm-alpha").then().statusCode(403);
        try (McpSession session = initializeMcp()) {
            assertDenied(session.call("keycloak_read_configuration", "{\"scopeId\":\"realm-alpha\"}"));
        }
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "mixed", roles = {"legacy-reader", "scope-alpha"},
            augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "dual-client"))
    void additiveScopeDoesNotPretendToNarrowExistingLegacyRead() throws Exception {
        given().get("/api/v1/targets/" + TARGET).then().statusCode(200);
        given().get(BASE).then().statusCode(200).body("$", hasSize(1));
        assertThat(readRest("realm-alpha").path("scope").path("scopeId").asText()).isEqualTo("realm-alpha");
        given().get(BASE + "/client-beta").then().statusCode(403);
        verify(adapter).getRealm(any(), eq("realm-a"));
        verifyNoMoreInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha")
    void roleWithoutTrustedJwtBindingCannotUseSpoofedIdentityHeaders() {
        given().header("X-Client-Id", "operator-console").header("X-Subject", "subject-alpha")
                .header("X-Issuer", "https://synthetic-identity.invalid/realms/operators")
                .get(BASE + "/realm-alpha").then().statusCode(anyOf(equalTo(401), equalTo(403)));
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "unapproved-client"))
    void clientClaimCannotBeOverriddenByRequestHeaders() {
        given().header("X-Client-Id", "operator-console").header("X-Role", "scope-alpha")
                .get(BASE + "/realm-alpha").then().statusCode(403);
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "operator-console"))
    void approvedConsoleClientIsNotAutomaticallyApprovedForMcp() throws Exception {
        // MCP clientInfo is caller-controlled presentation, not the verified azp claim.
        try (McpSession session = initializeMcp("reference-agent")) {
            JsonNode scopes = session.call("keycloak_list_configuration_scopes", "{}");
            assertThat(scopes.path("result").path("isError").asBoolean()).isFalse();
            assertThat(scopes.toString()).doesNotContain("realm-alpha", "client-beta");
            assertDenied(session.call("keycloak_read_configuration", "{\"scopeId\":\"realm-alpha\"}"));
        }
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "reference-agent"))
    void approvedAgentClientIsNotAutomaticallyApprovedForRest() {
        given().get(BASE + "/realm-alpha").then().statusCode(403);
        verifyNoInteractions(adapter);
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "dual-client"))
    void equivalentRestAndMcpContextsReturnTheSamePermittedFacts() throws Exception {
        JsonNode rest = readRest("realm-alpha");
        try (McpSession session = initializeMcp()) {
            JsonNode listed = session.call("keycloak_list_configuration_scopes", "{}");
            assertThat(listed.path("result").path("isError").asBoolean()).isFalse();
            assertThat(listed.toString()).contains("realm-alpha").doesNotContain("client-beta", "realm-b");
            JsonNode envelope = session.call("keycloak_read_configuration", "{\"scopeId\":\"realm-alpha\"}");
            assertThat(envelope.path("result").path("isError").asBoolean()).isFalse();
            JsonNode mcp = toolPayload(envelope);
            assertThat(mcp.path("facts")).isEqualTo(rest.path("facts"));
            assertThat(mcp.path("scope")).isEqualTo(rest.path("scope"));
            assertThat(mcp.path("status")).isEqualTo(rest.path("status"));
            assertThat(mcp.toString()).doesNotContain(CANARY, "resetPasswordAllowed");
        }
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "dual-client"))
    void missingPermittedFieldRemainsNullAndPartialAcrossRestAndMcp() throws Exception {
        RealmRepresentation incomplete = new RealmRepresentation();
        incomplete.setId(REALM_ALPHA_ID);
        incomplete.setRealm("realm-a");
        incomplete.setRegistrationAllowed(false);
        incomplete.setVerifyEmail(null);
        when(adapter.getRealm(any(), eq("realm-a"))).thenReturn(incomplete);
        JsonNode rest = readRest("realm-alpha");
        assertThat(rest.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(rest.path("facts")).isEqualTo(mapper.readTree("{\"registrationAllowed\":false,\"verifyEmail\":null}"));
        assertThat(rest.path("missingFields")).isEqualTo(mapper.readTree("[\"verifyEmail\"]"));
        try (McpSession session = initializeMcp()) {
            JsonNode mcp = toolPayload(session.call("keycloak_read_configuration", "{\"scopeId\":\"realm-alpha\"}"));
            assertThat(mcp.path("status")).isEqualTo(rest.path("status"));
            assertThat(mcp.path("facts")).isEqualTo(rest.path("facts"));
            assertThat(mcp.path("missingFields")).isEqualTo(rest.path("missingFields"));
        }
    }

    @Test
    @TestSecurity(user = "alpha", roles = "scope-alpha", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "dual-client"))
    void restrictedScopeDoesNotOpenLegacyOverviewReportsHistoryOrTools() throws Exception {
        given().get("/api/v1/targets").then().statusCode(200).body("$", hasSize(0));
        given().get("/api/v1/targets/" + TARGET + "/overview").then().statusCode(403);
        given().get("/api/v1/targets/" + TARGET + "/assessments").then().statusCode(403);
        given().get("/api/v1/targets/" + TARGET + "/snapshots").then().statusCode(403);
        given().get("/api/v1/targets/" + TARGET + "/health-checks").then().statusCode(403);
        given().contentType("application/json").body("{}")
                .post("/api/v1/targets/" + TARGET + "/operations-reports").then().statusCode(403);
        try (McpSession session = initializeMcp()) {
            assertDenied(session.call("keycloak_list_realms", "{\"targetId\":\"" + TARGET + "\"}"));
            assertDenied(session.call("keycloak_get_realm", "{\"targetId\":\"" + TARGET + "\",\"realm\":\"realm-a\"}"));
            JsonNode scopes = session.call("keycloak_list_configuration_scopes", "{}");
            assertThat(scopes.path("result").path("isError").asBoolean()).isFalse();
            assertThat(scopes.toString()).contains("realm-alpha");
        }
        verifyNoInteractions(adapter);
    }

    private JsonNode readRest(String scopeId) throws Exception {
        return mapper.readTree(given().get(BASE + "/" + scopeId).then().statusCode(200)
                .header("Cache-Control", equalTo("no-store")).header("Vary", containsString("Authorization"))
                .extract().asString());
    }

    private JsonNode toolPayload(JsonNode envelope) throws Exception {
        JsonNode structured = envelope.path("result").path("structuredContent");
        if (structured.has("facts")) return structured;
        for (JsonNode content : envelope.path("result").path("content")) {
            if ("text".equals(content.path("type").asText())) {
                JsonNode parsed = mapper.readTree(content.path("text").asText());
                if (parsed.has("facts")) return parsed;
            }
        }
        throw new AssertionError("MCP configuration observation was not present in the typed tool response");
    }

    private static void assertDenied(JsonNode envelope) {
        assertThat(envelope.path("result").path("isError").asBoolean()).isTrue();
        assertThat(envelope.toString()).doesNotContain(CANARY);
    }

    private McpSession initializeMcp() {
        return initializeMcp("configuration-boundary-synthetic-test");
    }

    private McpSession initializeMcp(String clientName) {
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-11-25","capabilities":{},
                  "clientInfo":{"name":"%s","version":"1"}}}
                """.formatted(clientName);
        String session = given().contentType("application/json").accept("application/json, text/event-stream")
                .body(initialize).post("/mcp").then().statusCode(200).extract().header("Mcp-Session-Id");
        assertThat(session).isNotBlank();
        given().contentType("application/json").accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", session)
                .body("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}")
                .post("/mcp").then().statusCode(anyOf(equalTo(200), equalTo(202), equalTo(204)));
        return new McpSession(session);
    }

    private final class McpSession implements AutoCloseable {
        private final String id;
        private int requestId = 2;

        private McpSession(String id) { this.id = id; }

        private JsonNode call(String tool, String arguments) throws Exception {
            String body = "{\"jsonrpc\":\"2.0\",\"id\":" + requestId++
                    + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                    + "\",\"arguments\":" + arguments + "}}";
            String response = given().contentType("application/json").accept("application/json, text/event-stream")
                    .header("Mcp-Session-Id", id).body(body).post("/mcp")
                    .then().statusCode(200).extract().asString();
            String json = response.startsWith("{") ? response : response.lines()
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim()).findFirst().orElseThrow();
            return mapper.readTree(json);
        }

        @Override public void close() {
            given().header("Mcp-Session-Id", id).delete("/mcp");
        }
    }
}
