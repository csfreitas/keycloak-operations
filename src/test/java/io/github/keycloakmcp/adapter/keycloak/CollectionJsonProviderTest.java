package io.github.keycloakmcp.adapter.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.keycloakmcp.collection.CollectionBudget;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

class CollectionJsonProviderTest {
    private static final Type REALMS = new TypeReference<List<RealmRepresentation>>() { }.getType();
    private static final Type CLIENTS = new TypeReference<List<ClientRepresentation>>() { }.getType();
    private static final String TOKEN = "{\"access_token\":\"a.valid-token_~+/=\",\"token_type\":\"bEaReR\",\"expires_in\":300}";
    private final CollectionJsonProvider provider = new CollectionJsonProvider();

    @Test
    void tokenAcceptsValidOpaqueBearerAndOptionalNonnegativeLifetimes() {
        AccessTokenResponse token = read(TOKEN.substring(0, TOKEN.length() - 1)
                + ",\"refresh_expires_in\":0,\"not-before-policy\":0,\"futureField\":true}", AccessTokenResponse.class);
        assertThat(token.getToken()).isEqualTo("a.valid-token_~+/=");
        assertThat(token.getExpiresIn()).isEqualTo(300);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{}", "\"token\"",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":0}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":-1}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":1.5}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":\"300\"}",
            "{\"access_token\":\"x\",\"token_type\":\"Bearer\",\"expires_in\":9223372036854775808}",
            "{\"access_token\":null,\"token_type\":\"Bearer\",\"expires_in\":300}",
            "{\"access_token\":\"\",\"token_type\":\"Bearer\",\"expires_in\":300}",
            "{\"access_token\":\"a b\",\"token_type\":\"Bearer\",\"expires_in\":300}",
            "{\"access_token\":\"a\\r\\nsecret-canary\",\"token_type\":\"Bearer\",\"expires_in\":300}",
            "{\"access_token\":\"a\",\"token_type\":\"Basic\",\"expires_in\":300}",
            "{\"access_token\":\"a\",\"token_type\":\"Bearer \",\"expires_in\":300}",
            "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_expires_in\":-1}",
            "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_expires_in\":null}",
            "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":300,\"refresh_token\":42}"})
    void invalidTokenShapesAreRejectedWithoutRawDetails(String body) {
        rejected(body, AccessTokenResponse.class);
    }

    @Test
    void emptyListsRemainObservedEmptyAndMissingFlagsRemainUnknown() {
        assertThat(this.<List<RealmRepresentation>>read("[]", REALMS)).isEmpty();
        assertThat(this.<List<ClientRepresentation>>read("[]", CLIENTS)).isEmpty();
        RealmRepresentation realm = read("{\"realm\":\"demo\",\"futureField\":{\"new\":true},\"enabled\":null}",
                RealmRepresentation.class);
        assertThat(realm.getRealm()).isEqualTo("demo");
        assertThat(realm.isEnabled()).isNull();
        assertThat(realm.isBruteForceProtected()).isNull();
        ClientRepresentation client = read("{\"clientId\":\"demo\",\"futureFlag\":true}", ClientRepresentation.class);
        assertThat(client.isEnabled()).isNull();
        assertThat(client.isPublicClient()).isNull();
    }

    @Test
    void typedPropertiesAndUnknownVersionFieldsRemainCompatible() {
        ClientRepresentation client = read("""
                {"id":"a","clientId":"demo","enabled":true,"publicClient":false,"protocol":"openid-connect",
                 "redirectUris":["https://app.invalid/*"],"webOrigins":["+"],
                 "attributes":{"pkce.code.challenge.method":"S256"},"future":{"arbitrary":[1,true,null]}}
                """, ClientRepresentation.class);
        assertThat(client.isEnabled()).isTrue();
        assertThat(client.isPublicClient()).isFalse();
        assertThat(client.getAttributes()).containsEntry("pkce.code.challenge.method", "S256");
        ServerInfoRepresentation info = read("""
                {"systemInfo":{"version":"26.7.1","futureFlag":true},"profileInfo":{"name":"default"},
                 "features":[{"name":"organization","enabled":true,"future":true}],"futureTop":true}
                """, ServerInfoRepresentation.class);
        assertThat(info.getSystemInfo().getVersion()).isEqualTo("26.7.1");
        assertThat(info.getFeatures()).hasSize(1);
        assertThat(info.getFeatures().getFirst().isEnabled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "\"[]\"", "[null]", "[true]", "[{}]",
            "[{\"realm\":42}]", "[{\"realm\":\" \"}]", "[{\"realm\":\"a\\nsecret-canary\"}]",
            "[{\"realm\":\"same\"},{\"realm\":\"same\"}]",
            "[{\"realm\":\"one\",\"id\":\"same\"},{\"realm\":\"two\",\"id\":\"same\"}]"})
    void invalidRealmListEnvelopesAndIdentitiesAreRejected(String body) { rejected(body, REALMS); }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "[null]", "[{}]", "[{\"clientId\":42}]",
            "[{\"clientId\":\"same\"},{\"clientId\":\"same\"}]",
            "[{\"clientId\":\"one\",\"id\":\"same\"},{\"clientId\":\"two\",\"id\":\"same\"}]"})
    void invalidClientListEnvelopesAndIdentitiesAreRejected(String body) { rejected(body, CLIENTS); }

    @Test
    void topLevelListsHaveAnExactItemLimitWithoutSilentTruncation() {
        String accepted = realms(CollectionJsonProvider.MAX_LIST_ITEMS);
        assertThat(this.<List<RealmRepresentation>>read(accepted, REALMS)).hasSize(CollectionJsonProvider.MAX_LIST_ITEMS);
        rejected(realms(CollectionJsonProvider.MAX_LIST_ITEMS + 1), REALMS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"false\"", "0", "1", "[]", "{}"})
    void observedBooleanFieldsCannotBeCoerced(String value) {
        rejected("{\"realm\":\"demo\",\"bruteForceProtected\":" + value + "}", RealmRepresentation.class);
        rejected("{\"clientId\":\"demo\",\"publicClient\":" + value + "}", ClientRepresentation.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"clientId\":\"demo\",\"protocol\":1}",
            "{\"clientId\":\"demo\",\"redirectUris\":\"https://app.invalid\"}",
            "{\"clientId\":\"demo\",\"redirectUris\":[null]}",
            "{\"clientId\":\"demo\",\"webOrigins\":[1]}",
            "{\"clientId\":\"demo\",\"attributes\":[]}",
            "{\"clientId\":\"demo\",\"attributes\":{\"pkce.code.challenge.method\":true}}",
            "{\"clientId\":\"demo\",\"attributes\":{\"pkce.code.challenge.method\":null}}"})
    void observedClientStringsListsAndMapsCannotBeCoerced(String body) { rejected(body, ClientRepresentation.class); }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{\"systemInfo\":[]}", "{\"systemInfo\":{\"version\":26}}",
            "{\"profileInfo\":{\"name\":true}}", "{\"features\":{}}", "{\"features\":[null]}",
            "{\"features\":[{\"name\":\"organization\"}]}",
            "{\"features\":[{\"name\":\"organization\",\"enabled\":\"true\"}]}",
            "{\"features\":[{\"name\":\"organization\",\"enabled\":true},{\"name\":\"organization\",\"enabled\":false}]}"})
    void serverMetadataAndFeaturesDoNotManufactureObservations(String body) { rejected(body, ServerInfoRepresentation.class); }

    @ParameterizedTest
    @MethodSource("invalidDocuments")
    void rawDocumentBoundsAndJsonIntegrityAreEnforced(String body) { rejected(body, ServerInfoRepresentation.class); }

    static Stream<String> invalidDocuments() {
        return Stream.of("", " ", "{} {}", "{} []", "{\"secret-canary\":1,\"secret-canary\":2}",
                "{\"future\":\"" + "x".repeat(32_769) + "\"}",
                "{\"" + "x".repeat(257) + "\":true}",
                "{\"future\":" + "1".repeat(129) + "}",
                "{\"future\":" + "[".repeat(64) + "null" + "]".repeat(64) + "}",
                "{\"future\":[" + "0,".repeat(1024) + "0]}",
                "{" + IntStream.range(0, 1025).mapToObj(i -> "\"k" + i + "\":true")
                        .collect(java.util.stream.Collectors.joining(",")) + "}");
    }

    @Test
    void maximumPermittedStringsNamesNumbersAndContainerWidthsAreAccepted() {
        assertThat(this.<ServerInfoRepresentation>read("{\"" + "x".repeat(256) + "\":\"" + "x".repeat(32_768) + "\","
                + "\"number\":" + "1".repeat(128) + ",\"list\":[" + "0,".repeat(1023) + "0]}",
                ServerInfoRepresentation.class)).isNotNull();
    }

    @Test
    void byteLimitAcceptsBoundaryAndRejectsAdditionalWhitespaceForTokenAndAdmin() {
        String admin = "{}" + " ".repeat(CollectionJsonProvider.MAX_BYTES - 2);
        assertThat(this.<ServerInfoRepresentation>read(admin, ServerInfoRepresentation.class)).isNotNull();
        rejected(admin + " ", ServerInfoRepresentation.class);
        String token = TOKEN + " ".repeat(CollectionJsonProvider.MAX_TOKEN_BYTES - TOKEN.length());
        assertThat(this.<AccessTokenResponse>read(token, AccessTokenResponse.class)).isNotNull();
        rejected(token + " ", AccessTokenResponse.class);
    }

    @Test
    void oversizedReaderStopsAfterOneByteLookaheadAndClosesWithoutDrain() {
        AtomicInteger reads = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream input = new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return ' '; }
            @Override public void close() { closed.set(true); }
        };
        assertThatThrownBy(() -> read(input, AccessTokenResponse.class))
                .isInstanceOf(ProcessingException.class).hasMessage(CollectionJsonProvider.FAILURE).hasNoCause();
        assertThat(reads.get()).isEqualTo(CollectionJsonProvider.MAX_TOKEN_BYTES + 1);
        assertThat(closed.get()).isTrue();
    }

    @Test
    void parserAndCloseFailuresContainNeitherRawCauseNorSuppressedDetails() {
        InputStream input = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("wire-secret-canary"); }
            @Override public void close() throws IOException { throw new IOException("close-secret-canary"); }
        };
        assertThatThrownBy(() -> read(input, ServerInfoRepresentation.class))
                .isInstanceOf(ProcessingException.class).hasMessage(CollectionJsonProvider.FAILURE).hasNoCause()
                .satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
    }

    @Test
    void lateReadPreservesCauseFreeBudgetReasonAndClosesTheInput() {
        AtomicLong clock = new AtomicLong();
        AtomicBoolean closed = new AtomicBoolean();
        InputStream input = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)) {
            @Override public byte[] readNBytes(int length) throws IOException {
                clock.set(1_000_000_000L);
                return super.readNBytes(length);
            }
            @Override public void close() throws IOException {
                closed.set(true);
                throw new IOException("close-secret-canary");
            }
        };
        try (var scope = CollectionBudget.open("test", new CollectionBudget(Duration.ofSeconds(1), clock::get))) {
            assertThatThrownBy(() -> read(input, ServerInfoRepresentation.class))
                    .isInstanceOf(CollectionBudget.Aborted.class).hasMessage(CollectionBudget.REASON_EXCEEDED)
                    .hasNoCause().satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
        }
        assertThat(closed.get()).isTrue();
        assertThat(CollectionBudget.current()).isNull();
    }

    @Test
    void scopedReaderDoesNotSupportUnapprovedRepresentationTypes() { rejected("{}", Object.class); }

    private static String realms(int count) {
        return "[" + IntStream.range(0, count).mapToObj(i -> "{\"realm\":\"r" + i + "\"}")
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private void rejected(String body, Type type) {
        assertThatThrownBy(() -> read(body, type)).isInstanceOf(ProcessingException.class)
                .hasMessage(CollectionJsonProvider.FAILURE).hasNoCause()
                .satisfies(failure -> assertThat(failure.getSuppressed()).isEmpty());
    }

    private <T> T read(String body, Type type) {
        return read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), type);
    }

    @SuppressWarnings("unchecked")
    private <T> T read(InputStream input, Type type) {
        Class<?> raw = type instanceof Class<?> clazz ? clazz : List.class;
        return (T) provider.readFrom((Class<Object>) raw, type, new Annotation[0], MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(), input);
    }
}
