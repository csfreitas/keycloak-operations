package io.github.keycloakmcp.collector.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import io.github.keycloakmcp.adapter.keycloak.KeycloakVersionDetector;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.assessment.engine.EvidenceContext;
import io.github.keycloakmcp.collector.AssessmentEvidenceService;
import io.github.keycloakmcp.collector.infrastructure.InfrastructureEvidenceCollector;
import io.github.keycloakmcp.collector.metrics.MetricsEvidenceCollector;
import io.github.keycloakmcp.config.AssessmentConfig;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

class KeycloakEvidenceCollectorTest {
    private final StableAdminApiAdapter admin = mock(StableAdminApiAdapter.class);
    private final AssessmentConfig config = mock(AssessmentConfig.class);
    private final Target target = new Target(new TargetId("test"), "test", TargetType.KEYCLOAK,
            TargetEnvironment.PRD, true,
            new KeycloakTargetConfiguration("http://localhost", "master", "test", "ref"),
            null, null, Map.of());
    private KeycloakEvidenceCollector collector;

    @BeforeEach
    void setup() {
        when(config.maxRealms()).thenReturn(50);
        when(config.maxClientsPerRealm()).thenReturn(100);
        ServerInfoRepresentation server = new ServerInfoRepresentation();
        SystemInfoRepresentation system = new SystemInfoRepresentation();
        system.setVersion("26.7.1");
        server.setSystemInfo(system);
        when(admin.getServerInfo(target)).thenReturn(server);
        collector = new KeycloakEvidenceCollector(admin, new KeycloakVersionDetector(), config);
        RealmRepresentation realm = realm("app");
        when(admin.listRealms(target)).thenReturn(List.of(realm));
        when(admin.getRealm(target, "app")).thenReturn(realm);
        when(admin.listClients(target, "app", false)).thenReturn(List.of());
    }

    @Test
    void deniedClientCollectionIsExplicitAndDoesNotInventSafeZero() {
        when(admin.listClients(target, "app", false)).thenThrow(McpException.authorizationFailed("secret detail"));
        var result = service().collect(target);
        EvidenceContext context = new EvidenceContext(result.evidence());

        assertThat(result.failedSources()).isEmpty();
        assertThat(result.partialSources()).containsExactly("keycloak");
        assertThat(context.get("keycloak.collection.complete")).contains(false);
        assertThat(context.get("keycloak.collection.issues")).contains(
                List.of(Map.of("code", "UNAUTHORIZED", "scope", "clients:app")));
        assertThat(context.hasKey("keycloak.clients.publicWithoutPkceS256")).isFalse();
        assertThat(result.evidence().toString()).doesNotContain("secret detail");
    }

    @Test
    void deniedServerMetadataPreservesPermittedRealmAndClientEvidenceWithoutEscalation() {
        when(admin.getServerInfo(target)).thenThrow(McpException.authorizationFailed("secret detail"));
        var result = service().collect(target);
        var context = new EvidenceContext(result.evidence());

        assertThat(result.failedSources()).isEmpty();
        assertThat(result.partialSources()).containsExactly("keycloak");
        assertThat(context.hasKey("keycloak.version")).isFalse();
        assertThat(context.get("keycloak.collection.complete")).contains(false);
        assertThat(context.get("keycloak.collection.issues"))
                .contains(List.of(Map.of("code", "UNAUTHORIZED", "scope", "server-info")));
        assertThat(context.forSubject(io.github.keycloakmcp.assessment.engine.EvidenceSubject.realm("app"))
                .get("realm.bruteForceProtected")).contains(true);
        assertThat(context.get("keycloak.collection.realmsCollected")).contains(1);
        assertThat(context.get("keycloak.clients.publicWithoutPkceS256")).contains(0);
        assertThat(result.evidence().toString()).doesNotContain("secret detail");
    }

    @Test
    void unavailableServerMetadataDoesNotDiscardObservedClientRisk() {
        when(admin.getServerInfo(target)).thenThrow(McpException.keycloakUnavailable("secret detail", null));
        when(admin.listClients(target, "app", false)).thenReturn(List.of(client("unsafe", null)));
        var result = service().collect(target);
        var context = new EvidenceContext(result.evidence());

        assertThat(result.partialSources()).containsExactly("keycloak");
        assertThat(context.hasKey("keycloak.version")).isFalse();
        assertThat(context.get("keycloak.collection.issues"))
                .contains(List.of(Map.of("code", "UNAVAILABLE", "scope", "server-info")));
        assertThat(context.get("keycloak.clients.publicWithoutPkceS256")).contains(1);
    }

    @Test
    void nullServerMetadataIsExplicitlyUnknownAndResourceReadsContinue() {
        when(admin.getServerInfo(target)).thenReturn(null);
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.hasKey("keycloak.version")).isFalse();
        assertThat(context.get("keycloak.collection.complete")).contains(false);
        assertThat(context.get("keycloak.collection.issues"))
                .contains(List.of(Map.of("code", "INVALID_RESPONSE", "scope", "server-info")));
        assertThat(context.get("keycloak.collection.realmsCollected")).contains(1);
    }

    @Test
    void absentSystemInfoOrVersionDoesNotBecomeObservedVersion() {
        ServerInfoRepresentation server = new ServerInfoRepresentation();
        when(admin.getServerInfo(target)).thenReturn(server);
        var missingSystem = new EvidenceContext(collector.collect(target));
        SystemInfoRepresentation system = new SystemInfoRepresentation();
        system.setVersion("  ");
        server.setSystemInfo(system);
        var missingVersion = new EvidenceContext(collector.collect(target));

        for (var context : List.of(missingSystem, missingVersion)) {
            assertThat(context.hasKey("keycloak.version")).isFalse();
            assertThat(context.get("keycloak.collection.complete")).contains(false);
            assertThat(context.get("keycloak.collection.issues"))
                    .contains(List.of(Map.of("code", "MISSING_FIELDS", "scope", "server-info.version")));
            assertThat(context.get("keycloak.collection.realmsCollected")).contains(1);
        }
    }

    @Test
    void configuredRhbkTypeWithoutMetadataDoesNotProveObservedProduct() {
        Target rhbk = new Target(target.id(), target.displayName(), TargetType.RHBK, target.environment(),
                true, target.keycloak(), null, null, Map.of());
        when(admin.getServerInfo(rhbk)).thenThrow(McpException.authorizationFailed("not allowed"));
        when(admin.listRealms(rhbk)).thenReturn(List.of(realm("app")));
        when(admin.getRealm(rhbk, "app")).thenReturn(realm("app"));
        when(admin.listClients(rhbk, "app", false)).thenReturn(List.of());
        var context = new EvidenceContext(collector.collect(rhbk));

        assertThat(context.get("keycloak.product")).isEmpty();
        assertThat(context.get("keycloak.product.configured")).contains("RHBK");
        assertThat(context.get("keycloak.version.raw")).isEmpty();
        assertThat(context.get("keycloak.collection.realmsCollected")).contains(1);
    }

    @Test
    void observedRedHatBuildIsPreservedSeparatelyFromNormalizedVersionAndConfiguredProduct() {
        ServerInfoRepresentation server = new ServerInfoRepresentation();
        SystemInfoRepresentation system = new SystemInfoRepresentation();
        system.setVersion("26.6.3.redhat-00002");
        server.setSystemInfo(system);
        when(admin.getServerInfo(target)).thenReturn(server);
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.get("keycloak.product")).contains("RHBK");
        assertThat(context.get("keycloak.product.configured")).contains("KEYCLOAK");
        assertThat(context.get("keycloak.version")).contains("26.6.3");
        assertThat(context.get("keycloak.version.raw")).contains("26.6.3.redhat-00002");
    }

    @Test
    void deniedRealmHasIdentityButNoFabricatedRealmProperties() {
        when(admin.getRealm(target, "app")).thenThrow(new jakarta.ws.rs.ForbiddenException());
        var result = service().collect(target);
        EvidenceContext context = new EvidenceContext(result.evidence());
        var realmContext = context.forSubjects(io.github.keycloakmcp.assessment.engine.SubjectType.REALM).getFirst();

        assertThat(result.partialSources()).containsExactly("keycloak");
        assertThat(realmContext.subject().orElseThrow().id()).isEqualTo("app");
        assertThat(realmContext.hasKey("realm.bruteForceProtected")).isFalse();
        assertThat(context.get("keycloak.collection.realmsCollected")).contains(0);
    }

    @Test
    void realmLimitAndClientLimitAreReportedNotPresentedAsComplete() {
        when(config.maxRealms()).thenReturn(1);
        when(config.maxClientsPerRealm()).thenReturn(1);
        when(admin.listRealms(target)).thenReturn(List.of(realm("app"), realm("not-scanned")));
        when(admin.listClients(target, "app", false)).thenReturn(List.of(client("safe", "S256"), client("unsafe", null)));
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.get("keycloak.collection.complete")).contains(false);
        assertThat(context.get("keycloak.collection.issues")).contains(List.of(
                Map.of("code", "TRUNCATED", "scope", "realms"),
                Map.of("code", "TRUNCATED", "scope", "clients:app")));
        assertThat(context.get("keycloak.collection.realmsDiscovered")).contains(2);
        assertThat(context.get("keycloak.collection.clientsObserved")).contains(2);
        assertThat(context.get("keycloak.collection.clientsInspected")).contains(1);
        assertThat(context.hasKey("keycloak.clients.publicWithoutPkceS256")).isFalse();
    }

    @Test
    void observedPositiveFindingSurvivesPartialCollectionAsLowerBound() {
        when(config.maxClientsPerRealm()).thenReturn(1);
        when(admin.listClients(target, "app", false)).thenReturn(List.of(client("unsafe", null), client("safe", "S256")));
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.get("keycloak.collection.complete")).contains(false);
        assertThat(context.get("keycloak.clients.publicWithoutPkceS256")).contains(1);
    }

    @Test
    void pkceAppliesOnlyToEnabledPublicOidcAuthorizationCodeClients() {
        ClientRepresentation disabled = client("disabled", null);
        disabled.setEnabled(false);
        ClientRepresentation saml = client("saml", null);
        saml.setProtocol("saml");
        ClientRepresentation noCode = client("no-code", null);
        noCode.setStandardFlowEnabled(false);
        ClientRepresentation confidential = client("confidential", null);
        confidential.setPublicClient(false);
        when(admin.listClients(target, "app", false)).thenReturn(List.of(disabled, saml, noCode, confidential,
                client("safe", "S256"), client("unsafe", null)));
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.get("keycloak.collection.complete")).contains(true);
        assertThat(context.get("keycloak.clients.publicWithoutPkceS256")).contains(1);
        assertThat(context.get("keycloak.clients.publicWithoutPkceS256.clientIds")).contains(List.of("app/unsafe"));
    }

    @Test
    void unknownPkceApplicabilityDoesNotBecomePass() {
        ClientRepresentation unknown = client("unknown", null);
        unknown.setStandardFlowEnabled(null);
        when(admin.listClients(target, "app", false)).thenReturn(List.of(unknown));
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.get("keycloak.collection.complete")).contains(false);
        assertThat(context.hasKey("keycloak.clients.publicWithoutPkceS256")).isFalse();
    }

    @Test
    void duplicateClientNamesInDifferentRealmsDoNotCollapse() {
        when(admin.listRealms(target)).thenReturn(List.of(realm("app"), realm("other")));
        when(admin.getRealm(target, "other")).thenReturn(realm("other"));
        when(admin.listClients(target, "app", false)).thenReturn(List.of(client("shared", null)));
        when(admin.listClients(target, "other", false)).thenReturn(List.of(client("shared", null)));
        var context = new EvidenceContext(collector.collect(target));

        assertThat(context.get("keycloak.clients.publicWithoutPkceS256.clientIds"))
                .contains(List.of("app/shared", "other/shared"));
    }

    @Test
    void emptyRealmCollectionDoesNotCreateSyntheticProtectedRealm() {
        when(admin.listRealms(target)).thenReturn(List.of());
        var context = new EvidenceContext(collector.collect(target));
        assertThat(context.hasKey("realm.bruteForceProtected")).isFalse();
        assertThat(context.hasKey("realm.sslRequired")).isFalse();
    }

    @Test
    void absentOptionalRealmBooleansRemainUnknownInsteadOfObservedFalse() {
        var context = new EvidenceContext(collector.collect(target))
                .forSubject(io.github.keycloakmcp.assessment.engine.EvidenceSubject.realm("app"));
        assertThat(context.get("realm.verifyEmail")).isEmpty();
        assertThat(context.get("realm.eventsEnabled")).isEmpty();
        assertThat(context.get("realm.rememberMe")).isEmpty();
    }

    private AssessmentEvidenceService service() {
        return new AssessmentEvidenceService(collector, mock(InfrastructureEvidenceCollector.class),
                mock(MetricsEvidenceCollector.class));
    }

    private static RealmRepresentation realm(String name) {
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(name);
        realm.setEnabled(true);
        realm.setBruteForceProtected(true);
        realm.setSslRequired("external");
        realm.setRegistrationAllowed(false);
        return realm;
    }

    private static ClientRepresentation client(String id, String pkce) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(id);
        client.setEnabled(true);
        client.setProtocol("openid-connect");
        client.setStandardFlowEnabled(true);
        client.setImplicitFlowEnabled(false);
        client.setDirectAccessGrantsEnabled(false);
        client.setPublicClient(true);
        client.setAttributes(pkce == null ? Map.of() : Map.of("pkce.code.challenge.method", pkce));
        return client;
    }
}
