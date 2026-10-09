package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.ClientRepresentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.domain.change.ChangeRecord;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.change.ClientCreateChangeRequest;
import io.github.keycloakmcp.domain.change.ClientEnabledChangeRequest;
import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.persistence.mapper.ChangePersistenceMapper;
import io.github.keycloakmcp.persistence.repository.ChangeRepository;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Admission must reject unsafe intent, while output projection never rewrites executable state. */
@QuarkusTest
@TestProfile(WriteEnabledTestProfile.class)
class ChangeMetadataBoundaryTest {

    private static final String TARGET = "lab-keycloak-a";
    private static final String REALM = "master";
    private static final String CLIENT = "metadata-client";
    private static final String CANARY = "change-boundary-canary-9281";
    private static final String SECRET_TEXT = "password=" + CANARY;
    private static final String INPUT_ERROR = "Sensitive content is not allowed in change metadata";

    @Inject ChangeManagementService service;
    @Inject ChangeRepository repository;
    @Inject ChangePersistenceMapper mapper;
    @Inject ChangePlanFingerprinter fingerprinter;
    @Inject ObjectMapper objectMapper;
    @InjectMock StableAdminApiAdapter adminApi;
    @InjectMock TargetAuthorizationService authorization;

    private final AtomicReference<ClientRepresentation> liveClient = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        liveClient.set(sampleClient());
        when(authorization.currentActor()).thenReturn("local-lab");
        when(adminApi.findClientByClientId(any(), eq(REALM), eq(CLIENT)))
                .thenAnswer(invocation -> copy(liveClient.get()));
        when(adminApi.clientExists(any(), any(), any())).thenReturn(false);
        doAnswer(invocation -> {
            ClientRepresentation outgoing = invocation.getArgument(2);
            assertThat(outgoing.getSecret()).isNull();
            liveClient.set(copy(outgoing));
            return null;
        }).when(adminApi).updateClient(any(), any(), any());
        clearInvocations(adminApi, authorization);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "description"})
    void genericIntentRejectsRecognizableSecretBeforeProviderOrPersistence(String property) {
        assertInputRejected(() -> service.planClientUpdate(
                TARGET, REALM, CLIENT, Map.of(property, SECRET_TEXT), "ignored", null));
    }

    @Test
    void unsafePropertyNameIsRejectedBeforeValidatorCanEchoIt() {
        assertInputRejected(() -> service.planClientUpdate(
                TARGET, REALM, CLIENT, Map.of(SECRET_TEXT, "value"), null, null));
    }

    @Test
    void createDescriptionIsRejectedBeforeExistenceReadOrPersistence() {
        assertInputRejected(() -> service.planClientCreate(new ClientCreateChangeRequest(
                TARGET, REALM, CLIENT, "Safe name", SECRET_TEXT,
                false, true, true, false, false, "ignored", null)));
    }

    @Test
    void redirectQueryCredentialIsRejectedBeforeProviderOrPersistence() {
        assertInputRejected(() -> service.planClientUrlUpdate(urlRequest(
                List.of("https://app.example/callback?access_token=" + CANARY))));
    }

    @Test
    void malformedCredentialUrlIsRejectedBeforeRawUriError() {
        assertInputRejected(() -> service.planClientUrlUpdate(urlRequest(
                List.of("https://app.example/bad path?password=" + CANARY))));
    }

    @ParameterizedTest
    @EnumSource(PlanKind.class)
    void everyPlanningKindRejectsUnsafeRealmBeforeProviderOrPersistence(PlanKind kind) {
        assertInputRejected(() -> plan(kind, SECRET_TEXT, CLIENT, null));
    }

    @ParameterizedTest
    @EnumSource(PlanKind.class)
    void everyPlanningKindRejectsUnsafeIdempotencyKeyBeforeLookupOrProvider(PlanKind kind) {
        assertInputRejected(() -> plan(kind, REALM, CLIENT, SECRET_TEXT));
    }

    @ParameterizedTest
    @EnumSource(PlanKind.class)
    void everyPlanningKindRejectsUnsafeResourceIdentityBeforeProvider(PlanKind kind) {
        assertInputRejected(() -> plan(kind, REALM, SECRET_TEXT, null));
    }

    @ParameterizedTest
    @EnumSource(PlanKind.class)
    void targetAuthorizationPrecedesMetadataAdmission(PlanKind kind) {
        doThrow(McpException.targetUnauthorized(TARGET))
                .when(authorization).assertAllowed(any(), eq(TargetPermission.PLAN));
        long before = repository.count();

        assertThatThrownBy(() -> plan(kind, SECRET_TEXT, CLIENT, null))
                .isInstanceOf(McpException.class)
                .satisfies(error -> assertThat(((McpException) error).getCode())
                        .isEqualTo(ErrorCode.TARGET_NOT_AUTHORIZED));

        assertThat(repository.count()).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    @Test
    void observedGenericMetadataIsRejectedWithoutPlanOrProviderMutation() {
        liveClient.get().setDescription(SECRET_TEXT);
        assertObservedRejected(() -> service.planClientUpdate(
                TARGET, REALM, CLIENT, Map.of("name", "Safe new name"), null, null));
        assertThat(liveClient.get().getDescription()).isEqualTo(SECRET_TEXT);
        assertThat(liveClient.get().getName()).isEqualTo("Original name");
    }

    @Test
    void observedUrlBaselineIsRejectedWithoutPlanOrProviderMutation() {
        liveClient.get().setRedirectUris(List.of("https://old.example/callback?token=" + CANARY));
        assertObservedRejected(() -> service.planClientUrlUpdate(
                urlRequest(List.of("https://new.example/callback"))));
        assertThat(liveClient.get().getRedirectUris().get(0)).contains(CANARY);
    }

    @Test
    void rawObservedPkceIsCheckedBeforeCaseNormalization() {
        String rawJwt = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJzeW50aGV0aWMifQ.signature";
        liveClient.get().getAttributes().put("pkce.code.challenge.method", rawJwt);
        assertObservedRejected(() -> plan(PlanKind.SECURITY, REALM, CLIENT, null));
        assertThat(liveClient.get().getAttributes()).containsEntry("pkce.code.challenge.method", rawJwt);
    }

    @Test
    void ordinaryOperationalTextAndIgnoredCallerActorRemainUnchanged() {
        String intended = "Review password policy and token lifespan; Bearer authentication is enabled";
        var planned = service.planClientUpdate(
                TARGET, REALM, CLIENT, Map.of("description", intended), SECRET_TEXT, null);

        assertThat(planned.desiredState()).containsEntry("description", intended);
        assertThat(repository.findById(planned.changeId()).desiredState)
                .containsEntry("description", intended);
        assertThat(planned.actor()).isEqualTo("local-lab");
        var applied = service.apply(planned.changeId(), SECRET_TEXT);

        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().getDescription()).isEqualTo(intended);
        assertThat(applied.desiredState()).containsEntry("description", intended);
    }

    @Test
    void knownProviderCredentialsRemainUnchangedOnObservationButAreClearedFromOutboundUpdate() {
        ClientRepresentation observed = liveClient.get();
        String providerSecret = observed.getSecret();
        String registrationToken = "synthetic-registration-access-token-4187";
        observed.setRegistrationAccessToken(registrationToken);

        var planned = safePlan(null);

        assertThat(observed.getSecret()).isEqualTo(providerSecret);
        assertThat(observed.getRegistrationAccessToken()).isEqualTo(registrationToken);
        assertThat(liveClient.get()).isSameAs(observed);
        assertThat(json(planned)).doesNotContain(providerSecret, registrationToken);

        var applied = service.apply(planned.changeId(), null);

        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().getSecret()).isNull();
        assertThat(liveClient.get().getRegistrationAccessToken()).isNull();
        // The provider observation was copied, not edited in place by admission.
        assertThat(observed.getSecret()).isEqualTo(providerSecret);
        assertThat(observed.getRegistrationAccessToken()).isEqualTo(registrationToken);
    }

    @Test
    @Transactional
    void historicalReadProjectionDoesNotRewriteEntityOrFingerprints() {
        var planned = safePlan(null);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        entity.desiredState = Map.of("name", SECRET_TEXT);
        entity.baselineState = Map.of("name", "Authorization: Bearer " + CANARY);
        entity.operationsJson.get(0).put("after", SECRET_TEXT);
        entity.diffJson.get(0).put("after", SECRET_TEXT);
        entity.rejectionReason = SECRET_TEXT;
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        assertSafe(service.getChange(entity.id));
        var page = service.listChanges(Optional.of(TARGET), Optional.empty(), 0, 500);
        assertSafe(page.items().stream().filter(item -> entity.id.equals(item.changeId())).findFirst().orElseThrow());

        assertThat(storedSnapshot(entity)).isEqualTo(before);
        assertThat(entity.planFingerprint).isEqualTo(planned.planFingerprint());
        assertThat(entity.baselineFingerprint).isEqualTo(planned.baselineFingerprint());
        verifyNoInteractions(adminApi);
    }

    @Test
    @Transactional
    void historyPreservesCanonicalCorrelationAndProvenanceApartFromDescriptiveMetadata() {
        var planned = safePlan(null);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        // This is an explicit contract limitation: canonical provenance is retained,
        // not subjected to descriptive redaction, even if its text resembles a credential.
        entity.actor = "https://issuer.example#password=" + CANARY;
        entity.approvedBy = "https://issuer.example#token=" + CANARY;
        entity.rejectedBy = "https://issuer.example#secret=" + CANARY;
        entity.resultMessage = SECRET_TEXT;
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        ChangeRecord projected = service.getChange(entity.id);

        assertThat(projected.changeId()).isEqualTo(entity.id);
        assertThat(projected.targetId()).isEqualTo(entity.targetId);
        assertThat(projected.actor()).isEqualTo(entity.actor);
        assertThat(projected.approvedBy()).isEqualTo(entity.approvedBy);
        assertThat(projected.rejectedBy()).isEqualTo(entity.rejectedBy);
        assertThat(projected.resultMessage()).doesNotContain(CANARY).contains("[REDACTED]");
        assertThat(storedSnapshot(entity)).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    @Test
    @Transactional
    void validIntegrityUnsafeLegacyPlanCannotBeApprovedOrMutated() throws Exception {
        var planned = safePlan(null);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        entity.status = ChangeStatus.WAITING_APPROVAL.name();
        makeLegacyIntentUnsafeWithValidIntegrity(entity);
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        assertReplan(() -> service.approve(entity.id, "ignored"));

        assertThat(storedSnapshot(entity)).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    @Test
    @Transactional
    void validIntegrityUnsafeLegacyPlanCannotBeAppliedOrMutated() throws Exception {
        var planned = safePlan(null);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        makeLegacyIntentUnsafeWithValidIntegrity(entity);
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        assertReplan(() -> service.apply(entity.id, "ignored"));

        assertThat(storedSnapshot(entity)).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    @Test
    @Transactional
    void unsafeLegacyStandaloneVerificationIsBlockedBeforeProviderOrStatusMutation() {
        var planned = safePlan(null);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        entity.desiredState = Map.of("name", SECRET_TEXT);
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        assertReplan(() -> service.verify(entity.id));

        assertThat(storedSnapshot(entity)).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    @Test
    void unsafeLiveMetadataCannotBeResubmittedDuringAnUnrelatedEnabledWrite() {
        var planned = plan(PlanKind.ENABLED, REALM, CLIENT, null);
        service.approve(planned.changeId(), "ignored");
        liveClient.get().setDescription(SECRET_TEXT);
        clearInvocations(adminApi);

        assertReplan(() -> service.apply(planned.changeId(), "ignored"));

        verify(adminApi, org.mockito.Mockito.never()).updateClient(any(), any(), any());
        assertThat(liveClient.get().getDescription()).isEqualTo(SECRET_TEXT);
        assertThat(liveClient.get().isEnabled()).isFalse();
    }

    @Test
    void rejectionReasonIsSanitizedWithoutRewritingDesiredState() {
        var planned = safePlan(null);
        var rejected = service.reject(planned.changeId(), "ignored", "Reason: " + SECRET_TEXT);
        ChangeRecordEntity entity = repository.findById(planned.changeId());

        assertThat(rejected.status()).isEqualTo(ChangeStatus.REJECTED);
        assertSafe(rejected);
        assertThat(entity.rejectionReason).doesNotContain(CANARY).contains("[REDACTED]");
        assertThat(entity.desiredState).isEqualTo(planned.desiredState());
        assertThat(entity.planFingerprint).isEqualTo(planned.planFingerprint());
        assertThat(entity.integrityFingerprint).isNotBlank();
    }

    @Test
    void unsafeReadBackCannotBecomeVerifiedOrPersistRecognizableSecretEvidence() {
        var planned = safePlan(null);
        doAnswer(invocation -> {
            ClientRepresentation actual = copy(invocation.getArgument(2));
            actual.setName(SECRET_TEXT);
            liveClient.set(actual);
            return null;
        }).when(adminApi).updateClient(any(), any(), any());

        assertThatThrownBy(() -> service.apply(planned.changeId(), "ignored"))
                .isInstanceOf(McpException.class)
                .satisfies(error -> assertThat(((McpException) error).getCode())
                        .isEqualTo(ErrorCode.VERIFICATION_FAILED));

        ChangeRecordEntity entity = repository.findById(planned.changeId());
        assertThat(entity.status).isEqualTo(ChangeStatus.FAILED.name());
        assertThat(entity.verificationStatus).isEqualTo("VERIFICATION_FAILED");
        assertThat(json(entity.verificationJson)).doesNotContain(CANARY);
        assertThat(json(service.getChange(entity.id))).doesNotContain(CANARY);
        assertThat(entity.desiredState).isEqualTo(planned.desiredState());
        assertThat(liveClient.get().getName()).isEqualTo(SECRET_TEXT);
    }

    @ParameterizedTest
    @EnumSource(value = ChangeStatus.class, names = {"APPLIED", "VERIFIED"})
    @Transactional
    void terminalApplyReturnsSanitizedHistoryWithoutProviderOrEntityMutation(ChangeStatus status) {
        var planned = safePlan(null);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        entity.status = status.name();
        entity.resultMessage = SECRET_TEXT;
        entity.desiredState = Map.of("name", SECRET_TEXT);
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        assertSafe(service.apply(entity.id, "ignored"));

        assertThat(storedSnapshot(entity)).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    @Test
    @Transactional
    void idempotentPlanRetrySanitizesHistoricalMetadataWithoutRewritingPlan() {
        String key = "metadata-retry-" + UUID.randomUUID();
        var planned = safePlan(key);
        ChangeRecordEntity entity = repository.findById(planned.changeId());
        entity.policyReason = SECRET_TEXT;
        entity.resultMessage = SECRET_TEXT;
        String before = storedSnapshot(entity);
        clearInvocations(adminApi);

        ChangeRecord retry = safePlan(key);

        assertThat(retry.changeId()).isEqualTo(planned.changeId());
        assertSafe(retry);
        assertThat(storedSnapshot(entity)).isEqualTo(before);
        verifyNoInteractions(adminApi);
    }

    private ChangeRecord safePlan(String idempotencyKey) {
        return service.planClientUpdate(
                TARGET, REALM, CLIENT, Map.of("name", "Requested name"), null, idempotencyKey);
    }

    private ChangeRecord plan(PlanKind kind, String realm, String clientId, String idempotencyKey) {
        return switch (kind) {
            case GENERIC -> service.planClientUpdate(
                    TARGET, realm, clientId, Map.of("name", "Requested name"), null, idempotencyKey);
            case URLS -> service.planClientUrlUpdate(new ClientUrlChangeRequest(
                    TARGET, realm, clientId, List.of("https://new.example/callback"), null, null, idempotencyKey));
            case SECURITY -> service.planClientSecurityUpdate(new ClientSecurityChangeRequest(
                    TARGET, realm, clientId, "S256", null, null, null, null, null, null, idempotencyKey));
            case CREATE -> service.planClientCreate(new ClientCreateChangeRequest(
                    TARGET, realm, clientId, "New client", "Client description",
                    false, true, true, false, false, null, idempotencyKey));
            case ENABLED -> service.planClientEnabledUpdate(new ClientEnabledChangeRequest(
                    TARGET, realm, clientId, true, null, idempotencyKey));
        };
    }

    private static ClientUrlChangeRequest urlRequest(List<String> redirectUris) {
        return new ClientUrlChangeRequest(TARGET, REALM, CLIENT, redirectUris, null, null, null);
    }

    private void assertInputRejected(Runnable operation) {
        long before = repository.count();
        assertThatThrownBy(operation::run).isInstanceOf(McpException.class)
                .hasMessage(INPUT_ERROR)
                .satisfies(error -> assertThat(((McpException) error).getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        assertThat(repository.count()).isEqualTo(before);
        verify(authorization).assertAllowed(any(), eq(TargetPermission.PLAN));
        verifyNoInteractions(adminApi);
    }

    private void assertObservedRejected(Runnable operation) {
        long before = repository.count();
        assertReplan(operation);
        assertThat(repository.count()).isEqualTo(before);
        verify(adminApi, org.mockito.Mockito.never()).updateClient(any(), any(), any());
        verify(adminApi, org.mockito.Mockito.never()).createClient(any(), any(), any());
    }

    private static void assertReplan(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(McpException.class)
                .hasMessageContaining("REPLAN_REQUIRED")
                .hasMessageNotContaining(CANARY)
                .satisfies(error -> assertThat(((McpException) error).getCode()).isEqualTo(ErrorCode.CHANGE_CONFLICT));
    }

    private void assertSafe(Object value) {
        assertThat(json(value)).doesNotContain(CANARY).contains("[REDACTED]");
    }

    private String storedSnapshot(ChangeRecordEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("record", mapper.toDomain(entity));
        snapshot.put("integrityFingerprint", entity.integrityFingerprint);
        snapshot.put("targetContextFingerprint", entity.targetContextFingerprint);
        snapshot.put("policyRevision", entity.policyRevision);
        return json(snapshot);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Could not serialize test evidence", e);
        }
    }

    private void makeLegacyIntentUnsafeWithValidIntegrity(ChangeRecordEntity entity) throws Exception {
        entity.desiredState = Map.of("name", SECRET_TEXT);
        entity.operationsJson.get(0).put("after", SECRET_TEXT);
        entity.diffJson.get(0).put("after", SECRET_TEXT);
        entity.planFingerprint = fingerprinter.fingerprintPlan(
                entity.targetId, entity.realm, entity.resourceType, entity.resourceId,
                entity.operation, mapper.toDomain(entity).operations());
        entity.approvalFingerprint = entity.planFingerprint;
        Method integrity = ChangeManagementService.class.getDeclaredMethod("integrityFingerprint", ChangeRecordEntity.class);
        integrity.setAccessible(true);
        entity.integrityFingerprint = (String) integrity.invoke(ClientProxy.unwrap(service), entity);
    }

    private static ClientRepresentation sampleClient() {
        ClientRepresentation client = new ClientRepresentation();
        client.setId("metadata-client-uuid");
        client.setClientId(CLIENT);
        client.setName("Original name");
        client.setDescription("Original description");
        client.setAttributes(new HashMap<>());
        client.setRedirectUris(new ArrayList<>(List.of("https://old.example/callback")));
        client.setWebOrigins(new ArrayList<>(List.of("https://old.example")));
        client.setRootUrl("https://root.example");
        client.setEnabled(false);
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(false);
        client.setStandardFlowEnabled(false);
        client.setImplicitFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(false);
        client.setSecret("provider-managed-secret-not-part-of-metadata-admission");
        return client;
    }

    private static ClientRepresentation copy(ClientRepresentation source) {
        ClientRepresentation copy = new ClientRepresentation();
        copy.setId(source.getId());
        copy.setClientId(source.getClientId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setAttributes(source.getAttributes() == null ? null : new HashMap<>(source.getAttributes()));
        copy.setRedirectUris(source.getRedirectUris() == null ? null : new ArrayList<>(source.getRedirectUris()));
        copy.setWebOrigins(source.getWebOrigins() == null ? null : new ArrayList<>(source.getWebOrigins()));
        copy.setRootUrl(source.getRootUrl());
        copy.setEnabled(source.isEnabled());
        copy.setPublicClient(source.isPublicClient());
        copy.setServiceAccountsEnabled(source.isServiceAccountsEnabled());
        copy.setStandardFlowEnabled(source.isStandardFlowEnabled());
        copy.setImplicitFlowEnabled(source.isImplicitFlowEnabled());
        copy.setDirectAccessGrantsEnabled(source.isDirectAccessGrantsEnabled());
        copy.setSecret(source.getSecret());
        copy.setRegistrationAccessToken(source.getRegistrationAccessToken());
        return copy;
    }

    private enum PlanKind { GENERIC, URLS, SECURITY, CREATE, ENABLED }
}
