package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.observability.McpMetrics;
import io.github.keycloakmcp.target.*;
import jakarta.ws.rs.ProcessingException;

class StableAdminApiAdapterBudgetTest {
    private final KeycloakClientFactory factory = mock(KeycloakClientFactory.class);
    private final McpMetrics metrics = mock(McpMetrics.class);
    private final StableAdminApiAdapter adapter = new StableAdminApiAdapter(factory, metrics);
    private final Target target = new Target(TargetId.of("test"), "test", TargetType.KEYCLOAK,
            TargetEnvironment.DEV, true, new KeycloakTargetConfiguration("http://localhost", "master", "client", "ref"),
            null, null, Map.of());

    @Test
    void foreignRealmDetailIsRejectedBeforeClientRead() {
        Keycloak client = mock(Keycloak.class);
        RealmResource realm = mock(RealmResource.class);
        when(factory.getClient(target)).thenReturn(client);
        when(client.realm("app")).thenReturn(realm);
        var foreign = new RealmRepresentation();
        foreign.setRealm("foreign-secret-canary");
        when(realm.toRepresentation()).thenReturn(foreign);
        try (var scope = CollectionBudget.open("test", 30_000)) {
            assertThatThrownBy(() -> adapter.getRealm(target, "app"))
                    .isInstanceOf(io.github.keycloakmcp.domain.error.McpException.class)
                    .hasNoCause().hasMessage("Keycloak Admin collection evidence is unavailable");
            assertThatThrownBy(() -> adapter.listClients(target, "app", false))
                    .isInstanceOf(io.github.keycloakmcp.domain.error.McpException.class)
                    .hasNoCause().hasMessage("Keycloak Admin collection evidence is unavailable");
        }
        verify(realm, never()).clients();
    }

    @Test
    void expiredScopeDoesNotResolveClientOrRecordAttempt() {
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(10), clock::get);
        clock.set(10_000_000);
        try (var scope = CollectionBudget.open("test", budget)) {
            assertThatThrownBy(() -> adapter.getServerInfo(target)).isInstanceOf(CollectionBudget.Aborted.class)
                    .hasMessage("OPERATION_BUDGET_EXCEEDED");
        }
        verifyNoInteractions(factory, metrics);
    }

    @Test
    void lateRealmExistenceResponseStopsFollowingClientRequest() {
        AtomicLong clock = new AtomicLong();
        Keycloak client = mock(Keycloak.class);
        RealmResource realm = mock(RealmResource.class);
        when(factory.getClient(target)).thenReturn(client);
        when(client.realm("app")).thenReturn(realm);
        when(realm.toRepresentation()).thenAnswer(inv -> {
            clock.set(10_000_000); return new RealmRepresentation();
        });
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofMillis(10), clock::get))) {
            assertThatThrownBy(() -> adapter.listClients(target, "app", false)).isInstanceOf(CollectionBudget.Aborted.class);
        }
        verify(realm, never()).clients();
        verify(factory).getClient(target);
    }

    @Test
    void lateResponseRejectedAndScopeDoesNotAffectFollowingOrdinaryRequest() {
        AtomicLong clock = new AtomicLong();
        Keycloak client = mock(Keycloak.class, RETURNS_DEEP_STUBS);
        when(factory.getClient(target)).thenReturn(client);
        when(client.serverInfo().getInfo()).thenAnswer(inv -> {
            clock.set(10_000_000); return new ServerInfoRepresentation();
        });
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofMillis(10), clock::get))) {
            assertThatThrownBy(() -> adapter.getServerInfo(target)).isInstanceOf(CollectionBudget.Aborted.class);
        }
        adapter.getServerInfo(target);
    }

    @Test
    void expiredTransportFailureBecomesSafeAbortRatherThanAvailabilityFinding() {
        AtomicLong clock = new AtomicLong();
        when(factory.getClient(target)).thenAnswer(inv -> {
            clock.set(10_000_000); throw new ProcessingException("sensitive transport detail");
        });
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofMillis(10), clock::get))) {
            assertThatThrownBy(() -> adapter.getServerInfo(target)).isInstanceOf(CollectionBudget.Aborted.class)
                    .hasMessage("OPERATION_BUDGET_EXCEEDED").hasNoCause();
        }
    }

    @Test
    void lateMissingRealmIsAbortedRatherThanPresentedAsObservedMissing() {
        AtomicLong clock = new AtomicLong();
        Keycloak client = mock(Keycloak.class);
        RealmResource realm = mock(RealmResource.class);
        when(factory.getClient(target)).thenReturn(client);
        when(client.realm("app")).thenReturn(realm);
        when(realm.toRepresentation()).thenAnswer(inv -> {
            clock.set(10_000_000); throw new jakarta.ws.rs.NotFoundException();
        });
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofMillis(10), clock::get))) {
            assertThatThrownBy(() -> adapter.listClients(target, "app", false)).isInstanceOf(CollectionBudget.Aborted.class)
                    .hasMessage("OPERATION_BUDGET_EXCEEDED").hasNoCause();
        }
        verify(realm, never()).clients();
    }

    @Test
    void foreignTargetScopeRejectsBeforeClientResolution() {
        try (var scope = CollectionBudget.open("other", 30_000)) {
            assertThatThrownBy(() -> adapter.getServerInfo(target)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Collection scope target mismatch");
        }
        verifyNoInteractions(factory, metrics);
    }
}
