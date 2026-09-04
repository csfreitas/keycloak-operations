package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.domain.change.ChangeStatus;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ClientCreateChangeRequest;
import io.github.keycloakmcp.domain.change.ClientEnabledChangeRequest;
import io.github.keycloakmcp.domain.change.ClientSecurityChangeRequest;
import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.github.keycloakmcp.persistence.repository.ChangeRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
@TestProfile(WriteEnabledTestProfile.class)
class ChangeManagementServiceTest {

    private static final String TARGET_A = "lab-keycloak-a";
    private static final String TARGET_B = "lab-keycloak-b";
    private static final String REALM = "master";
    private static final String CLIENT = "account";
    private static final String NEW_CLIENT = "slice3-client";

    @Inject
    ChangeManagementService changeManagementService;

    @Inject
    ChangeRepository changeRepository;

    @InjectMock
    StableAdminApiAdapter adminApi;

    private final AtomicReference<ClientRepresentation> liveClient = new AtomicReference<>();
    private final AtomicReference<ClientRepresentation> createdClient = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        liveClient.set(sampleClient("Account", "Account console"));
        createdClient.set(null);
        when(adminApi.findClientByClientId(any(), eq(REALM), eq(CLIENT)))
                .thenAnswer(inv -> copy(liveClient.get()));
        doAnswer(inv -> {
            ClientRepresentation updated = inv.getArgument(2);
            assertThat(updated.getSecret()).isNull();
            liveClient.set(copy(updated));
            return null;
        }).when(adminApi).updateClient(any(), eq(REALM), any());
        when(adminApi.clientExists(any(), eq(REALM), eq(NEW_CLIENT)))
                .thenAnswer(inv -> createdClient.get() != null);
        when(adminApi.findClientByClientId(any(), eq(REALM), eq(NEW_CLIENT)))
                .thenAnswer(inv -> copy(createdClient.get()));
        doAnswer(inv -> {
            ClientRepresentation created = copy(inv.getArgument(2));
            assertThat(created.getSecret()).isNull();
            created.setId("created-client-uuid");
            createdClient.set(created);
            return null;
        }).when(adminApi).createClient(any(), eq(REALM), any());
    }

    @Test
    void planApproveApplyVerifyHappyPathOnDev() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Account Updated"), "planner", null);

        assertThat(planned.status()).isEqualTo(ChangeStatus.APPROVED); // DEV + LOW
        assertThat(planned.requiresApproval()).isFalse();
        assertThat(planned.diff()).isNotEmpty();
        assertThat(planned.planFingerprint()).isNotBlank();

        var applied = changeManagementService.apply(planned.changeId(), "applier");
        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().getName()).isEqualTo("Account Updated");
        assertThat(applied.verificationStatus()).isEqualTo("VERIFIED");
    }

    @Test
    void applyWithoutApprovalDeniedOnPrd() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("description", "prd-desc"), "planner", null);
        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);
        assertThat(planned.requiresApproval()).isTrue();

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_NOT_APPROVED));
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    void approveThenApplyOnPrd() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Prd Name"), "planner", null);
        var approved = changeManagementService.approve(planned.changeId(), "approver-1");
        assertThat(approved.status()).isEqualTo(ChangeStatus.APPROVED);
        assertThat(approved.approvalFingerprint()).isEqualTo(approved.planFingerprint());

        var applied = changeManagementService.apply(planned.changeId(), "applier");
        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
    }

    @Test
    @Transactional
    void modifiedPlanInvalidatesApproval() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "X"), "planner", null);
        changeManagementService.approve(planned.changeId(), "approver");
        ChangeRecordEntity entity = changeRepository.findById(planned.changeId());
        entity.planFingerprint = entity.planFingerprint + "-tampered";

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.APPROVAL_INVALID));
    }

    @Test
    void rejectPreventsApply() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "RejectMe"), "planner", null);
        changeManagementService.reject(planned.changeId(), "rejector", "nope");
        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.POLICY_DENIED));
    }

    @Test
    void staleBaselineRequiresReplan() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Stale"), "planner", null);
        ClientRepresentation mutated = copy(liveClient.get());
        mutated.setName("Externally Changed");
        liveClient.set(mutated);

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> {
                    assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_CONFLICT);
                    assertThat(ex.getMessage()).contains("REPLAN_REQUIRED");
                });
    }

    @Test
    void idempotentPlanReturnsSameChange() {
        String key = "idem-" + System.nanoTime();
        var first = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("description", "idem"), "a", key);
        var second = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("description", "idem"), "a", key);
        assertThat(second.changeId()).isEqualTo(first.changeId());
    }

    @Test
    void idempotencyKeyCannotBeReusedWithDifferentIntentOrResource() {
        String key = "intent-" + System.nanoTime();
        changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("description", "original"), "a", key);
        assertThatThrownBy(() -> changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("description", "different"), "a", key))
                .isInstanceOf(McpException.class).hasMessageContaining("different change request");
        assertThatThrownBy(() -> changeManagementService.planClientUpdate(
                TARGET_A, "another-realm", CLIENT, Map.of("description", "original"), "a", key))
                .isInstanceOf(McpException.class).hasMessageContaining("different change request");
        assertThatThrownBy(() -> changeManagementService.planClientUpdate(
                TARGET_A, REALM, "other-client", Map.of("description", "original"), "a", key))
                .isInstanceOf(McpException.class).hasMessageContaining("different change request");
    }

    @Test
    void idempotencyUsesNormalizedUrlSets() {
        String key = "urls-" + System.nanoTime();
        var first = changeManagementService.planClientUrlUpdate(new ClientUrlChangeRequest(
                TARGET_A, REALM, CLIENT, List.of("https://b.example/cb", "https://a.example/cb"), null, "a", key));
        var retry = changeManagementService.planClientUrlUpdate(new ClientUrlChangeRequest(
                TARGET_A, REALM, CLIENT,
                List.of("https://a.example/cb", "https://b.example/cb", "https://a.example/cb"), null, "spoof", key));
        assertThat(retry.changeId()).isEqualTo(first.changeId());
        assertThatThrownBy(() -> changeManagementService.planClientUrlUpdate(new ClientUrlChangeRequest(
                TARGET_A, REALM, CLIENT, List.of("https://c.example/cb"), null, "a", key)))
                .isInstanceOf(McpException.class).hasMessageContaining("different change request");
    }

    @Test
    void createIdempotencyNormalizesDefaultsAndRejectsOperationReuse() {
        String key = "create-" + System.nanoTime();
        var first = changeManagementService.planClientCreate(new ClientCreateChangeRequest(
                TARGET_A, REALM, NEW_CLIENT, null, null, null, null, null, null, null, "a", key));
        var retry = changeManagementService.planClientCreate(new ClientCreateChangeRequest(
                TARGET_A, REALM, NEW_CLIENT, null, null, false, true, true, false, false, "b", key));
        assertThat(retry.changeId()).isEqualTo(first.changeId());
        assertThatThrownBy(() -> changeManagementService.planClientEnabledUpdate(new ClientEnabledChangeRequest(
                TARGET_A, REALM, NEW_CLIENT, true, "a", key)))
                .isInstanceOf(McpException.class).hasMessageContaining("different change request");
    }

    @Test
    void securityIdempotencyRejectsChangingTheRequestedSetting() {
        String key = "security-" + System.nanoTime();
        var first = changeManagementService.planClientSecurityUpdate(new ClientSecurityChangeRequest(
                TARGET_A, REALM, CLIENT, "S256", null, null, null, null, null, "a", key));
        var retry = changeManagementService.planClientSecurityUpdate(new ClientSecurityChangeRequest(
                TARGET_A, REALM, CLIENT, "S256", null, null, null, null, null, "b", key));
        assertThat(retry.changeId()).isEqualTo(first.changeId());
        assertThatThrownBy(() -> changeManagementService.planClientSecurityUpdate(new ClientSecurityChangeRequest(
                TARGET_A, REALM, CLIENT, "NONE", null, null, null, null, null, "a", key)))
                .isInstanceOf(McpException.class).hasMessageContaining("different change request");
    }

    @Test
    void provenanceDoesNotTrustCallerSuppliedActorOrApprover() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Provenance"), "administrator", null);
        assertThat(planned.actor()).isEqualTo("local-lab");
        var approved = changeManagementService.approve(planned.changeId(), "fake-human-approver");
        assertThat(approved.approvedBy()).isEqualTo("local-lab");
        var applied = changeManagementService.apply(planned.changeId(), "fake-executor");
        assertThat(applied.resultMessage()).isEqualTo("Applied by local-lab");
    }

    @Test
    @Transactional
    void mutatedDesiredStateCannotBeApproved() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Reviewed"), "a", null);
        changeRepository.findById(planned.changeId()).desiredState = Map.of("name", "Unreviewed");
        assertThatThrownBy(() -> changeManagementService.approve(planned.changeId(), "a"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.APPROVAL_INVALID));
    }

    @Test
    @Transactional
    void mutatedOperationsCannotReuseApproval() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Reviewed"), "a", null);
        changeManagementService.approve(planned.changeId(), "a");
        changeRepository.findById(planned.changeId()).operationsJson.get(0).put("after", "Unreviewed");
        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "a"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.APPROVAL_INVALID));
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    @Transactional
    void historicalPendingPlansRemainReadableButRequireReplanning() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Historical"), "a", null);
        changeRepository.findById(planned.changeId()).policyRevision = null;
        assertThat(changeManagementService.getChange(planned.changeId()).changeId()).isEqualTo(planned.changeId());
        assertThatThrownBy(() -> changeManagementService.approve(planned.changeId(), "a"))
                .isInstanceOf(McpException.class).hasMessageContaining("REPLAN_REQUIRED");
        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "a"))
                .isInstanceOf(McpException.class).hasMessageContaining("REPLAN_REQUIRED");
    }

    @Test
    @Transactional
    void changedTargetContextInvalidatesApprovedPlan() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "Context"), "a", null);
        changeManagementService.approve(planned.changeId(), "a");
        changeRepository.findById(planned.changeId()).targetContextFingerprint = "different-target-context";
        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "a"))
                .isInstanceOf(McpException.class).hasMessageContaining("REPLAN_REQUIRED");
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    void concurrentAppliesOfTheSamePlanOnlyWriteOnce() throws Exception {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Concurrent"), "a", null);
        CountDownLatch firstWrite = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        doAnswer(inv -> {
            firstWrite.countDown();
            if (!releaseWrite.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test did not release first write");
            }
            liveClient.set(copy(inv.getArgument(2)));
            return null;
        }).when(adminApi).updateClient(any(), eq(REALM), any());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> changeManagementService.apply(planned.changeId(), "a"));
            try {
                assertThat(firstWrite.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return changeManagementService.apply(planned.changeId(), "b");
                });
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                releaseWrite.countDown();
                assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(ChangeStatus.VERIFIED);
                assertThat(second.get(10, TimeUnit.SECONDS).status()).isEqualTo(ChangeStatus.VERIFIED);
            } finally {
                releaseWrite.countDown();
            }
        }
        verify(adminApi, times(1)).updateClient(any(), eq(REALM), any());
    }

    @Test
    void repeatedApplyIsIdempotent() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "Once"), "planner", null);
        var first = changeManagementService.apply(planned.changeId(), "a");
        var second = changeManagementService.apply(planned.changeId(), "a");
        assertThat(first.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(second.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(second.changeId()).isEqualTo(first.changeId());
    }

    @Test
    void targetIsolationListFilter() {
        var a = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "IsoA"), "a", null);
        changeManagementService.planClientUpdate(
                TARGET_B, REALM, CLIENT, Map.of("name", "IsoB"), "b", null);

        var onlyA = changeManagementService.listChanges(Optional.of(TARGET_A), Optional.empty(), 0, 50);
        assertThat(onlyA.items()).allMatch(c -> TARGET_A.equals(c.targetId()));
        assertThat(onlyA.items().stream().map(c -> c.changeId())).contains(a.changeId());
    }

    @Test
    void secretNeverStoredInPlan() {
        assertThatThrownBy(() -> changeManagementService.planClientUpdate(
                        TARGET_A, REALM, CLIENT, Map.of("clientSecret", "leak"), "a", null))
                .isInstanceOf(McpException.class);
    }

    @Test
    void verificationFailureWhenReadBackMismatches() {
        var planned = changeManagementService.planClientUpdate(
                TARGET_A, REALM, CLIENT, Map.of("name", "ShouldVerify"), "planner", null);
        doAnswer(inv -> null).when(adminApi).updateClient(any(), eq(REALM), any());

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.VERIFICATION_FAILED));
    }

    @Test
    void planApproveApplyVerifyClientUrlSets() {
        var planned = changeManagementService.planClientUrlUpdate(urlRequest(
                TARGET_A,
                List.of("https://new.example/callback", "https://new.example/callback"),
                null));

        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);
        assertThat(planned.risk()).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(planned.desiredState().get("redirectUris"))
                .isEqualTo(List.of("https://new.example/callback"));
        assertThat(planned.diff()).hasSize(2);

        changeManagementService.approve(planned.changeId(), "approver");
        var applied = changeManagementService.apply(planned.changeId(), "applier");

        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().getRedirectUris()).containsExactly("https://new.example/callback");
        assertThat(liveClient.get().getWebOrigins()).containsExactly("https://old.example");
        assertThat(liveClient.get().getRootUrl()).isEqualTo("https://preserved.example");
    }

    @Test
    void unsafeClientUrlAdditionDeniedByProductionPolicy() {
        assertThatThrownBy(() -> changeManagementService.planClientUrlUpdate(urlRequest(
                        TARGET_B,
                        List.of("http://external.example/callback"),
                        null)))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.POLICY_DENIED));
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    void staleClientUrlBaselineRequiresReplan() {
        var planned = changeManagementService.planClientUrlUpdate(urlRequest(
                TARGET_A,
                List.of("https://new.example/callback"),
                null));
        changeManagementService.approve(planned.changeId(), "approver");
        ClientRepresentation changed = copy(liveClient.get());
        changed.setRedirectUris(List.of("https://external.example/callback"));
        liveClient.set(changed);

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_CONFLICT));
        assertThat(changeManagementService.getChange(planned.changeId()).status()).isEqualTo(ChangeStatus.FAILED);
    }

    @Test
    void clientUrlPlanIsIdempotentPerTarget() {
        String key = "client-urls-" + System.nanoTime();
        ClientUrlChangeRequest request = new ClientUrlChangeRequest(
                TARGET_A,
                REALM,
                CLIENT,
                List.of("https://new.example/callback"),
                null,
                "planner",
                key);

        var first = changeManagementService.planClientUrlUpdate(request);
        var second = changeManagementService.planClientUrlUpdate(request);

        assertThat(second.changeId()).isEqualTo(first.changeId());
    }

    @Test
    void clientUrlVerificationFailureIsPersisted() {
        var planned = changeManagementService.planClientUrlUpdate(urlRequest(
                TARGET_A,
                List.of("https://new.example/callback"),
                null));
        changeManagementService.approve(planned.changeId(), "approver");
        doAnswer(inv -> null).when(adminApi).updateClient(any(), eq(REALM), any());

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.VERIFICATION_FAILED));
        assertThat(changeManagementService.getChange(planned.changeId()).status()).isEqualTo(ChangeStatus.FAILED);
    }

    @Test
    void clientUrlAdapterFailureIsPersistedWithoutLeakingCause() {
        var planned = changeManagementService.planClientUrlUpdate(urlRequest(
                TARGET_A,
                List.of("https://new.example/callback"),
                null));
        changeManagementService.approve(planned.changeId(), "approver");
        doAnswer(inv -> {
            throw new IllegalStateException("sensitive adapter detail");
        }).when(adminApi).updateClient(any(), eq(REALM), any());

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .hasMessage("Change apply failed");
        var failed = changeManagementService.getChange(planned.changeId());
        assertThat(failed.status()).isEqualTo(ChangeStatus.FAILED);
        assertThat(failed.resultMessage()).doesNotContain("sensitive adapter detail");
    }

    @Test
    void planApproveApplyVerifyClientSecuritySettings() {
        var planned = changeManagementService.planClientSecurityUpdate(securityRequest(
                TARGET_A, "S256", true, null, null, null, null));

        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);
        assertThat(planned.risk()).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(planned.desiredState())
                .containsEntry("pkceCodeChallengeMethod", "S256")
                .containsEntry("standardFlowEnabled", true);

        changeManagementService.approve(planned.changeId(), "approver");
        var applied = changeManagementService.apply(planned.changeId(), "applier");

        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().getAttributes())
                .containsEntry("pkce.code.challenge.method", "S256");
        assertThat(liveClient.get().isStandardFlowEnabled()).isTrue();
        assertThat(liveClient.get().getRedirectUris()).containsExactly("https://old.example/callback");
        assertThat(liveClient.get().getSecret()).isNull();
    }

    @Test
    void unsafeClientSecurityWeakeningDeniedByProductionPolicy() {
        assertThatThrownBy(() -> changeManagementService.planClientSecurityUpdate(securityRequest(
                        TARGET_B, null, null, true, null, null, null)))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.POLICY_DENIED));
        verify(adminApi, never()).updateClient(any(), any(), any());
    }

    @Test
    void staleClientSecurityBaselineRequiresReplan() {
        var planned = changeManagementService.planClientSecurityUpdate(securityRequest(
                TARGET_A, null, true, null, null, null, null));
        changeManagementService.approve(planned.changeId(), "approver");
        ClientRepresentation changed = copy(liveClient.get());
        changed.setStandardFlowEnabled(true);
        liveClient.set(changed);

        assertThatThrownBy(() -> changeManagementService.apply(planned.changeId(), "applier"))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_CONFLICT));
        assertThat(changeManagementService.getChange(planned.changeId()).status()).isEqualTo(ChangeStatus.FAILED);
    }

    @Test
    void planApproveCreateAndVerifySecretFreeClient() {
        var planned = changeManagementService.planClientCreate(new ClientCreateChangeRequest(
                TARGET_A,
                REALM,
                NEW_CLIENT,
                "Slice 3",
                "Lifecycle client",
                null,
                null,
                null,
                null,
                null,
                "planner",
                null));

        assertThat(planned.operation()).isEqualTo(ChangeOperationType.CREATE);
        assertThat(planned.risk()).isEqualTo(ChangeRisk.HIGH);
        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);
        assertThat(planned.desiredState()).containsEntry("enabled", false).doesNotContainKey("secret");

        changeManagementService.approve(planned.changeId(), "approver");
        var applied = changeManagementService.apply(planned.changeId(), "applier");

        assertThat(applied.status()).isEqualTo(ChangeStatus.VERIFIED);
        assertThat(createdClient.get().getClientId()).isEqualTo(NEW_CLIENT);
        assertThat(createdClient.get().getSecret()).isNull();
    }

    @Test
    void createPlanRejectsExistingClient() {
        createdClient.set(sampleClient("Existing", "Existing"));
        assertThatThrownBy(() -> changeManagementService.planClientCreate(new ClientCreateChangeRequest(
                        TARGET_A, REALM, NEW_CLIENT, null, null, null, null, null, null, null, "planner", null)))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.CHANGE_CONFLICT));
        verify(adminApi, never()).createClient(any(), any(), any());
    }

    @Test
    void productionRejectsCreateWithDirectAccessGrants() {
        assertThatThrownBy(() -> changeManagementService.planClientCreate(new ClientCreateChangeRequest(
                        TARGET_B,
                        REALM,
                        NEW_CLIENT,
                        "Unsafe",
                        null,
                        false,
                        true,
                        true,
                        true,
                        false,
                        "planner",
                        null)))
                .isInstanceOf(McpException.class)
                .satisfies(ex -> assertThat(((McpException) ex).getCode()).isEqualTo(ErrorCode.POLICY_DENIED));
        verify(adminApi, never()).createClient(any(), any(), any());
    }

    @Test
    void planApproveDisableAndVerifyExistingClient() {
        ClientRepresentation enabled = copy(liveClient.get());
        enabled.setEnabled(true);
        liveClient.set(enabled);
        var planned = changeManagementService.planClientEnabledUpdate(new ClientEnabledChangeRequest(
                TARGET_A, REALM, CLIENT, false, "planner", null));

        assertThat(planned.risk()).isEqualTo(ChangeRisk.MEDIUM);
        assertThat(planned.status()).isEqualTo(ChangeStatus.WAITING_APPROVAL);
        changeManagementService.approve(planned.changeId(), "approver");

        assertThat(changeManagementService.apply(planned.changeId(), "applier").status())
                .isEqualTo(ChangeStatus.VERIFIED);
        assertThat(liveClient.get().isEnabled()).isFalse();
    }

    private static ClientUrlChangeRequest urlRequest(
            String targetId,
            List<String> redirectUris,
            List<String> webOrigins) {
        return new ClientUrlChangeRequest(
                targetId, REALM, CLIENT, redirectUris, webOrigins, "planner", null);
    }

    private static ClientSecurityChangeRequest securityRequest(
            String targetId,
            String pkce,
            Boolean standard,
            Boolean implicit,
            Boolean direct,
            Boolean serviceAccounts,
            Boolean publicClient) {
        return new ClientSecurityChangeRequest(
                targetId,
                REALM,
                CLIENT,
                pkce,
                standard,
                implicit,
                direct,
                serviceAccounts,
                publicClient,
                "planner",
                null);
    }

    private static ClientRepresentation sampleClient(String name, String description) {
        ClientRepresentation rep = new ClientRepresentation();
        rep.setId("client-uuid-1");
        rep.setClientId(CLIENT);
        rep.setName(name);
        rep.setDescription(description);
        rep.setAttributes(new HashMap<>());
        rep.setRedirectUris(new java.util.ArrayList<>(List.of("https://old.example/callback")));
        rep.setWebOrigins(new java.util.ArrayList<>(List.of("https://old.example")));
        rep.setRootUrl("https://preserved.example");
        rep.setEnabled(false);
        rep.setPublicClient(false);
        rep.setServiceAccountsEnabled(false);
        rep.setStandardFlowEnabled(false);
        rep.setImplicitFlowEnabled(false);
        rep.setDirectAccessGrantsEnabled(false);
        rep.setSecret("server-side-secret");
        return rep;
    }

    private static ClientRepresentation copy(ClientRepresentation source) {
        ClientRepresentation copy = new ClientRepresentation();
        copy.setId(source.getId());
        copy.setClientId(source.getClientId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setAttributes(source.getAttributes() == null ? new HashMap<>() : new HashMap<>(source.getAttributes()));
        copy.setRedirectUris(source.getRedirectUris() == null
                ? null
                : new java.util.ArrayList<>(source.getRedirectUris()));
        copy.setWebOrigins(source.getWebOrigins() == null
                ? null
                : new java.util.ArrayList<>(source.getWebOrigins()));
        copy.setRootUrl(source.getRootUrl());
        copy.setEnabled(source.isEnabled());
        copy.setPublicClient(source.isPublicClient());
        copy.setServiceAccountsEnabled(source.isServiceAccountsEnabled());
        copy.setStandardFlowEnabled(source.isStandardFlowEnabled());
        copy.setImplicitFlowEnabled(source.isImplicitFlowEnabled());
        copy.setDirectAccessGrantsEnabled(source.isDirectAccessGrantsEnabled());
        copy.setSecret(source.getSecret());
        return copy;
    }
}
