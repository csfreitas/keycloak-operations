package io.github.keycloakmcp.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import io.github.keycloakmcp.target.Target;

/** Prevents opt-in write tests from ever operating on a non-local or unexpected target. */
final class DisposableKeycloakGuard {

    static final String TARGET_ID = "lab-keycloak-a";
    static final String EXPECTED_VERSION = "26.7.1";

    private DisposableKeycloakGuard() {
    }

    static void requireDisposableLoopback(Target target) {
        assertThat(target.id().value()).isEqualTo(TARGET_ID);
        assertThat(target.enabled()).isTrue();

        URI uri = URI.create(target.keycloak().url());
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getHost()).isIn("localhost", "127.0.0.1", "::1");
        assertThat(uri.getPort()).isEqualTo(8080);
        assertThat(uri.getUserInfo()).isNull();
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getFragment()).isNull();
        assertThat(uri.getPath()).isIn("", "/");
    }
}
