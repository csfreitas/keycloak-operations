package io.github.keycloakmcp.security;

import static io.github.keycloakmcp.security.ConfigurationReadFixtureIdentityAugmentor.CLIENT_ATTRIBUTE;
import static io.github.keycloakmcp.security.RegistryPreflightBoundaryTestProfile.CLIENT;
import static io.github.keycloakmcp.security.RegistryPreflightBoundaryTestProfile.CREDENTIAL_REFERENCE;
import static io.github.keycloakmcp.security.RegistryPreflightBoundaryTestProfile.ROLE;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.github.keycloakmcp.target.ConfigurationTargetRegistry;
import io.github.keycloakmcp.target.Target;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.ValidatableResponse;

/**
 * HTTP admission/serialization with trusted test-only JWT augmentation and mocked
 * local registry reads. This does not test JWT signatures, OIDC, live identity,
 * database availability, registration, source connectivity or credential validity.
 */
@QuarkusTest
@TestProfile(RegistryPreflightBoundaryTestProfile.class)
class RegistryPreflightBoundaryTest {
    private static final String PATH = "/api/v1/registry/targets/preflight";
    private static final String CANDIDATE = "registry-http-candidate";
    private static final String SOURCE_URL = "https://source.example.invalid/auth";
    private static final String CANARY = "REGISTRY_HTTP_PRIVATE_CANARY";
    private static final String VALID_DRAFT = """
            {"operation":"CREATE","targetId":"registry-http-candidate",
             "displayName":"REGISTRY_HTTP_PRIVATE_CANARY","productType":"KEYCLOAK","environment":"TEST",
             "keycloak":{"url":"https://source.example.invalid/auth","authRealm":"master",
             "clientId":"source-reader","credentialRef":"lab-a"}}
            """;

    @InjectMock TargetRepository repository;
    @InjectMock ConfigurationTargetRegistry configured;

    @BeforeEach
    void localRegistryFixture() {
        // Any startup bootstrap invocation is outside the request under test.
        reset(repository, configured);
        when(configured.findById(CANDIDATE)).thenReturn(Optional.empty());
        when(repository.existsById(CANDIDATE)).thenReturn(false);
    }

    @Test
    void anonymousMalformedRequestIsDeniedBeforeRegistryReads() {
        post("{").statusCode(anyOf(equalTo(401), equalTo(403)));

        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = "unapproved-client"))
    void approvedSubjectAndRoleCannotUseAnUnapprovedClientEvenWithMalformedBody() {
        assertDeniedBeforeValidation(post("{"));

        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = "unapproved-role", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void approvedSubjectAndClientStillRequireTheExplicitAdministrativeRole() {
        assertDeniedBeforeValidation(post("{"));

        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "different-human", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void matchingRoleAndClientDoNotAuthorizeADifferentSubject() {
        assertDeniedBeforeValidation(post(VALID_DRAFT));

        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = "legacy-admin", augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void legacyTargetAdministrationDoesNotElevateToRegistryPreflight() {
        assertDeniedBeforeValidation(post("{"));

        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE)
    void namedTestPrincipalWithoutTrustedJwtBindingCannotUsePreflight() {
        post(VALID_DRAFT).statusCode(401)
                .body("code", equalTo("AUTHENTICATION_FAILED"))
                .header("Cache-Control", equalTo("no-store"))
                .header("Vary", containsString("Authorization"));

        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void authorizedCandidateIsOnlyLocallyValidWithoutPersistenceOrNetworkClaims() {
        String response = assertNonOperational(post(VALID_DRAFT).statusCode(200))
                .body("contractVersion", equalTo("0.1.0"))
                .body("validation", equalTo("LOCALLY_VALID"))
                .body("issues", hasSize(0))
                .extract().asString();

        assertThat(response).doesNotContain(CANARY, SOURCE_URL, CREDENTIAL_REFERENCE, "source-reader",
                "test-secret-a", "test-secret-b", "subject-admin");
        verify(configured).findById(CANDIDATE);
        verify(repository).existsById(CANDIDATE);
        verifyNoMoreInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void unknownSecretFieldIsRejectedBeforeRegistryReadsAndNeverEchoed() {
        String draft = VALID_DRAFT.stripTrailing();
        draft = draft.substring(0, draft.length() - 1) + ",\"clientSecret\":\"" + CANARY + "\"}";
        String response = assertNonOperational(post(draft).statusCode(400))
                .body("validation", equalTo("REJECTED"))
                .body("issues", hasSize(1))
                .body("issues[0].field", equalTo("body"))
                .body("issues[0].code", equalTo("INVALID_SHAPE"))
                .extract().asString();

        assertThat(response).doesNotContain(CANARY, SOURCE_URL, CREDENTIAL_REFERENCE, "clientSecret");
        verifyNoInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void existingDatabaseTargetIsRejectedWithoutMutation() {
        when(repository.existsById(CANDIDATE)).thenReturn(true);

        assertNonOperational(post(VALID_DRAFT).statusCode(400))
                .body("validation", equalTo("REJECTED"))
                .body("issues", hasSize(1))
                .body("issues[0].field", equalTo("targetId"))
                .body("issues[0].code", equalTo("ALREADY_EXISTS"));

        verify(configured).findById(CANDIDATE);
        verify(repository).existsById(CANDIDATE);
        verifyNoMoreInteractions(configured, repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void configurationOwnedTargetIsNotAdoptedAndDoesNotNeedDatabaseLookup() {
        when(configured.findById(CANDIDATE)).thenReturn(Optional.of(mock(Target.class)));

        assertNonOperational(post(VALID_DRAFT).statusCode(400))
                .body("validation", equalTo("REJECTED"))
                .body("issues[0].code", equalTo("ALREADY_EXISTS"));

        verify(configured).findById(CANDIDATE);
        verifyNoMoreInteractions(configured);
        verifyNoInteractions(repository);
    }

    @Test
    @TestSecurity(user = "admin", roles = ROLE, augmentors = ConfigurationReadFixtureIdentityAugmentor.class,
            attributes = @SecurityAttribute(key = CLIENT_ATTRIBUTE, value = CLIENT))
    void databaseExceptionIsSanitizedAndInconclusiveRatherThanAnAvailableId() {
        when(repository.existsById(CANDIDATE)).thenThrow(new IllegalStateException(
                "jdbc:postgresql://private.example.invalid/ops password=" + CANARY,
                new RuntimeException("CANARY_NESTED_PROVIDER_CAUSE")));

        String response = assertNonOperational(post(VALID_DRAFT).statusCode(503))
                .body("validation", equalTo("INCONCLUSIVE"))
                .body("issues", hasSize(1))
                .body("issues[0].field", equalTo("registry"))
                .body("issues[0].code", equalTo("REGISTRY_UNAVAILABLE"))
                .extract().asString();

        assertThat(response).doesNotContain(CANARY, "CANARY_NESTED_PROVIDER_CAUSE", "jdbc:",
                "private.example.invalid", "password", SOURCE_URL, CREDENTIAL_REFERENCE);
        verify(configured).findById(CANDIDATE);
        verify(repository).existsById(CANDIDATE);
        verifyNoMoreInteractions(configured, repository);
    }

    private static ValidatableResponse post(String json) {
        return given().contentType("application/json").body(json).post(PATH).then();
    }

    private static void assertDeniedBeforeValidation(ValidatableResponse response) {
        String json = response.statusCode(403)
                .body("code", equalTo("AUTHORIZATION_FAILED"))
                .header("Cache-Control", equalTo("no-store"))
                .header("Vary", containsString("Authorization"))
                .extract().asString();
        assertThat(json).doesNotContain("INVALID_JSON", "INVALID_SHAPE", "ALREADY_EXISTS", CANARY,
                SOURCE_URL, CREDENTIAL_REFERENCE);
    }

    private static ValidatableResponse assertNonOperational(ValidatableResponse response) {
        return response.header("Cache-Control", equalTo("no-store"))
                .header("Vary", containsString("Authorization"))
                .body("persisted", equalTo(false))
                .body("registrationAvailable", equalTo(false))
                .body("connectivity", equalTo("NOT_TESTED"))
                .body("credentialValidity", equalTo("NOT_TESTED"))
                .body("destinationApproval", equalTo("NOT_CHECKED"))
                .body("globalReadOnly", equalTo(true));
    }
}
