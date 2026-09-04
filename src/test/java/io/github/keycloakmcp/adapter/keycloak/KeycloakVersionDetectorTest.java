package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.info.FeatureRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

import io.github.keycloakmcp.domain.common.ServerInfo;

class KeycloakVersionDetectorTest {

    private KeycloakVersionDetector detector;

    @BeforeEach
    void setUp() {
        detector = new KeycloakVersionDetector();
    }

    @Test
    void detectsRhbkFromRedHatBuildOfKeycloakProductName() {
        ServerInfo.Product product = detector.detectProduct(
                "26.6.5",
                Map.of("product", "Red Hat build of Keycloak"));

        assertThat(product).isEqualTo(ServerInfo.Product.RHBK);
    }

    @Test
    void detectsRhbkFromProductHintString() {
        assertThat(detector.detectProduct("Red Hat build of Keycloak 26.6.5"))
                .isEqualTo(ServerInfo.Product.RHBK);
        assertThat(detector.detectProduct("RHBK"))
                .isEqualTo(ServerInfo.Product.RHBK);
    }

    @Test
    void detectsCommunityKeycloak() {
        ServerInfo.Product product = detector.detectProduct(
                "26.7.1",
                Map.of("product", "Keycloak", "version", "26.7.1"));

        assertThat(product).isEqualTo(ServerInfo.Product.KEYCLOAK);
        assertThat(detector.detectProduct("Keycloak")).isEqualTo(ServerInfo.Product.KEYCLOAK);
    }

    @Test
    void returnsUnknownWhenNoProductHint() {
        assertThat(detector.detectProduct((String) null)).isEqualTo(ServerInfo.Product.UNKNOWN);
        assertThat(detector.detectProduct("   ")).isEqualTo(ServerInfo.Product.UNKNOWN);
        assertThat(detector.detectProduct("26.7.1")).isEqualTo(ServerInfo.Product.UNKNOWN);
    }

    @Test
    void parseVersionExtractsNumericVersion() {
        assertThat(detector.parseVersion("26.7.1")).contains("26.7.1");
        assertThat(detector.parseVersion("Keycloak 26.6.5")).contains("26.6.5");
        assertThat(detector.parseVersion("26.6.5.redhat-00001")).contains("26.6.5");
    }

    @Test
    void observedRedHatBuildIdentifiesRhbkWithoutChangingNormalizedVersionContract() {
        assertThat(detector.detectProduct("26.6.3.redhat-00002")).isEqualTo(ServerInfo.Product.RHBK);
        assertThat(detector.detectProduct("Keycloak26.6.3.redhat-00002")).isEqualTo(ServerInfo.Product.RHBK);
        assertThat(detector.parseVersion("26.6.3.redhat-00002")).contains("26.6.3");
    }

    @Test
    void observedRedHatBuildOverridesGenericProductAndDefaultProfileHints() {
        var metadata = Map.of("product", "Keycloak", "profileInfo", Map.of("name", "default"),
                "systemInfo", Map.of("version", "26.6.3.redhat-00002"));
        assertThat(detector.detectProduct(null, metadata)).isEqualTo(ServerInfo.Product.RHBK);
    }

    @Test
    void representationDefaultProfileAndAdminFeaturesCannotMaskRedHatBuild() {
        ServerInfoRepresentation server = mock(ServerInfoRepresentation.class, RETURNS_DEEP_STUBS);
        when(server.getSystemInfo().getVersion()).thenReturn("26.6.3.redhat-00002");
        when(server.getProfileInfo().getName()).thenReturn("default");
        FeatureRepresentation adminApi = mock(FeatureRepresentation.class);
        when(adminApi.getName()).thenReturn("ADMIN_API");
        when(adminApi.isEnabled()).thenReturn(true);
        when(server.getFeatures()).thenReturn(List.of(adminApi));

        assertThat(detector.detectProduct(server)).isEqualTo(ServerInfo.Product.RHBK);
        assertThat(detector.detectProduct((ServerInfoRepresentation) null)).isEqualTo(ServerInfo.Product.UNKNOWN);
    }
}
