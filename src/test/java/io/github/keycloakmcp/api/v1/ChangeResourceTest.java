package io.github.keycloakmcp.api.v1;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ChangeResourceTest {

    @Test
    void listChangesReturnsPage() {
        given()
                .when().get("/api/v1/changes")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("items", notNullValue())
                .body("page", equalTo(0))
                .body("total", greaterThanOrEqualTo(0));
    }

    @Test
    void getUnknownChangeReturns404() {
        given()
                .when().get("/api/v1/changes/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("CHANGE_NOT_FOUND"));
    }
}
