package io.github.keycloakmcp.service.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Issue;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Status;
import io.github.keycloakmcp.target.ConfigurationTargetRegistry;
import io.github.keycloakmcp.target.Target;

class RegistryPreflightServiceTest {
    private static final String TARGET_ID = "registry-new";
    private static final String CREDENTIAL_REF = "reference-a";
    private static final String SOURCE_URL = "https://identity.example.invalid/auth";
    private static final String VALID_DRAFT = """
            {"operation":"CREATE","targetId":"registry-new","displayName":"New target","productType":"KEYCLOAK",
             "environment":"TEST","keycloak":{"url":"https://identity.example.invalid/auth",
             "authRealm":"master","clientId":"operations-reader","credentialRef":"reference-a"}}
            """;

    private final RegistryPreflightPolicy policy = mock(RegistryPreflightPolicy.class);
    private final RegistryPreflightValidator validator = spy(new RegistryPreflightValidator());
    private final ConfigurationTargetRegistry configured = mock(ConfigurationTargetRegistry.class);
    private final TargetRepository repository = mock(TargetRepository.class);
    private final McpRuntimeConfig runtime = mock(McpRuntimeConfig.class);
    private final McpRuntimeConfig.CredentialEntry credential = mock(McpRuntimeConfig.CredentialEntry.class);
    private final RegistryPreflightService service = new RegistryPreflightService(
            policy, validator, configured, repository, runtime);

    @BeforeEach
    void knownReferenceAndUnoccupiedTarget() {
        when(runtime.credentials()).thenReturn(Map.of(CREDENTIAL_REF, credential));
        when(runtime.readOnly()).thenReturn(true);
        when(configured.findById(TARGET_ID)).thenReturn(Optional.empty());
        when(repository.existsById(TARGET_ID)).thenReturn(false);
    }

    @Test
    void authorizationPrecedesBodyParsingConfigurationAndDatabaseAccess() {
        McpException denied = McpException.authorizationFailed("Registry preflight is not authorized");
        doThrow(denied).when(policy).assertAllowed();
        InputStream body = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Unauthorized request body must not be read");
            }
        };

        assertThatThrownBy(() -> service.preflight(body)).isSameAs(denied);

        verify(policy).assertAllowed();
        verifyNoInteractions(validator, configured, repository, runtime, credential);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "{}"})
    void malformedDraftsNeverReachTheRegistryOrCredentialReferences(String json) {
        RegistryPreflightResult result = service.preflight(body(json));

        assertThat(result.validation()).isEqualTo(Status.REJECTED);
        assertThat(result.issues()).isNotEmpty();
        assertNonOperational(result, true);
        verify(policy).assertAllowed();
        verifyNoInteractions(configured, repository, credential);
        verify(runtime, never()).credentials();
    }

    @Test
    void localValidityDoesNotMeanCredentialsDestinationOrRegistrationWereApproved() {
        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.contractVersion()).isEqualTo("0.1.0");
        assertThat(result.validation()).isEqualTo(Status.LOCALLY_VALID);
        assertThat(result.issues()).isEmpty();
        assertNonOperational(result, true);
        verify(policy).assertAllowed();
        verify(configured).findById(TARGET_ID);
        verify(repository).existsById(TARGET_ID);
        verifyNoMoreInteractions(configured, repository);
        verifyNoInteractions(credential);
        assertThat(result.toString()).doesNotContain(SOURCE_URL, CREDENTIAL_REF, "operations-reader");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void readOnlyStateIsReportedWithoutCreatingAnyMutationCapability(boolean readOnly) {
        when(runtime.readOnly()).thenReturn(readOnly);

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.LOCALLY_VALID);
        assertNonOperational(result, readOnly);
        verify(repository).existsById(TARGET_ID);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(credential);
    }

    @Test
    void unknownCredentialReferenceIsNotMistakenForAUsableConnection() {
        when(runtime.credentials()).thenReturn(Map.of());

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.REJECTED);
        assertThat(result.issues()).contains(new Issue("keycloak.credentialRef", "UNKNOWN_CREDENTIAL_REFERENCE"));
        assertNonOperational(result, true);
        assertThat(result.toString()).doesNotContain(CREDENTIAL_REF, SOURCE_URL);
        verifyNoInteractions(credential);
    }

    @Test
    void checkingReferenceExistenceNeverReadsAnyCredentialValue() {
        when(credential.clientSecret()).thenThrow(new AssertionError("Secret value must never be resolved"));
        when(credential.token()).thenThrow(new AssertionError("Token value must never be resolved"));
        when(credential.kubeconfig()).thenThrow(new AssertionError("Credential files must never be opened"));

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.LOCALLY_VALID);
        verifyNoInteractions(credential);
    }

    @Test
    void configurationOwnedIdCollisionShortCircuitsDatabaseLookup() {
        when(configured.findById(TARGET_ID)).thenReturn(Optional.of(mock(Target.class)));

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.REJECTED);
        assertThat(result.issues()).contains(new Issue("targetId", "ALREADY_EXISTS"));
        assertNonOperational(result, true);
        verifyNoInteractions(repository, credential);
    }

    @Test
    void databaseIdCollisionDoesNotAdoptOrOverwriteTheRecord() {
        when(repository.existsById(TARGET_ID)).thenReturn(true);

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.REJECTED);
        assertThat(result.issues()).contains(new Issue("targetId", "ALREADY_EXISTS"));
        assertNonOperational(result, true);
        verify(repository).existsById(TARGET_ID);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(credential);
    }

    @Test
    void databaseFailureIsInconclusiveAndDoesNotExposeProviderDetails() {
        when(repository.existsById(TARGET_ID)).thenThrow(new IllegalStateException(
                "jdbc:postgresql://private.invalid/ops password=CANARY_DATABASE_SECRET",
                new RuntimeException("CANARY_NESTED_DATABASE_CAUSE")));

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.INCONCLUSIVE);
        assertThat(result.issues()).contains(new Issue("registry", "REGISTRY_UNAVAILABLE"));
        assertNonOperational(result, true);
        assertThat(result.toString()).doesNotContain("jdbc:", "private.invalid", "CANARY", "password");
        verify(repository).existsById(TARGET_ID);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(credential);
    }

    @Test
    void configurationLookupFailureDoesNotBecomeAnAvailableTargetId() {
        when(configured.findById(TARGET_ID)).thenThrow(new IllegalStateException("CANARY_CONFIG_DETAILS"));

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.INCONCLUSIVE);
        assertThat(result.issues()).contains(new Issue("registry", "REGISTRY_UNAVAILABLE"));
        assertNonOperational(result, true);
        assertThat(result.toString()).doesNotContain("CANARY_CONFIG_DETAILS");
        verifyNoInteractions(repository, credential);
    }

    @Test
    void repeatedPreflightIsDeterministicAndPerformsOnlyFreshReadQueries() {
        RegistryPreflightResult first = service.preflight(body(VALID_DRAFT));
        RegistryPreflightResult second = service.preflight(body(VALID_DRAFT));

        assertThat(first).isEqualTo(second);
        verify(policy, times(2)).assertAllowed();
        verify(configured, times(2)).findById(TARGET_ID);
        verify(repository, times(2)).existsById(TARGET_ID);
        verifyNoMoreInteractions(configured, repository);
        verifyNoInteractions(credential);
    }

    @Test
    void aPreviousLocallyValidResultDoesNotBypassNewCollisionChecks() {
        assertThat(service.preflight(body(VALID_DRAFT)).validation()).isEqualTo(Status.LOCALLY_VALID);
        when(repository.existsById(TARGET_ID)).thenReturn(true);

        RegistryPreflightResult result = service.preflight(body(VALID_DRAFT));

        assertThat(result.validation()).isEqualTo(Status.REJECTED);
        assertThat(result.issues()).contains(new Issue("targetId", "ALREADY_EXISTS"));
        assertNonOperational(result, true);
        verify(policy, times(2)).assertAllowed();
        verify(repository, times(2)).existsById(TARGET_ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void rejectedUnknownFieldsAreNotPersistedOrEchoed() {
        String draft = VALID_DRAFT.stripTrailing();
        draft = draft.substring(0, draft.length() - 1) + ",\"clientSecret\":\"CANARY_REQUEST_SECRET\"}";

        RegistryPreflightResult result = service.preflight(body(draft));

        assertThat(result.validation()).isEqualTo(Status.REJECTED);
        assertNonOperational(result, true);
        assertThat(result.toString()).doesNotContain("CANARY_REQUEST_SECRET", SOURCE_URL, CREDENTIAL_REF);
        verifyNoInteractions(configured, repository, credential);
        verify(runtime, never()).credentials();
        verify(repository, never()).existsById(anyString());
    }

    private static void assertNonOperational(RegistryPreflightResult result, boolean readOnly) {
        assertThat(result.persisted()).isFalse();
        assertThat(result.registrationAvailable()).isFalse();
        assertThat(result.connectivity()).isEqualTo("NOT_TESTED");
        assertThat(result.credentialValidity()).isEqualTo("NOT_TESTED");
        assertThat(result.destinationApproval()).isEqualTo("NOT_CHECKED");
        assertThat(result.globalReadOnly()).isEqualTo(readOnly);
    }

    private static InputStream body(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
