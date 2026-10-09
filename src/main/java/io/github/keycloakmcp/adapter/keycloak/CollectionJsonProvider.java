package io.github.keycloakmcp.adapter.keycloak;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.keycloakmcp.collection.CollectionBudget;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;

/** Strict wire boundary registered only on the dedicated scoped collection client. */
final class CollectionJsonProvider extends JacksonProvider {
    static final int MAX_BYTES = 1_048_576;
    static final int MAX_TOKEN_BYTES = 65_536;
    static final int MAX_LIST_ITEMS = 500;
    static final int MAX_CONTAINER_ITEMS = 1024;
    static final String FAILURE = "Keycloak collection response unavailable or incomplete";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();
    static {
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxNameLength(256).maxStringLength(32_768)
                .maxNumberLength(128).build());
    }
    // Conversion receives only a fully validated tree. Nested model deserializers may
    // read subtrees; their next sibling is not an additional document on the wire.
    private static final ObjectMapper MODELS = JSON.copy()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, String> headers, InputStream input) {
        try {
            CollectionBudget.checkpointCurrent();
            JavaType model = MODELS.constructType(genericType == null ? type : genericType);
            int limit = model.hasRawClass(AccessTokenResponse.class) ? MAX_TOKEN_BYTES : MAX_BYTES;
            byte[] bytes = input.readNBytes(limit + 1);
            require(bytes.length > 0 && bytes.length <= limit);
            CollectionBudget.checkpointCurrent();
            JsonNode root = JSON.readTree(bytes);
            require(root != null && !root.isNull());
            boundedContainers(root);
            validate(root, model);
            Object result = MODELS.convertValue(root, model);
            CollectionBudget.checkpointCurrent();
            return result;
        } catch (CollectionBudget.Aborted aborted) {
            throw aborted;
        } catch (Exception failure) {
            // Jackson diagnostics can contain raw property values and token material.
            // No parser/conversion/transport cause crosses this boundary.
            throw invalid();
        } finally {
            // The transport wrapper aborts before closing; do not drain a rejected body
            // or attach a close failure as a suppressed exception containing wire data.
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
    }

    private static void validate(JsonNode root, JavaType type) {
        if (type.hasRawClass(AccessTokenResponse.class)) {
            token(root);
        } else if (type.hasRawClass(ServerInfoRepresentation.class)) {
            serverInfo(root);
        } else if (type.hasRawClass(RealmRepresentation.class)) {
            realm(root);
        } else if (type.hasRawClass(ClientRepresentation.class)) {
            client(root);
        } else if (type.hasRawClass(List.class) && type.getContentType() != null) {
            JavaType itemType = type.getContentType();
            boolean realms = itemType.hasRawClass(RealmRepresentation.class);
            require(realms || itemType.hasRawClass(ClientRepresentation.class));
            require(root.isArray() && root.size() <= MAX_LIST_ITEMS);
            Set<String> identities = new HashSet<>(), ids = new HashSet<>();
            for (JsonNode item : root) {
                CollectionBudget.checkpointCurrent();
                if (realms) realm(item); else client(item);
                require(identities.add(item.get(realms ? "realm" : "clientId").textValue()));
                if (item.hasNonNull("id")) require(ids.add(item.get("id").textValue()));
            }
        } else {
            throw invalid();
        }
    }

    private static void token(JsonNode root) {
        require(root.isObject());
        JsonNode access = root.get("access_token"), type = root.get("token_type");
        require(access != null && access.isTextual() && !access.textValue().isEmpty()
                && access.textValue().matches("[\\x21-\\x7E]+"));
        require(type != null && type.isTextual() && "Bearer".equalsIgnoreCase(type.textValue()));
        integer(root.get("expires_in"), true);
        for (String key : List.of("refresh_expires_in", "not-before-policy")) {
            if (root.has(key)) integer(root.get(key), false);
        }
        strings(root, "refresh_token", "id_token", "session_state", "scope");
    }

    private static void integer(JsonNode value, boolean positive) {
        require(value != null && value.isIntegralNumber() && value.canConvertToLong()
                && (positive ? value.longValue() > 0 : value.longValue() >= 0));
    }

    private static void realm(JsonNode root) {
        require(root.isObject());
        identity(root.get("realm"));
        if (root.hasNonNull("id")) identity(root.get("id"));
        booleans(root, "enabled", "bruteForceProtected", "registrationAllowed", "verifyEmail",
                "resetPasswordAllowed", "rememberMe", "loginWithEmailAllowed", "duplicateEmailsAllowed",
                "eventsEnabled", "adminEventsEnabled", "adminEventsDetailsEnabled", "registrationEmailAsUsername",
                "editUsernameAllowed", "revokeRefreshToken", "permanentLockout");
        strings(root, "id", "displayName", "displayNameHtml", "sslRequired", "passwordPolicy", "otpPolicyType");
        stringMap(root, "attributes");
    }

    private static void client(JsonNode root) {
        require(root.isObject());
        identity(root.get("clientId"));
        if (root.hasNonNull("id")) identity(root.get("id"));
        booleans(root, "enabled", "publicClient", "bearerOnly", "standardFlowEnabled", "implicitFlowEnabled",
                "directAccessGrantsEnabled", "serviceAccountsEnabled", "authorizationServicesEnabled",
                "fullScopeAllowed", "frontchannelLogout", "surrogateAuthRequired", "consentRequired",
                "alwaysDisplayInConsole");
        strings(root, "id", "name", "description", "protocol", "rootUrl", "baseUrl", "adminUrl");
        stringList(root, "redirectUris");
        stringList(root, "webOrigins");
        stringMap(root, "attributes");
    }

    private static void serverInfo(JsonNode root) {
        require(root.isObject());
        JsonNode system = root.get("systemInfo");
        if (system != null && !system.isNull()) {
            require(system.isObject());
            strings(system, "version", "product", "productName", "serverName");
        }
        JsonNode profile = root.get("profileInfo");
        if (profile != null && !profile.isNull()) {
            require(profile.isObject());
            strings(profile, "name");
        }
        JsonNode features = root.get("features");
        if (features != null && !features.isNull()) {
            require(features.isArray());
            Set<String> names = new HashSet<>();
            for (JsonNode feature : features) {
                require(feature.isObject());
                identity(feature.get("name"));
                require(names.add(feature.get("name").textValue()));
                // Keycloak's model has a primitive enabled flag: omission must not
                // manufacture an observed disabled capability during conversion.
                require(feature.has("enabled") && feature.get("enabled").isBoolean());
            }
        }
    }

    private static void identity(JsonNode value) {
        require(value != null && value.isTextual() && !value.textValue().isBlank()
                && value.textValue().length() <= 256
                && value.textValue().codePoints().noneMatch(Character::isISOControl));
    }

    private static void strings(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = root.get(key);
            require(value == null || value.isNull() || value.isTextual());
        }
    }

    private static void booleans(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = root.get(key);
            require(value == null || value.isNull() || value.isBoolean());
        }
    }

    private static void stringList(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || value.isNull()) return;
        require(value.isArray());
        for (JsonNode item : value) require(item.isTextual());
    }

    private static void stringMap(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || value.isNull()) return;
        require(value.isObject());
        for (JsonNode item : value) require(item.isTextual());
    }

    private static void boundedContainers(JsonNode node) {
        CollectionBudget.checkpointCurrent();
        if (node.isContainerNode()) {
            require(node.size() <= MAX_CONTAINER_ITEMS);
            for (JsonNode child : node) boundedContainers(child);
        }
    }

    private static void require(boolean condition) { if (!condition) throw invalid(); }
    private static ProcessingException invalid() { return new ProcessingException(FAILURE); }
}
