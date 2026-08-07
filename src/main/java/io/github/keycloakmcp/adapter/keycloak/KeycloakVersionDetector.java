package io.github.keycloakmcp.adapter.keycloak;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.keycloak.representations.info.FeatureRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import io.github.keycloakmcp.domain.common.ServerInfo;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class KeycloakVersionDetector {

    public ServerInfo.Product detectProduct(String versionOrProductHint) {
        return detectProduct(versionOrProductHint, Map.of());
    }

    public ServerInfo.Product detectProduct(String versionOrProductHint, Map<String, ?> serverInfo) {
        String productName = firstNonBlank(
                stringValue(serverInfo, "product"),
                stringValue(serverInfo, "productName"),
                stringValue(serverInfo, "serverName"),
                nestedString(serverInfo, "systemInfo", "product"),
                nestedString(serverInfo, "systemInfo", "productName"),
                nestedString(serverInfo, "profileInfo", "name"),
                versionOrProductHint);

        if (productName == null || productName.isBlank()) {
            return ServerInfo.Product.UNKNOWN;
        }

        String normalized = productName.toLowerCase(Locale.ROOT);
        if (normalized.contains("red hat") || normalized.contains("rhbk") || normalized.contains("build of keycloak")) {
            return ServerInfo.Product.RHBK;
        }
        if (normalized.contains("keycloak")) {
            return ServerInfo.Product.KEYCLOAK;
        }
        return ServerInfo.Product.UNKNOWN;
    }

    public ServerInfo.Product detectProduct(ServerInfoRepresentation serverInfo) {
        if (serverInfo == null) {
            return ServerInfo.Product.UNKNOWN;
        }
        SystemInfoRepresentation systemInfo = serverInfo.getSystemInfo();
        String systemProduct = readStringProperty(systemInfo, "getProduct", "getProductName", "getServerName");
        String profileName = null;
        if (serverInfo.getProfileInfo() != null) {
            profileName = serverInfo.getProfileInfo().getName();
        }
        String version = systemInfo == null ? null : systemInfo.getVersion();
        ServerInfo.Product detected = detectProduct(firstNonBlank(systemProduct, profileName, version), Map.of());
        if (detected != ServerInfo.Product.UNKNOWN) {
            return detected;
        }
        // Community Keycloak often omits productName; profile "default" is not a product marker.
        // If Admin API features are visible, treat as Keycloak unless RHBK markers appear later.
        Map<String, Boolean> features = extractFeatureFlags(serverInfo);
        if (featureEnabled(features, "ADMIN_API", "ADMIN_V2") || version != null) {
            return ServerInfo.Product.KEYCLOAK;
        }
        return ServerInfo.Product.UNKNOWN;
    }

    public Optional<String> parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        int start = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isDigit(trimmed.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return Optional.of(trimmed);
        }
        int end = start;
        while (end < trimmed.length()) {
            char c = trimmed.charAt(end);
            if (Character.isDigit(c) || c == '.' || c == '-') {
                end++;
            } else {
                break;
            }
        }
        String candidate = trimmed.substring(start, end);
        int redhatIdx = candidate.toLowerCase(Locale.ROOT).indexOf(".redhat");
        if (redhatIdx > 0) {
            candidate = candidate.substring(0, redhatIdx);
        }
        while (candidate.endsWith(".") || candidate.endsWith("-")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return Optional.of(candidate);
    }

    public KeycloakCapabilities detectCapabilities(ServerInfoRepresentation serverInfo) {
        if (serverInfo == null) {
            return new KeycloakCapabilities(null, false, false, false, false, false);
        }
        SystemInfoRepresentation systemInfo = serverInfo.getSystemInfo();
        String version = systemInfo == null ? null : parseVersion(systemInfo.getVersion()).orElse(systemInfo.getVersion());
        return detectCapabilities(version, extractFeatureFlags(serverInfo));
    }

    public KeycloakCapabilities detectCapabilities(Map<String, ?> serverInfo) {
        String version = parseVersion(firstNonBlank(
                stringValue(serverInfo, "version"),
                nestedString(serverInfo, "systemInfo", "version")))
                .orElse(stringValue(serverInfo, "version"));
        return detectCapabilities(version, extractFeatureFlags(serverInfo));
    }

    public KeycloakCapabilities detectCapabilities(String version, Map<String, Boolean> features) {
        Map<String, Boolean> safeFeatures = features == null ? Map.of() : features;
        boolean organizations = featureEnabled(safeFeatures, "ORGANIZATION", "organizations", "organization");
        boolean fgapV2 = featureEnabled(safeFeatures,
                "ADMIN_FINE_GRAINED_AUTHZ_V2",
                "admin-fine-grained-authz-v2",
                "fineGrainedAdminPermissionsV2");
        // Admin API v2 is intentionally not used as the primary path in 0.1.0
        boolean adminApiV2 = false;
        boolean workflows = featureEnabled(safeFeatures, "WORKFLOWS", "workflows", "workflow");
        boolean scim = featureEnabled(safeFeatures, "SCIM", "scim");
        return new KeycloakCapabilities(version, organizations, fgapV2, adminApiV2, workflows, scim);
    }

    private static boolean featureEnabled(Map<String, Boolean> features, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                if (entry.getKey() != null
                        && entry.getKey().equalsIgnoreCase(key)
                        && Boolean.TRUE.equals(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Boolean> extractFeatureFlags(ServerInfoRepresentation serverInfo) {
        List<FeatureRepresentation> features = serverInfo.getFeatures();
        if (features == null || features.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        for (FeatureRepresentation feature : features) {
            if (feature == null || feature.getName() == null) {
                continue;
            }
            result.put(feature.getName(), feature.isEnabled());
        }
        return result;
    }

    private static Map<String, Boolean> extractFeatureFlags(Map<String, ?> serverInfo) {
        if (serverInfo == null) {
            return Map.of();
        }
        Object features = serverInfo.get("features");
        if (features == null) {
            features = serverInfo.get("feature");
        }
        return normalizeFeatures(features);
    }

    private static Map<String, Boolean> normalizeFeatures(Object features) {
        if (features == null) {
            return Map.of();
        }
        if (features instanceof Map<?, ?> map) {
            LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                Object value = entry.getValue();
                boolean enabled = value instanceof Boolean b ? b
                        : value instanceof String s && ("ENABLED".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s));
                result.put(String.valueOf(entry.getKey()), enabled);
            }
            return result;
        }
        if (features instanceof Collection<?> collection) {
            LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                if (item instanceof String name) {
                    result.put(name, true);
                } else if (item instanceof Map<?, ?> itemMap) {
                    Object name = firstNonNull(itemMap.get("name"), itemMap.get("id"), itemMap.get("feature"));
                    Object enabled = firstNonNull(itemMap.get("enabled"), itemMap.get("status"), Boolean.TRUE);
                    if (name != null) {
                        boolean on = enabled instanceof Boolean b ? b
                                : enabled instanceof String s
                                        && ("ENABLED".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s));
                        result.put(String.valueOf(name), on);
                    }
                } else if (item instanceof FeatureRepresentation feature) {
                    if (feature.getName() != null) {
                        result.put(feature.getName(), feature.isEnabled());
                    }
                }
            }
            return result;
        }
        return Map.of();
    }

    private static String readStringProperty(Object target, String... getters) {
        if (target == null || getters == null) {
            return null;
        }
        for (String getter : getters) {
            try {
                Object value = target.getClass().getMethod(getter).invoke(target);
                if (value != null) {
                    String text = String.valueOf(value);
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // method not present on this Keycloak version
            }
        }
        return null;
    }

    private static String nestedString(Map<String, ?> map, String parent, String child) {
        if (map == null) {
            return null;
        }
        Object nested = map.get(parent);
        if (nested instanceof Map<?, ?> nestedMap) {
            Object value = nestedMap.get(child);
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private static String stringValue(Map<String, ?> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
