package io.github.keycloakmcp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class MeResourceTest {

    @Test
    void returnsLabIdentityWhenOidcDisabled() {
        given()
                .when().get("/api/v1/me")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("authenticated", equalTo(true))
                .body("authMode", equalTo("OPEN_LAB"))
                .body("subject", notNullValue());
    }
}

@QuarkusTest
class FleetResourceEnrichedTest {

    @Test
    void fleetIncludesUiFields() {
        given()
                .when().get("/api/v1/fleet")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThanOrEqualTo(0));
        // When targets exist, metricsConfigured and healthStatus are present
        var response = given().when().get("/api/v1/fleet").then().statusCode(200).extract().jsonPath();
        var size = response.getList("$").size();
        if (size > 0) {
            given()
                    .when().get("/api/v1/fleet")
                    .then()
                    .body("[0].targetId", notNullValue())
                    .body("[0].healthStatus", notNullValue())
                    .body("[0].metricsConfigured", anyOf(equalTo(true), equalTo(false)));
        }
    }
}
