package io.github.keycloakmcp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class SensitiveDataFilterTest {

    private SensitiveDataFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveDataFilter(new ObjectMapper());
    }

    @Test
    void redactsClientSecretPasswordAndTokenFields() {
        Map<String, Object> input = new HashMap<>();
        input.put("clientSecret", "super-secret");
        input.put("password", "hunter2");
        input.put("token", "eyJhbGciOiJIUzI1NiJ9.fake");
        input.put("accessToken", "access-value");
        input.put("refreshToken", "refresh-value");

        Map<String, Object> redacted = filter.redact(input);

        assertThat(redacted.get("clientSecret")).isEqualTo("[REDACTED]");
        assertThat(redacted.get("password")).isEqualTo("[REDACTED]");
        assertThat(redacted.get("token")).isEqualTo("[REDACTED]");
        assertThat(redacted.get("accessToken")).isEqualTo("[REDACTED]");
        assertThat(redacted.get("refreshToken")).isEqualTo("[REDACTED]");
    }

    @Test
    void preservesNameAndNamespaceMetadata() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", "keycloak");
        input.put("namespace", "rhbk-prod");
        input.put("clientId", "portal-web");
        input.put("secret", "should-hide");

        Map<String, Object> redacted = filter.redact(input);

        assertThat(redacted.get("name")).isEqualTo("keycloak");
        assertThat(redacted.get("namespace")).isEqualTo("rhbk-prod");
        assertThat(redacted.get("clientId")).isEqualTo("portal-web");
        assertThat(redacted.get("secret")).isEqualTo("[REDACTED]");
    }

    @Test
    void redactsNestedMaps() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("clientSecret", "nested-secret");
        nested.put("password", "nested-password");
        nested.put("name", "backend-api");

        Map<String, Object> input = new HashMap<>();
        input.put("client", nested);
        input.put("items", List.of(Map.of("token", "abc", "realm", "mcp-demo")));

        Map<String, Object> redacted = filter.redact(input);

        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) redacted.get("client");
        assertThat(client.get("clientSecret")).isEqualTo("[REDACTED]");
        assertThat(client.get("password")).isEqualTo("[REDACTED]");
        assertThat(client.get("name")).isEqualTo("backend-api");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) redacted.get("items");
        assertThat(items.get(0).get("token")).isEqualTo("[REDACTED]");
        assertThat(items.get(0).get("realm")).isEqualTo("mcp-demo");
    }

    @Test
    void isSensitiveKeyDetectsVariants() {
        assertThat(filter.isSensitiveKey("clientSecret")).isTrue();
        assertThat(filter.isSensitiveKey("CLIENT_SECRET")).isTrue();
        assertThat(filter.isSensitiveKey("db-password")).isTrue();
        assertThat(filter.isSensitiveKey("name")).isFalse();
        assertThat(filter.isSensitiveKey("namespace")).isFalse();
        assertThat(filter.isSensitiveKey("resetPasswordAllowed")).isFalse();
        assertThat(filter.isSensitiveKey("loginWithEmailAllowed")).isFalse();
    }

    @Test
    void doesNotCorruptBooleanConfigFlagsOnRecords() {
        record RealmFlags(String realm, boolean resetPasswordAllowed, boolean enabled) {}
        RealmFlags input = new RealmFlags("mcp-demo", true, true);
        RealmFlags redacted = filter.redact(input);
        assertThat(redacted.resetPasswordAllowed()).isTrue();
        assertThat(redacted.enabled()).isTrue();
        assertThat(redacted.realm()).isEqualTo("mcp-demo");
    }
}
