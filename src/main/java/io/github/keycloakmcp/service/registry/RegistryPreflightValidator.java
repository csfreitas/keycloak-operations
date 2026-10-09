package io.github.keycloakmcp.service.registry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Issue;
import jakarta.enterprise.context.ApplicationScoped;

/** Parses a bounded, closed administrative draft; never performs name resolution or remote I/O. */
@ApplicationScoped
public class RegistryPreflightValidator {
    public static final int MAX_BODY_BYTES = 8192;
    private static final Set<String> ROOT_FIELDS = Set.of("operation", "targetId", "displayName",
            "productType", "environment", "keycloak");
    private static final Set<String> KEYCLOAK_FIELDS = Set.of("url", "authRealm", "clientId", "credentialRef");
    private static final Set<String> PRODUCTS = Set.of("KEYCLOAK", "RHBK");
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "HML", "STAGING", "PRD", "UNKNOWN");
    private static final JsonMapper JSON = mapper();

    public record KeycloakDraft(String url, String authRealm, String clientId, String credentialRef) {
        @Override public String toString() { return "KeycloakDraft[redacted]"; }
    }
    public record Draft(String targetId, String displayName, String productType, String environment,
            KeycloakDraft keycloak) {
        @Override public String toString() { return "RegistryDraft[redacted]"; }
    }
    public record Validation(Draft draft, List<Issue> issues) {
        public Validation { issues = List.copyOf(issues); }
    }

    public Validation validate(InputStream body) {
        if (body == null) return rejected("body", "INVALID_JSON");
        JsonNode root;
        try {
            byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) return rejected("body", "BODY_TOO_LARGE");
            root = JSON.readTree(bytes);
        } catch (IOException | RuntimeException malformed) {
            // Do not retain Jackson diagnostics, which can contain submitted values or field names.
            return rejected("body", "INVALID_JSON");
        }
        if (!closedObject(root, ROOT_FIELDS)) return rejected("body", "INVALID_SHAPE");
        JsonNode keycloak = root.get("keycloak");
        if (!closedObject(keycloak, KEYCLOAK_FIELDS)) return rejected("keycloak", "INVALID_SHAPE");

        List<Issue> issues = new ArrayList<>();
        String operation = text(root, "operation"), id = text(root, "targetId");
        String name = text(root, "displayName"), product = text(root, "productType");
        String environment = text(root, "environment"), url = text(keycloak, "url");
        String realm = text(keycloak, "authRealm"), client = text(keycloak, "clientId");
        String reference = text(keycloak, "credentialRef");
        if (!"CREATE".equals(operation)) issues.add(new Issue("operation", "UNSUPPORTED_OPERATION"));
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,128}")) issues.add(new Issue("targetId", "INVALID_IDENTIFIER"));
        if (!displayName(name)) issues.add(new Issue("displayName", "INVALID_DISPLAY_NAME"));
        if (product == null || !PRODUCTS.contains(product)) issues.add(new Issue("productType", "INVALID_PRODUCT_TYPE"));
        if (environment == null || !ENVIRONMENTS.contains(environment)) issues.add(new Issue("environment", "INVALID_ENVIRONMENT"));
        if (!endpoint(url)) issues.add(new Issue("keycloak.url", "INVALID_HTTPS_ENDPOINT"));
        if (!identifier(realm)) issues.add(new Issue("keycloak.authRealm", "INVALID_IDENTIFIER"));
        if (!identifier(client)) issues.add(new Issue("keycloak.clientId", "INVALID_IDENTIFIER"));
        if (!identifier(reference)) issues.add(new Issue("keycloak.credentialRef", "INVALID_IDENTIFIER"));
        return new Validation(issues.isEmpty() ? new Draft(id, name, product, environment,
                new KeycloakDraft(url, realm, client, reference)) : null, issues);
    }

    private static boolean closedObject(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || node.size() != fields.size()) return false;
        var names = node.fieldNames();
        while (names.hasNext()) if (!fields.contains(names.next())) return false;
        return true;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}");
    }

    private static boolean displayName(String value) {
        return value != null && !value.isBlank() && value.length() <= 255 && value.equals(value.strip())
                && value.codePoints().noneMatch(cp -> Character.isISOControl(cp)
                        || Character.getType(cp) == Character.FORMAT || cp >= 0xD800 && cp <= 0xDFFF);
    }

    private static boolean endpoint(String value) {
        if (value == null || value.length() > 1024 || !value.matches("[\\x21-\\x7E]+")) return false;
        try {
            URI uri = new URI(value);
            String host = uri.getHost(), path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || uri.isOpaque() || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || host == null
                    || host.length() > 253 || uri.getPort() == 0 || uri.getPort() > 65535
                    || !host.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")
                    || !uri.getRawAuthority().equals(host + (uri.getPort() == -1 ? "" : ":" + uri.getPort()))) return false;
            for (String label : host.split("\\.", -1)) {
                if (label.length() > 63 || !label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")) return false;
            }
            if (!path.matches("(?:/[A-Za-z0-9._~-]*)*") || path.contains("//")) return false;
            for (String segment : path.split("/", -1)) if (".".equals(segment) || "..".equals(segment)) return false;
            return true;
        } catch (URISyntaxException malformed) {
            return false;
        }
    }

    private static Validation rejected(String field, String code) {
        return new Validation(null, List.of(new Issue(field, code)));
    }

    private static JsonMapper mapper() {
        JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).build();
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(4).maxNameLength(64).maxStringLength(2048).maxNumberLength(32).build());
        return mapper;
    }
}
