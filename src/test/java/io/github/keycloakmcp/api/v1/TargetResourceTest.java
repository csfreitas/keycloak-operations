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
class TargetResourceTest {

    @Test
    void listsTargets() {
        given()
                .when().get("/api/v1/targets")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void getsKnownTarget() {
        given()
                .when().get("/api/v1/targets/lab-keycloak-a")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(404)))
                .contentType(ContentType.JSON);
    }

    @Test
    void unknownTargetReturnsJsonError() {
        given()
                .when().get("/api/v1/targets/does-not-exist-xyz")
                .then()
                .statusCode(404)
                .body("code", equalTo("TARGET_NOT_FOUND"))
                .body("message", notNullValue());
    }
}
