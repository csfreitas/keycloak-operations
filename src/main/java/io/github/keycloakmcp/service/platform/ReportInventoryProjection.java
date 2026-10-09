package io.github.keycloakmcp.service.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Report-only projection; never changes retained inventory or its hash. */
final class ReportInventoryProjection {
    private static final List<String> INSTALLATION = List.of(
            "keycloak", "pods", "topology", "scheduling", "hpa", "pdb", "resources", "probes", "networking");
    private static final Set<String> COUNTS = Set.of("nodeCount", "zoneCount", "desiredReplicas",
            "readyReplicas", "currentReplicas", "availableReplicas", "minReplicas", "maxReplicas", "restartCount");

    private ReportInventoryProjection() {}

    static Map<String, Object> project(Map<String, Object> summary) {
        Map<String, Object> result = copy(summary);
        if (!(result.get("inventory") instanceof Map<?, ?> raw)) return result;
        Map<String, Object> inventory = copy(raw);
        result.put("inventory", inventory);
        boolean unconfigured = "NONE".equals(summary.get("infraType"))
                || warning(inventory, "infrastructure", "NOT_CONFIGURED");
        boolean opaqueFailure = inventory.containsKey("collectionError")
                || inventory.get("warnings") instanceof List<?> warnings
                && warnings.stream().anyMatch(w -> !(w instanceof Map<?, ?> map) || map.get("resource") == null);
        if (unconfigured || opaqueFailure) {
            inventory.put("cluster", null);
            INSTALLATION.forEach(key -> inventory.put(key, null));
            return result;
        }

        fieldsUnknown(inventory, "cluster", warning(inventory, "nodes", null), "nodeCount", "zoneCount");
        fieldsUnknown(inventory, "cluster", warning(inventory, "infrastructure", null)
                || warning(inventory, "openshift-config", null), "platform");
        fieldsUnknown(inventory, "cluster", warning(inventory, "clusterversion", null)
                || warning(inventory, "openshift-config", null), "version");
        if (!(inventory.get("keycloak") instanceof Map<?, ?> kc)
                || !(kc.get("name") instanceof String name) || name.isBlank()
                || warning(inventory, "installation", null)) {
            INSTALLATION.forEach(key -> inventory.put(key, null));
            return result;
        }
        if (warning(inventory, "workload", null)) {
            // A missing pod template need not erase independently observed workload identity/replicas.
            List.of("scheduling", "hpa", "pdb", "resources", "probes", "networking")
                    .forEach(key -> inventory.put(key, null));
        }
        if (warning(inventory, "pods", null)) {
            inventory.put("pods", null);
            inventory.put("topology", null);
        } else if (!(inventory.get("pods") instanceof List<?> pods)) {
            inventory.put("topology", null);
        } else {
            boolean missingZones = warning(inventory, "nodes", null) || warning(inventory, "pod-zones", null)
                    || pods.stream().anyMatch(p -> !(p instanceof Map<?, ?> pod)
                            || !(pod.get("zone") instanceof String zone) || zone.isBlank());
            fieldsUnknown(inventory, "topology", missingZones, "zoneCount", "podsByZone");
        }
        for (String section : List.of("hpa", "pdb")) {
            if (warning(inventory, section, null)) inventory.put(section, null);
        }
        // Preserve associated Services/Routes even when another networking query failed.
        fieldsUnknown(inventory, "networking", warning(inventory, "networking", null),
                "routeOrIngressPresent", "host", "tlsEnabled");
        if (warning(inventory, "networking", null) && inventory.get("networking") instanceof Map<?, ?> rawNetwork) {
            Map<String, Object> network = copy(rawNetwork);
            network.put("complete", false);
            inventory.put("networking", network);
        }
        return result;
    }

    private static void fieldsUnknown(Map<String, Object> inventory, String section, boolean unknown, String... keys) {
        if (unknown && inventory.get(section) instanceof Map<?, ?> raw) {
            Map<String, Object> projected = copy(raw);
            for (String key : keys) projected.put(key, null);
            inventory.put(section, projected);
        }
    }

    private static boolean warning(Map<String, Object> inventory, String resource, String code) {
        return inventory.get("warnings") instanceof List<?> warnings
                && warnings.stream().anyMatch(w -> w instanceof Map<?, ?> map
                        && resource.equals(map.get("resource")) && (code == null || code.equals(map.get("code"))));
    }

    private static Map<String, Object> copy(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input == null) return result;
        input.forEach((key, value) -> {
            if (key != null) result.put(key.toString(), copyValue(key.toString(), value));
        });
        return result;
    }

    private static Object copyValue(String key, Object value) {
        if (value instanceof Map<?, ?> map) return copy(map);
        if (value instanceof List<?> list) return list.stream().map(item -> copyValue("", item)).toList();
        if (COUNTS.contains(key) && value != null) {
            if (!(value instanceof Number number)) return null;
            double n = number.doubleValue();
            if (!Double.isFinite(n) || n < 0 || n != Math.rint(n) || n > Integer.MAX_VALUE) return null;
        }
        return value;
    }
}
