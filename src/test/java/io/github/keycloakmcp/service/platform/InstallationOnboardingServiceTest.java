package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import io.github.keycloakmcp.adapter.infrastructure.*;
import io.github.keycloakmcp.persistence.entity.*;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.*;
import io.github.keycloakmcp.target.*;
import io.github.keycloakmcp.domain.error.McpException;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@QuarkusTest
class InstallationOnboardingServiceTest {
    @Inject InstallationOnboardingService service;
    @Inject TargetRepository targets;
    @Inject TargetPersistenceMapper mapper;
    @Inject AuditRepository audits;
    @Inject EntityManager em;
    @InjectMock InfrastructureClientFactory clients;
    @InjectMock InstallationCandidateCollector collector;
    @InjectMock TargetAuthorizationService authz;
    String id;
    KubernetesInstallationBinding binding = new KubernetesInstallationBinding("apps/v1", "Deployment", "rhbk", "observed-uid");

    @BeforeEach void setup() throws Exception {
        id = "onboarding-" + UUID.randomUUID();
        var target = new Target(TargetId.of(id), "Onboarding", TargetType.RHBK, TargetEnvironment.TEST, true,
                new KeycloakTargetConfiguration("http://localhost:8080", "master", "assessor", "iam-ref"),
                new InfrastructureTargetConfiguration(InfrastructureType.KUBERNETES, "approved", "iam", "cluster-ref"), null, Map.of());
        QuarkusTransaction.requiringNew().run(() -> targets.persist(mapper.toEntity(target)));
        var cluster = mock(ClusterClient.class);
        var client = mock(KubernetesClient.class);
        when(client.getMasterUrl()).thenReturn(new java.net.URL("http://localhost:9999"));
        when(cluster.kubernetes()).thenReturn(client);
        when(clients.resolve(any())).thenReturn(Optional.of(cluster));
        when(collector.discover(any(), anyString())).thenReturn(List.of(binding));
        when(collector.stillExists(any(), anyString(), any())).thenReturn(true);
        when(authz.currentActor()).thenReturn("test-issuer#operator");
        when(authz.isAllowed(any(), any())).thenReturn(true);
    }

    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            InstallationDiscoveryRunEntity.delete("targetId", id);
            audits.delete("targetId", id);
            targets.deleteById(id);
        });
    }

    @Test void confirmsPersistedCandidateAndAuditsSameRevision() {
        var run = service.discover(id);
        var confirmed = service.confirm(id, request(run));
        assertThat(confirmed.binding()).isEqualTo(binding);
        assertThat(confirmed.managed()).isTrue();
        assertThat(confirmed.revision()).isEqualTo(1);
        assertThat(QuarkusTransaction.requiringNew().call(() -> targets.findById(id).registryRevision)).isEqualTo(1L);
        var audit = audits.find("targetId", id).firstResult();
        assertThat(audit.metadata).containsEntry("actor", "test-issuer#operator").containsEntry("selectedUid", "observed-uid");
        verify(authz, atLeastOnce()).assertAllowed(any(), eq(TargetPermission.DISCOVER));
        verify(authz, atLeastOnce()).assertAllowed(any(), eq(TargetPermission.BIND));
        assertThatThrownBy(() -> service.confirm(id, request(run))).isInstanceOf(McpException.class);
    }

    @Test void apiUsesOpaqueCandidateIdsAndReturnsConfiguredBinding() {
        var response = given().contentType("application/json").post("/api/v1/targets/" + id + "/installation/discover")
                .then().statusCode(200).body("targetId", equalTo(id)).extract().jsonPath();
        given().contentType("application/json").body(Map.of("runId", response.getString("runId"),
                "candidateId", response.getString("candidates[0].id")))
                .post("/api/v1/targets/" + id + "/installation/confirm")
                .then().statusCode(200).body("binding.uid", equalTo("observed-uid")).body("revision", equalTo(1));
    }

    @Test void cannotConfirmAsAnotherActor() {
        var run = service.discover(id);
        when(authz.currentActor()).thenReturn("test-issuer#other");
        assertRejected(run);
    }

    @Test void expiredDiscoveryCannotChangeBinding() {
        var run = service.discover(id);
        QuarkusTransaction.requiringNew().run(() -> InstallationDiscoveryRunEntity.<InstallationDiscoveryRunEntity>findById(run.runId()).expiresAt = Instant.now().minusSeconds(1));
        assertRejected(run);
    }

    @Test void changedRevisionCannotOverwriteConcurrentSelection() {
        var run = service.discover(id);
        QuarkusTransaction.requiringNew().run(() -> targets.findById(id).installationRevision++);
        assertRejected(run);
    }

    @Test void replacedResourceOrDeniedRevalidationCannotBind() {
        var run = service.discover(id);
        when(collector.stillExists(any(), anyString(), any())).thenReturn(false);
        assertRejected(run);
    }

    @Test void connectionChangeInvalidatesDiscovery() {
        var run = service.discover(id);
        QuarkusTransaction.requiringNew().run(() -> targets.findById(id).infraNamespace = "changed");
        assertRejected(run);
    }

    @Test void deniedDiscoverDoesNotContactInfrastructure() {
        doThrow(McpException.targetUnauthorized(id)).when(authz).assertAllowed(any(), eq(TargetPermission.DISCOVER));
        assertThatThrownBy(() -> service.discover(id)).isInstanceOf(McpException.class);
        verifyNoInteractions(clients, collector);
    }

    @Test void callerCannotSupplyAnUnobservedCandidate() {
        var run = service.discover(id);
        assertThatThrownBy(() -> service.confirm(id, new InstallationOnboardingService.Confirmation(run.runId(), "injected-uid"))).isInstanceOf(McpException.class);
        assertThat(service.state(id).binding()).isNull();
    }

    @Test void rollbackUndoesBindingRunConsumptionAndAuditTogether() {
        var run = service.discover(id);
        assertThatThrownBy(() -> QuarkusTransaction.requiringNew().run(() -> {
            service.confirm(id, request(run));
            throw new IllegalStateException("forced rollback");
        })).isInstanceOf(RuntimeException.class);
        assertThat(service.state(id).binding()).isNull();
        assertThat(audits.count("targetId", id)).isZero();
        assertThat(service.confirm(id, request(run)).binding()).isEqualTo(binding);
    }

    private InstallationOnboardingService.Confirmation request(InstallationOnboardingService.Discovery run) {
        return new InstallationOnboardingService.Confirmation(run.runId(), run.candidates().getFirst().id());
    }
    private void assertRejected(InstallationOnboardingService.Discovery run) {
        assertThatThrownBy(() -> service.confirm(id, request(run))).isInstanceOf(McpException.class);
        assertThat(service.state(id).binding()).isNull();
        assertThat(audits.count("targetId", id)).isZero();
    }
}
