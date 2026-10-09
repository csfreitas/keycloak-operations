package io.github.keycloakmcp.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.HealthStatus;
import io.github.keycloakmcp.target.Target;

class KeycloakAdminApiHealthCheckTest {
    private static final String SECRET = "password=fixture-secret token=fixture-token";
    private final StableAdminApiAdapter adapter = mock(StableAdminApiAdapter.class);
    private final Target target = mock(Target.class);
    private final KeycloakAdminApiHealthCheck check = new KeycloakAdminApiHealthCheck(adapter);

    @Test
    void recordsReachabilityAndObservedVersion() {
        var info = new ServerInfoRepresentation();
        var system = new SystemInfoRepresentation();
        system.setVersion("26.7.1");
        info.setSystemInfo(system);
        when(adapter.getServerInfo(target)).thenReturn(info);
        var result = check.check(target);
        assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(result.details()).containsEntry("version", "26.7.1").containsEntry("versionAvailable", true);
        verify(adapter).getServerInfo(target);
        verifyNoMoreInteractions(adapter);
    }

    @Test
    void absentSystemInfoDoesNotCauseNullMapFailure() {
        when(adapter.getServerInfo(target)).thenReturn(new ServerInfoRepresentation());
        var result = check.check(target);
        assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
        assertThat(result.details()).containsEntry("versionAvailable", false).doesNotContainKey("version");
    }

    @Test
    void absentVersionDoesNotInventVersionOrOutage() {
        var info = new ServerInfoRepresentation();
        info.setSystemInfo(new SystemInfoRepresentation());
        when(adapter.getServerInfo(target)).thenReturn(info);
        assertThat(check.check(target).details()).containsEntry("versionAvailable", false).doesNotContainKey("version");
    }

    @Test
    void nullResponseIsInconclusive() {
        assertThat(check.check(target).status()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(check.check(target).details()).containsEntry("reasonCode", "METADATA_UNAVAILABLE");
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(McpException.authorizationFailed(SECRET), HealthStatus.UNKNOWN, "ACCESS_DENIED"),
                Arguments.of(McpException.authenticationFailed(SECRET), HealthStatus.UNKNOWN, "AUTHENTICATION_FAILED"),
                Arguments.of(McpException.keycloakUnavailable(SECRET, new RuntimeException(SECRET)), HealthStatus.CRITICAL, "REQUEST_FAILED"),
                Arguments.of(McpException.unsupportedCapability(SECRET), HealthStatus.UNKNOWN, "METADATA_UNAVAILABLE"),
                Arguments.of(McpException.internal(SECRET), HealthStatus.UNKNOWN, "CHECK_FAILED"),
                Arguments.of(new RuntimeException(SECRET), HealthStatus.UNKNOWN, "CHECK_FAILED"));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void classifiesFailuresWithoutLeakingProviderMessages(RuntimeException error, HealthStatus status, String reason) {
        when(adapter.getServerInfo(target)).thenThrow(error);
        var result = check.check(target);
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.details()).containsEntry("reasonCode", reason);
        assertThat(result.toString()).doesNotContain("fixture-secret", "fixture-token", "Admin API unreachable");
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
        verify(adapter).getServerInfo(target);
        verifyNoMoreInteractions(adapter);
    }
}
