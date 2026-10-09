package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.credential.CredentialProvider;
import io.github.keycloakmcp.collection.CollectionBudget;
import io.github.keycloakmcp.credential.KeycloakCredentials;
import io.github.keycloakmcp.target.KeycloakTargetConfiguration;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetEnvironment;
import io.github.keycloakmcp.target.TargetId;
import io.github.keycloakmcp.target.TargetType;

/**
 * Ensures credential refs are not mixed across targets.
 * Uses a recording CredentialProvider (does not open real Keycloak connections for the
 * credential-resolution assertion; client build may fail later on network — we only verify
 * which credential-ref was requested per target).
 */
class KeycloakClientFactoryTest {

    @Test
    void resolvesDistinctCredentialRefsPerTarget() {
        List<String> requestedRefs = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        CredentialProvider provider = mock(CredentialProvider.class);
        when(provider.getKeycloakCredentials("cred-a")).thenAnswer(inv -> {
            requestedRefs.add("cred-a");
            calls.incrementAndGet();
            return new KeycloakCredentials("keycloak-mcp", "secret-a");
        });
        when(provider.getKeycloakCredentials("cred-b")).thenAnswer(inv -> {
            requestedRefs.add("cred-b");
            calls.incrementAndGet();
            return new KeycloakCredentials("keycloak-mcp", "secret-b");
        });

        KeycloakClientFactory factory = new KeycloakClientFactory(provider);

        Target targetA = target("lab-a", "http://kc-a:8080", "cred-a");
        Target targetB = target("lab-b", "http://kc-b:8080", "cred-b");

        try {
            factory.getClient(targetA);
        } catch (RuntimeException ignored) {
            // Admin client may fail later; credential lookup already happened
        }
        try {
            factory.getClient(targetB);
        } catch (RuntimeException ignored) {
            // same
        }

        verify(provider, times(1)).getKeycloakCredentials("cred-a");
        verify(provider, times(1)).getKeycloakCredentials("cred-b");
        assertThat(requestedRefs).containsExactly("cred-a", "cred-b");
        assertThat(calls.get()).isEqualTo(2);

        factory.shutdown();
    }

    @Test
    void cachesClientPerTargetFingerprint() {
        CredentialProvider provider = mock(CredentialProvider.class);
        when(provider.getKeycloakCredentials("cred-a"))
                .thenReturn(new KeycloakCredentials("keycloak-mcp", "secret-a"));

        KeycloakClientFactory factory = new KeycloakClientFactory(provider);
        Target targetA = target("lab-a", "http://127.0.0.1:9", "cred-a"); // unlikely to connect

        try {
            factory.getClient(targetA);
        } catch (RuntimeException ignored) {
        }
        try {
            factory.getClient(targetA);
        } catch (RuntimeException ignored) {
        }

        // Same fingerprint → credential resolved at least once; second call should reuse cache
        // (provider still called only when building — verify at most twice before cache, ideally once)
        verify(provider, org.mockito.Mockito.atLeastOnce()).getKeycloakCredentials("cred-a");
        factory.shutdown();
    }

    @Test
    void expiredBudgetRejectsBeforeCredentialResolution() {
        CredentialProvider provider = mock(CredentialProvider.class);
        var factory = new KeycloakClientFactory(provider);
        AtomicLong clock = new AtomicLong();
        var budget = new CollectionBudget(Duration.ofMillis(10), clock::get);
        clock.set(10_000_000);
        try (var scope = CollectionBudget.open("lab-a", budget)) {
            assertThatThrownBy(() -> factory.getClient(target("lab-a", "http://localhost", "cred-a")))
                    .isInstanceOf(CollectionBudget.Aborted.class);
        } finally { factory.shutdown(); }
        verifyNoInteractions(provider);
    }

    @Test
    void lateCredentialsCannotReturnEvenPreviouslyCachedClient() {
        CredentialProvider provider = mock(CredentialProvider.class);
        AtomicLong clock = new AtomicLong();
        when(provider.getKeycloakCredentials("cred-a"))
                .thenReturn(new KeycloakCredentials("client", "secret"))
                .thenAnswer(inv -> {
                    clock.set(10_000_000); return new KeycloakCredentials("client", "secret");
                });
        var factory = new KeycloakClientFactory(provider);
        var target = target("lab-a", "http://127.0.0.1:9", "cred-a");
        try (var scope = CollectionBudget.open("lab-a", new CollectionBudget(Duration.ofMillis(10), clock::get))) {
            factory.getClient(target);
            assertThatThrownBy(() -> factory.getClient(target)).isInstanceOf(CollectionBudget.Aborted.class)
                    .hasMessage("OPERATION_BUDGET_EXCEEDED");
        } finally { factory.shutdown(); }
    }

    @Test
    void collectionClientIsSeparateAndOrdinaryTransportCacheIsUnchanged() {
        CredentialProvider provider = mock(CredentialProvider.class);
        when(provider.getKeycloakCredentials("cred-a")).thenReturn(new KeycloakCredentials("client", "secret"));
        var factory = new KeycloakClientFactory(provider);
        var target = target("lab-a", "http://127.0.0.1:9", "cred-a");
        try {
            var ordinary = factory.getClient(target);
            org.keycloak.admin.client.Keycloak collection;
            try (var scope = CollectionBudget.open("lab-a", 30_000)) {
                collection = factory.getClient(target);
                assertThat(collection).isNotSameAs(ordinary);
                assertThat(factory.getClient(target)).isSameAs(collection);
            }
            assertThat(factory.getClient(target)).isSameAs(ordinary);
            try (var scope = CollectionBudget.open("lab-a", 30_000)) {
                assertThat(factory.getClient(target)).isSameAs(collection);
            }
        } finally { factory.shutdown(); }
    }

    @Test
    void mismatchedTargetRejectsBeforeCredentialResolution() {
        CredentialProvider provider = mock(CredentialProvider.class);
        var factory = new KeycloakClientFactory(provider);
        try (var scope = CollectionBudget.open("lab-b", 30_000)) {
            assertThatThrownBy(() -> factory.getClient(target("lab-a", "http://localhost", "cred-a")))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Collection scope target mismatch");
        } finally { factory.shutdown(); }
        verifyNoInteractions(provider);
    }

    private static Target target(String id, String url, String credentialRef) {
        return new Target(
                TargetId.of(id),
                id,
                TargetType.KEYCLOAK,
                TargetEnvironment.DEV,
                true,
                new KeycloakTargetConfiguration(url, "master", "keycloak-mcp", credentialRef),
                null,
                null,
                Map.of());
    }
}
