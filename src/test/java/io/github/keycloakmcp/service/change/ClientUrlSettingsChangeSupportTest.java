package io.github.keycloakmcp.service.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;

import io.github.keycloakmcp.domain.change.ClientUrlChangeRequest;
import io.github.keycloakmcp.domain.change.DiffKind;
import io.github.keycloakmcp.domain.error.McpException;

class ClientUrlSettingsChangeSupportTest {

    private ClientUrlSettingsChangeSupport support;

    @BeforeEach
    void setUp() {
        support = new ClientUrlSettingsChangeSupport();
    }

    @Test
    void plansDeterministicSetDiffAndStructuredOperation() {
        ClientRepresentation current = client(
                List.of("https://old.example/callback", "https://keep.example/callback"),
                List.of("https://old.example"));

        var planned = support.plan(current, request(
                List.of("https://new.example/callback", "https://keep.example/callback", "https://new.example/callback"),
                List.of("https://new.example")));

        assertThat(planned.desiredState().get("redirectUris"))
                .isEqualTo(List.of("https://keep.example/callback", "https://new.example/callback"));
        assertThat(planned.operations()).hasSize(2);
        assertThat(planned.operations().get(0).after()).isInstanceOf(List.class);
        assertThat(planned.diff())
                .extracting(diff -> diff.kind())
                .containsExactly(DiffKind.REMOVED, DiffKind.ADDED, DiffKind.REMOVED, DiffKind.ADDED);
    }

    @Test
    void rejectsNoOpRegardlessOfOrderAndDuplicates() {
        ClientRepresentation current = client(
                List.of("https://b.example/callback", "https://a.example/callback"),
                null);

        assertThatThrownBy(() -> support.plan(current, request(
                List.of("https://a.example/callback", "https://b.example/callback", "https://a.example/callback"),
                null)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("No effective");
    }

    @Test
    void distinguishesOmittedFromExplicitlyEmpty() {
        ClientRepresentation current = client(
                List.of("https://app.example/callback"),
                List.of("https://app.example"));

        var planned = support.plan(current, request(List.of(), null));

        assertThat(planned.desiredState()).containsOnlyKeys("redirectUris");
        assertThat(planned.operations()).singleElement()
                .satisfies(operation -> assertThat(operation.after()).isEqualTo(List.of()));
        assertThat(planned.diff()).singleElement()
                .satisfies(diff -> {
                    assertThat(diff.kind()).isEqualTo(DiffKind.REMOVED);
                    assertThat(diff.before()).isEqualTo("https://app.example/callback");
                });
    }

    @Test
    void acceptsExactHttpsLoopbackHttpPathWildcardAndPlusSentinel() {
        ClientRepresentation current = client(List.of(), List.of());

        var planned = support.plan(current, request(
                List.of(
                        "https://app.example/callback",
                        "http://127.0.0.1:8080/callback",
                        "https://app.example/callback/*"),
                List.of("https://app.example", "+")));

        assertThat(planned.operations()).hasSize(2);
    }

    @Test
    void rejectsUnsafeOrMalformedValues() {
        ClientRepresentation current = client(List.of(), List.of());
        List<String> invalidRedirects = List.of(
                "*",
                "/relative/callback",
                "ftp://app.example/callback",
                "https://user@app.example/callback",
                "https://app.example/a/../callback",
                "https://app.example/a/%2e%2e/callback",
                "https://*.example/callback",
                "https://app.example/callback#fragment",
                " https://app.example/callback");

        for (String invalid : invalidRedirects) {
            assertThatThrownBy(() -> support.plan(current, request(List.of(invalid), null)))
                    .as(invalid)
                    .isInstanceOf(McpException.class);
        }

        List<String> invalidOrigins = List.of(
                "*",
                "https://app.example/path",
                "https://app.example?query=x",
                "https://app.example#fragment",
                "https://user@app.example",
                "https://*.example");
        for (String invalid : invalidOrigins) {
            assertThatThrownBy(() -> support.plan(current, request(null, List.of(invalid))))
                    .as(invalid)
                    .isInstanceOf(McpException.class);
        }
    }

    @Test
    void enforcesEntryAndLengthBounds() {
        ClientRepresentation current = client(List.of(), List.of());
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= ClientUrlSettingsChangeSupport.MAX_ENTRIES_PER_FIELD; i++) {
            tooMany.add("https://app" + i + ".example/callback");
        }

        assertThatThrownBy(() -> support.plan(current, request(tooMany, null)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("maximum entries");
        assertThatThrownBy(() -> support.plan(current, request(
                List.of("https://app.example/" + "x".repeat(ClientUrlSettingsChangeSupport.MAX_ENTRY_LENGTH)),
                null)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("maximum length");

        List<String> oversizedRequest = IntStream.range(0, 17)
                .mapToObj(index -> "https://app" + index + ".example/" + "x".repeat(1_930))
                .toList();
        assertThatThrownBy(() -> support.plan(current, request(oversizedRequest, null)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("request exceeds maximum total length");
    }

    @Test
    void requiresAtLeastOneExplicitDesiredSet() {
        assertThatThrownBy(() -> support.plan(client(List.of(), List.of()), request(null, null)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void appliesOnlyRequestedCollectionsAndVerifiesReadBack() {
        ClientRepresentation client = client(
                List.of("https://old.example/callback"),
                List.of("https://unchanged.example"));
        client.setName("Preserved");
        var planned = support.plan(client, request(List.of("https://new.example/callback"), null));

        support.applyToRepresentation(client, planned.operations());

        assertThat(client.getRedirectUris()).containsExactly("https://new.example/callback");
        assertThat(client.getWebOrigins()).containsExactly("https://unchanged.example");
        assertThat(client.getName()).isEqualTo("Preserved");
        assertThat(support.compareDesired(client, planned.desiredState())).isEmpty();

        client.setRedirectUris(List.of("https://drift.example/callback"));
        assertThat(support.compareDesired(client, planned.desiredState()))
                .extracting(diff -> diff.kind())
                .containsExactly(DiffKind.REMOVED, DiffKind.ADDED);
    }

    private static ClientUrlChangeRequest request(List<String> redirects, List<String> origins) {
        return new ClientUrlChangeRequest("target", "realm", "client", redirects, origins, "actor", null);
    }

    private static ClientRepresentation client(List<String> redirects, List<String> origins) {
        ClientRepresentation representation = new ClientRepresentation();
        representation.setId("id");
        representation.setClientId("client");
        representation.setRedirectUris(redirects == null ? null : new ArrayList<>(redirects));
        representation.setWebOrigins(origins == null ? null : new ArrayList<>(origins));
        return representation;
    }
}
