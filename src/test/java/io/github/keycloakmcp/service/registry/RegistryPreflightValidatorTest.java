package io.github.keycloakmcp.service.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RegistryPreflightValidatorTest {
    private final RegistryPreflightValidator validator = new RegistryPreflightValidator();
    private static final JsonMapper JSON = new JsonMapper();
    private static final String VALID = """
            {"operation":"CREATE","targetId":"candidate-a","displayName":"Environment A",
             "productType":"RHBK","environment":"TEST","keycloak":{
             "url":"https://sso.example.invalid/auth","authRealm":"master",
             "clientId":"operations","credentialRef":"candidate-credential"}}
            """;

    @Test
    void validDraftIsNotTrimmedAndItsStringRepresentationIsRedacted() {
        var result = validate(VALID);
        assertThat(result.issues()).isEmpty();
        assertThat(result.draft().targetId()).isEqualTo("candidate-a");
        assertThat(result.draft().keycloak().url()).isEqualTo("https://sso.example.invalid/auth");
        assertThat(result.draft().toString()).isEqualTo("RegistryDraft[redacted]");
        assertThat(result.draft().keycloak().toString()).isEqualTo("KeycloakDraft[redacted]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://sso.example.invalid", "https://sso.example.invalid:8443/auth/",
            "https://keycloak.svc.cluster.local/", "https://127.0.0.1:443", "https://LOCALHOST/a_b-1.~"})
    void syntaxCanBeValidWithoutMakingAnyConnectivityOrDestinationSafetyClaim(String endpoint) throws Exception {
        assertThat(validate(changed("keycloak.url", endpoint)).issues()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://sso.example.invalid", "HTTPS://sso.example.invalid", "https:host",
            "https://user:secret@sso.example.invalid", "https://sso.example.invalid?token=secret",
            "https://sso.example.invalid#fragment", "https://sso.example.invalid:", "https://sso.example.invalid:0",
            "https://sso.example.invalid:65536", "https://sso.example.invalid:0443", "https://sso.example.invalid:-1",
            "https://sso.example.invalid./", "https://sso..example.invalid/", "https://-host.invalid/",
            "https://host-.invalid/", "https://host_name.invalid/", "https://[::1]/", "https://éxample.invalid/",
            "https://sso.example.invalid/%2e%2e/admin", "https://sso.example.invalid/../admin",
            "https://sso.example.invalid/./admin", "https://sso.example.invalid//admin",
            "https://sso.example.invalid/a;param", "https://sso.example.invalid/a\\b", "https://sso.example.invalid/a b",
            " https://sso.example.invalid", "https://sso.example.invalid\n", "file:///tmp/secret", ""})
    void rejectsAmbiguousOrUnsupportedEndpointSyntaxWithoutEcho(String endpoint) throws Exception {
        var result = validate(changed("keycloak.url", endpoint));
        assertThat(result.draft()).isNull();
        assertThat(result.issues()).containsExactly(new RegistryPreflightResult.Issue("keycloak.url", "INVALID_HTTPS_ENDPOINT"));
        assertThat(result.toString()).doesNotContain("secret", "example.invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEV", "TEST", "HML", "STAGING", "PRD", "UNKNOWN"})
    void acceptsOnlyExplicitEnvironmentNames(String environment) throws Exception {
        assertThat(validate(changed("environment", environment)).issues()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "PROD", "dev", "", " TEST", "TEST ", "OTHER"})
    void doesNotSilentlyNormalizeEnvironment(String environment) throws Exception {
        assertThat(validate(changed("environment", environment)).issues())
                .containsExactly(new RegistryPreflightResult.Issue("environment", "INVALID_ENVIRONMENT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"operation", "targetId", "displayName", "productType", "environment",
            "keycloak.url", "keycloak.authRealm", "keycloak.clientId", "keycloak.credentialRef"})
    void allScalarFieldsMustBeStrings(String field) throws Exception {
        for (String wrongType : new String[] {"null", "true", "42", "1.2", "[]", "{}"}) {
            ObjectNode root = (ObjectNode) JSON.readTree(VALID);
            parent(root, field).set(leaf(field), JSON.readTree(wrongType));
            assertThat(validate(root.toString()).draft()).as(field + "=" + wrongType).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"operation", "targetId", "displayName", "productType", "environment", "keycloak",
            "keycloak.url", "keycloak.authRealm", "keycloak.clientId", "keycloak.credentialRef"})
    void allFieldsAreRequired(String field) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(VALID);
        parent(root, field).remove(leaf(field));
        assertThat(validate(root.toString()).draft()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret", "password", "actor", "grants", "enabled", "tags", "owner", "revision",
            "infrastructure", "observability", "connectionId", "keycloak.clientSecret", "keycloak.managementUrl"})
    void closedShapeRejectsUnknownAndFuturePrivilegeOrCredentialFields(String field) throws Exception {
        var result = validate(changed(field, "do-not-echo-this-value"));
        assertThat(result.draft()).isNull();
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().getFirst().code()).isEqualTo("INVALID_SHAPE");
        assertThat(result.toString()).doesNotContain("do-not-echo-this-value", field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPLACE", "UPDATE", "DELETE", "create", " CREATE"})
    void createIsTheOnlyDraftOperation(String operation) throws Exception {
        assertThat(validate(changed("operation", operation)).issues())
                .containsExactly(new RegistryPreflightResult.Issue("operation", "UNSUPPORTED_OPERATION"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"targetId", "keycloak.authRealm", "keycloak.clientId", "keycloak.credentialRef"})
    void identifiersHaveExplicitBoundsAndNoSilentTrim(String field) throws Exception {
        assertThat(validate(changed(field, "a".repeat(128))).issues()).isEmpty();
        for (String value : new String[] {"a".repeat(129), "", " ", "a b", " a", "a ", "a/b", "a*", "a\n", "a\u200B"}) {
            assertThat(validate(changed(field, value)).draft()).as(field + " invalid identifier").isNull();
        }
    }

    @Test
    void displayNameHasBoundedUnicodeWithoutControlsFormattingOrUnpairedSurrogates() throws Exception {
        assertThat(validate(changed("displayName", "Ambiente São Paulo 🧪")).issues()).isEmpty();
        assertThat(validate(changed("displayName", "a".repeat(255))).issues()).isEmpty();
        for (String value : new String[] {"a".repeat(256), "", " ", " name", "name ", "a\nb", "a\u200Bb", "a\u202Eb"}) {
            assertThat(validate(changed("displayName", value)).draft()).isNull();
        }
        // Send the JSON escape: encoding an unpaired Java surrogate directly replaces it before parsing.
        assertThat(validate(VALID.replace("Environment A", "\\uD800")).draft()).isNull();
    }

    @Test
    void hostnameAndUrlHaveFiniteBounds() throws Exception {
        assertThat(validate(changed("keycloak.url", "https://" + "a".repeat(64) + ".invalid")).draft()).isNull();
        assertThat(validate(changed("keycloak.url", "https://a.invalid/" + "b".repeat(1024))).draft()).isNull();
        String endpoint = "https://a.invalid/";
        assertThat(validate(changed("keycloak.url", endpoint + "b".repeat(1024 - endpoint.length()))).issues()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("malformedBodies")
    void malformedOrOverspecifiedJsonIsRejectedWithoutRawDiagnostics(String input) {
        var result = validate(input);
        assertThat(result.draft()).isNull();
        assertThat(result.toString()).doesNotContain("private-canary", "example.invalid");
    }

    static Stream<String> malformedBodies() {
        return Stream.of("", " ", "null", "[]", "\"private-canary\"", "{", VALID + " {}",
                VALID.replace("\"operation\":", "\"operation\":\"CREATE\",\"operation\":"),
                VALID.replace("\"url\":", "\"url\":\"private-canary\",\"url\":"),
                "{\"private-canary\":" + "[".repeat(10) + "0" + "]".repeat(10) + "}",
                VALID.replace("\"operation\"", "/*private-canary*/\"operation\""),
                VALID.replace("\"CREATE\"", "'CREATE'"));
    }

    @Test
    void readsAtMostLimitPlusOneByteAndDoesNotCloseCallerStream() {
        int[] count = {0};
        boolean[] closed = {false};
        InputStream endless = new InputStream() {
            @Override public int read() { count[0]++; return ' '; }
            @Override public void close() { closed[0] = true; }
        };
        var result = validator.validate(endless);
        assertThat(count[0]).isEqualTo(8193);
        assertThat(closed[0]).isFalse();
        assertThat(result.issues()).containsExactly(new RegistryPreflightResult.Issue("body", "BODY_TOO_LARGE"));
    }

    @Test
    void bodyLimitIsBytesAndInclusive() {
        int padding = 8192 - VALID.getBytes(StandardCharsets.UTF_8).length;
        assertThat(validate(VALID + " ".repeat(padding)).issues()).isEmpty();
        assertThat(validate(VALID + " ".repeat(padding + 1)).issues().getFirst().code()).isEqualTo("BODY_TOO_LARGE");
        assertThat(validate(VALID + "é".repeat(4096)).issues().getFirst().code()).isEqualTo("BODY_TOO_LARGE");
    }

    @Test
    void inputFailuresHaveNoRawCauseOrValues() {
        var result = validator.validate(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("private-canary"); }
        });
        assertThat(result.issues()).containsExactly(new RegistryPreflightResult.Issue("body", "INVALID_JSON"));
        assertThat(result.toString()).doesNotContain("private-canary");
        assertThat(validator.validate(null).draft()).isNull();
    }

    private RegistryPreflightValidator.Validation validate(String json) {
        return validator.validate(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String changed(String field, String value) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(VALID);
        parent(root, field).put(leaf(field), value);
        return root.toString();
    }

    private static ObjectNode parent(ObjectNode root, String field) {
        return field.startsWith("keycloak.") ? (ObjectNode) root.get("keycloak") : root;
    }

    private static String leaf(String field) { return field.substring(field.lastIndexOf('.') + 1); }
}
