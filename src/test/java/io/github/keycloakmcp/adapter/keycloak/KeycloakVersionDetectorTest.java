package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
