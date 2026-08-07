package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakCapabilitiesTest {

    private KeycloakVersionDetector detector;

    @BeforeEach
    void setUp() {
        detector = new KeycloakVersionDetector();
    }

    @Test
    void capabilityDetectionPrefersFeatureFlagsOverRawVersionEquals() {
        // Same version string, different feature maps — features must drive capabilities
        KeycloakCapabilities withoutOrg = detector.detectCapabilities(
                "26.7.1",
                Map.of("ORGANIZATION", false, "WORKFLOWS", false));
        KeycloakCapabilities withOrg = detector.detectCapabilities(
                "26.7.1",
                Map.of("ORGANIZATION", true, "WORKFLOWS", true, "SCIM", true));

        assertThat(withoutOrg.version()).isEqualTo("26.7.1");
        assertThat(withoutOrg.organizations()).isFalse();
        assertThat(withoutOrg.workflows()).isFalse();

        assertThat(withOrg.version()).isEqualTo("26.7.1");
        assertThat(withOrg.organizations()).isTrue();
        assertThat(withOrg.workflows()).isTrue();
        assertThat(withOrg.scim()).isTrue();
    }

    @Test
    void adminApiV2IsNeverEnabledAsPrimaryInDetectCapabilities() {
        KeycloakCapabilities caps = detector.detectCapabilities(
                "26.7.1",
                Map.of("ADMIN_API_V2", true, "admin-api-v2", true));

        // Architecture decision for 0.1.0: Stable Admin API is primary
        assertThat(caps.adminApiV2()).isFalse();
    }

    @Test
    void detectsFineGrainedAdminPermissionsV2FromFeatures() {
        KeycloakCapabilities caps = detector.detectCapabilities(
                "26.6.5",
                Map.of("ADMIN_FINE_GRAINED_AUTHZ_V2", true));

        assertThat(caps.fineGrainedAdminPermissionsV2()).isTrue();
        assertThat(caps.adminApiV2()).isFalse();
    }

    @Test
    void detectCapabilitiesFromServerInfoMapUsesNestedVersionAndFeatures() {
        Map<String, Object> serverInfo = Map.of(
                "systemInfo", Map.of("version", "26.7.1"),
                "features", Map.of("organizations", true));

        KeycloakCapabilities caps = detector.detectCapabilities(serverInfo);

        assertThat(caps.version()).isEqualTo("26.7.1");
        assertThat(caps.organizations()).isTrue();
    }
}
