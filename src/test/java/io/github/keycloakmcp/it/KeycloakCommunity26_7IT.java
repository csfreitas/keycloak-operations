package io.github.keycloakmcp.it;

import static io.github.keycloakmcp.it.DisposableKeycloakGuard.EXPECTED_VERSION;
import static io.github.keycloakmcp.it.DisposableKeycloakGuard.TARGET_ID;
import static io.github.keycloakmcp.it.DisposableKeycloakGuard.requireDisposableLoopback;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.target.TargetResolver;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

/** Verifies the repository-declared Community Keycloak version through Admin REST. */
@QuarkusTest
@TestProfile(DisposableKeycloakIntegrationTestProfile.class)
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "RUN_KEYCLOAK_IT", matches = "true")
class KeycloakCommunity26_7IT {

    @Inject
    TargetResolver targetResolver;

    @Inject
    StableAdminApiAdapter adminApi;

    @Test
    void detectsRepositoryDeclaredCommunityVersion() {
        var target = targetResolver.require(TARGET_ID);
        requireDisposableLoopback(target);

        var serverInfo = adminApi.getServerInfo(target);
        assertThat(serverInfo.getSystemInfo()).isNotNull();
        assertThat(serverInfo.getSystemInfo().getVersion()).isEqualTo(EXPECTED_VERSION);
    }
}
