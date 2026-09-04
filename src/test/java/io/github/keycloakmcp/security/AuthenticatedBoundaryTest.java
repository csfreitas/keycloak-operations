package io.github.keycloakmcp.security;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AuthenticatedBoundaryTestProfile.class)
class AuthenticatedBoundaryTest {
    @Inject
    ObjectMapper mapper;

    @Test
    void anonymousCannotAccessRestMcpOrSse() {
        given().get("/api/v1/targets").then().statusCode(anyOf(equalTo(401), equalTo(403)));
        given().get("/api/v1/events").then().statusCode(anyOf(equalTo(401), equalTo(403)));
        given().contentType("application/json").body("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}")
                .post("/mcp").then().statusCode(anyOf(equalTo(401), equalTo(403)));
        given().get("/mcp/sse").then().statusCode(anyOf(equalTo(401), equalTo(403)));
    }

    @Test
    @TestSecurity(user = "reader", roles = "reader-a")
    void targetAndFleetListsExcludeUngrantableTargets() {
        given().get("/api/v1/targets").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].id", equalTo("lab-keycloak-a"));
        given().get("/api/v1/fleet").then().statusCode(200)
                .body("$", hasSize(1)).body("[0].targetId", equalTo("lab-keycloak-a"));
        given().get("/api/v1/targets/lab-keycloak-a").then().statusCode(200);
        given().get("/api/v1/targets/lab-keycloak-b").then().statusCode(403);
        given().get("/api/v1/audit?targetId=lab-keycloak-b").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader", roles = "reader-a")
    void readRoleCannotTriggerAssessmentOrReportCollection() {
        given().contentType("application/json").body("{}")
                .post("/api/v1/targets/lab-keycloak-a/operations-reports").then().statusCode(403);
        given().contentType("application/json").body("{}")
                .post("/api/v1/targets/lab-keycloak-a/assessments").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "unmapped", roles = "not-configured")
    void unconfiguredRoleDoesNotDiscoverAnyTargets() {
        given().get("/api/v1/targets").then().statusCode(200).body("$", hasSize(0));
        given().get("/api/v1/fleet").then().statusCode(200).body("$", hasSize(0));
        given().get("/api/v1/audit").then().statusCode(200).body("items", hasSize(0));
    }

    @Test
    @TestSecurity(user = "reader", roles = "reader-a")
    void mcpDiscoveryUsesTheSameTargetGrantAsRest() throws Exception {
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-11-25","capabilities":{},
                  "clientInfo":{"name":"authorization-test","version":"1"}}}
                """;
        var response = given().contentType("application/json").accept("application/json, text/event-stream")
                .body(initialize).post("/mcp").then().statusCode(200).extract().response();
        String session = response.header("Mcp-Session-Id");
        assertThat(session).isNotBlank();
        given().contentType("application/json").accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", session)
                .body("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}")
                .post("/mcp").then().statusCode(anyOf(equalTo(200), equalTo(202), equalTo(204)));

        JsonNode listed = callTool(session, "keycloak_list_targets", "{}");
        assertThat(listed.path("result").path("isError").asBoolean()).isFalse();
        assertThat(listed.toString()).contains("lab-keycloak-a").doesNotContain("lab-keycloak-b");
        JsonNode found = callTool(session, "keycloak_find_targets", "{\"product\":\"KEYCLOAK\"}");
        assertThat(found.toString()).contains("lab-keycloak-a").doesNotContain("lab-keycloak-b");
        JsonNode denied = callTool(session, "keycloak_get_target", "{\"targetId\":\"lab-keycloak-b\"}");
        assertThat(denied.path("result").path("isError").asBoolean()).isTrue();
        assertThat(denied.toString()).contains("TARGET_NOT_AUTHORIZED");
        given().header("Mcp-Session-Id", session).delete("/mcp");
    }

    private JsonNode callTool(String session, String tool, String arguments) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\""
                + tool + "\",\"arguments\":" + arguments + "}}";
        String response = given().contentType("application/json").accept("application/json, text/event-stream")
                .header("Mcp-Session-Id", session).body(body).post("/mcp")
                .then().statusCode(200).extract().asString();
        String json = response.startsWith("{") ? response : response.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).trim()).findFirst().orElseThrow();
        return mapper.readTree(json);
    }
}
