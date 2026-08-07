package io.github.keycloakmcp.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern SENSITIVE_INLINE = Pattern.compile(
            "(?i)(password|secret|token|credential)([\"'\\s:=]+)([^\\s,;\"']+)");

    private final ObjectMapper objectMapper;

    @Inject
    public SensitiveDataFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public <T> T redact(T value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return (T) redactString(s);
        }
        if (value instanceof Map<?, ?> map) {
            return (T) redactMap(map);
        }
        if (value instanceof List<?> list) {
            return (T) redactList(list);
        }
        JavaType type = objectMapper.getTypeFactory().constructType(value.getClass());
        Map<String, Object> asMap = objectMapper.convertValue(value, Map.class);
        Map<String, Object> redacted = redactMap(asMap);
        return objectMapper.convertValue(redacted, type);
    }

    public String redactString(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        return SENSITIVE_INLINE.matcher(message).replaceAll("$1$2" + REDACTED);
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
    private Map<String, Object> redactMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                result.put(key, REDACTED);
            } else if (value instanceof Map<?, ?> nestedMap) {
                result.put(key, redactMap(nestedMap));
            } else if (value instanceof List<?> nestedList) {
                result.put(key, redactList(nestedList));
            } else if (value != null && isLikelyBean(value)) {
                Map<String, Object> asMap = objectMapper.convertValue(value, Map.class);
                result.put(key, redactMap(asMap));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<Object> redactList(List<?> input) {
        List<Object> result = new ArrayList<>(input.size());
        for (Object item : input) {
            if (item instanceof Map<?, ?> map) {
                result.add(redactMap(map));
            } else if (item instanceof List<?> list) {
                result.add(redactList(list));
            } else if (item != null && isLikelyBean(item)) {
                result.add(redact(item));
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
