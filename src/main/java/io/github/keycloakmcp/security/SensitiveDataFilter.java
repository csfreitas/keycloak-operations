package io.github.keycloakmcp.security;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SensitiveDataFilter {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "clientsecret",
            "secret",
            "credentials",
            "accesstoken",
            "refreshtoken",
            "privatekey",
            "private_key",
            "token");

    private static final Set<String> SENSITIVE_METADATA_KEYS = Set.of(
            "authorization", "proxyauthorization", "cookie", "setcookie", "apikey", "xapikey");
    private static final String SECRET_LABEL =
            "[a-z0-9_.-]*(?:password|passwd|secret|token|private[-_.]?key|api[-_.]?key|credentials?)";
    private static final String QUOTED_VALUE =
            "\"(?:\\\\.|[^\"\\\\])*+(?:\"|\\z)|'(?:\\\\.|[^'\\\\])*+(?:'|\\z)";
    // Require an assignment, not prose such as "token lifespan" or "password policy".
    private static final Pattern SENSITIVE_INLINE = Pattern.compile(
            "(?i)(?<![a-z0-9_.-])((?:" + SECRET_LABEL
                    + "|authorization|proxy[-_.]?authorization)[\"']?\\s*[:=]\\s*)"
                    + "(" + QUOTED_VALUE + "|\\\\\\[REDACTED\\\\\\]|\\[REDACTED\\]|[^\\s,;\"'<>}\\])]+)");
    private static final Pattern QUERY_CREDENTIAL = Pattern.compile(
            "(?i)([?&](?:" + SECRET_LABEL + ")=)[^&#\\s\"'<>]*");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN (?:[A-Z0-9]{1,20} ){0,4}PRIVATE KEY-----.*?"
                    + "(?:-----END (?:[A-Z0-9]{1,20} ){0,4}PRIVATE KEY-----|\\z)");
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]*){2,4}");
    private static final Pattern URI_USERINFO = Pattern.compile(
            "(?i)(?<![a-z0-9+.-])([a-z][a-z0-9+.-]*://)[^\\s/?#@<>\"']+@");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?im)(\\b(?:proxy[-_.]?)?authorization[\"']?\\h*[:=]\\h*)"
                    + "(" + QUOTED_VALUE + "|[^\\r\\n]+)");
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(\\bBearer\\s+)(?!(?:tokens?|authentication|credentials?|authorization|scheme)\\b)"
                    + "[A-Za-z0-9._~+/-]+=*");
    private static final Pattern COOKIE_HEADER = Pattern.compile(
            "(?im)(\\b(?:set-cookie|cookie)\\s*:\\s*)[^\\r\\n]+");

    private final ObjectMapper objectMapper;

    @Inject
    public SensitiveDataFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public <T> T redact(T value) {
        return redact(value, false);
    }

    /**
     * Redacts recognizable credentials in JSON-shaped presentation/persistence metadata,
     * including string leaves and dynamic keys. Never use this lossy projection as
     * rule input, desired change state, or applied configuration. It is not a general
     * secret detector or an instruction/prompt-injection sanitizer.
     */
    public <T> T redactMetadata(T value) {
        return redact(value, true);
    }

    @SuppressWarnings("unchecked")
    private <T> T redact(T value, boolean metadata) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return (T) redactString(s);
        }
        if (value instanceof Map<?, ?> map) {
            return (T) redactMap(map, metadata);
        }
        if (value instanceof List<?> list) {
            return (T) redactList(list, metadata);
        }
        if (!isLikelyBean(value)) {
            return value;
        }
        JavaType type = objectMapper.getTypeFactory().constructType(value.getClass());
        Map<String, Object> asMap = objectMapper.convertValue(value, Map.class);
        Map<String, Object> redacted = redactMap(asMap, metadata);
        return objectMapper.convertValue(redacted, type);
    }

    public String redactString(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String safe = PRIVATE_KEY.matcher(message).replaceAll(Matcher.quoteReplacement(REDACTED));
        safe = JWT.matcher(safe).replaceAll(Matcher.quoteReplacement(REDACTED));
        safe = URI_USERINFO.matcher(safe).replaceAll("$1" + REDACTED + "@");
        safe = redactAssignments(AUTHORIZATION, safe);
        safe = BEARER.matcher(safe).replaceAll("$1" + REDACTED);
        safe = COOKIE_HEADER.matcher(safe).replaceAll("$1" + REDACTED);
        safe = QUERY_CREDENTIAL.matcher(safe).replaceAll("$1" + REDACTED);
        return redactAssignments(SENSITIVE_INLINE, safe);
    }

    private static String redactAssignments(Pattern pattern, String text) {
        return pattern.matcher(text).replaceAll(match -> {
            String value = match.group(2);
            if (value.equals("\\[REDACTED\\]")) {
                return Matcher.quoteReplacement(match.group(1) + value);
            }
            String quote = value.startsWith("\"") ? "\"" : value.startsWith("'") ? "'" : "";
            return Matcher.quoteReplacement(match.group(1) + quote + REDACTED + quote);
        });
    }

    public boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(".", "");
        if (SENSITIVE_KEYS.contains(normalized)) {
            return true;
        }
        // Match secret-bearing field names without redacting config flags like resetPasswordAllowed
        return normalized.endsWith("secret")
                || normalized.endsWith("password")
                || normalized.endsWith("token")
                || normalized.endsWith("privatekey")
                || normalized.contains("credential");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redactMap(Map<?, ?> input, boolean metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> reservedKeys = new HashSet<>();
        input.keySet().forEach(key -> reservedKeys.add(key == null ? null : String.valueOf(key)));
        int suffix = 2;
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
            String outputKey = metadata ? redactString(key) : key;
            if (metadata && !java.util.Objects.equals(key, outputKey)) {
                // Reserve original keys so ordering cannot overwrite a legitimate entry.
                String base = "[REDACTED KEY]";
                outputKey = base;
                while (reservedKeys.contains(outputKey) || result.containsKey(outputKey)) {
                    outputKey = base + " [entry " + suffix++ + "]";
                }
            }
            Object value = entry.getValue();
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[-_.]", "");
            if (isSensitiveKey(key) || metadata && SENSITIVE_METADATA_KEYS.contains(normalized)) {
                result.put(outputKey, REDACTED);
            } else if (value instanceof Map<?, ?> nestedMap) {
                result.put(outputKey, redactMap(nestedMap, metadata));
            } else if (value instanceof List<?> nestedList) {
                result.put(outputKey, redactList(nestedList, metadata));
            } else if (metadata && value instanceof String text) {
                result.put(outputKey, redactString(text));
            } else if (value != null && isLikelyBean(value)) {
                Map<String, Object> asMap = objectMapper.convertValue(value, Map.class);
                result.put(outputKey, redactMap(asMap, metadata));
            } else {
                result.put(outputKey, value);
            }
        }
        return result;
    }

    private List<Object> redactList(List<?> input, boolean metadata) {
        List<Object> result = new ArrayList<>(input.size());
        for (Object item : input) {
            if (item instanceof Map<?, ?> map) {
                result.add(redactMap(map, metadata));
            } else if (item instanceof List<?> list) {
                result.add(redactList(list, metadata));
            } else if (metadata && item instanceof String text) {
                result.add(redactString(text));
            } else if (item != null && isLikelyBean(item)) {
                result.add(redact(item, metadata));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static boolean isLikelyBean(Object value) {
        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || Number.class.isAssignableFrom(type)
                || value instanceof Boolean || value instanceof Character || value instanceof String) {
            return false;
        }
        Package pkg = type.getPackage();
        if (pkg == null) {
            return true;
        }
        String name = pkg.getName();
        return !(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta."));
    }
}
