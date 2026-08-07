package io.github.keycloakmcp.assessment.engine;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Controlled YAML condition DSL — no scripts, SpEL, or code execution.
 */
public final class ConditionEvaluator {

    private static final Set<String> SCALAR_OPS = Set.of(
            "equals",
            "notEquals",
            "lessThan",
            "lessThanOrEqual",
            "greaterThan",
            "greaterThanOrEqual",
            "exists",
            "notExists",
            "empty",
            "notEmpty",
            "contains",
            "notContains",
            "sizeGreaterThan",
            "sizeLessThan");

    private ConditionEvaluator() {
    }

    public static void validate(Map<?, ?> condition) {
        if (condition == null || condition.isEmpty()) {
            throw new IllegalArgumentException("condition must not be empty");
        }
        if (condition.containsKey("all") || condition.containsKey("any")) {
            Object all = condition.get("all");
            Object any = condition.get("any");
            if (all != null && !(all instanceof List<?>)) {
                throw new IllegalArgumentException("condition.all must be a list");
            }
            if (any != null && !(any instanceof List<?>)) {
                throw new IllegalArgumentException("condition.any must be a list");
            }
            if (all instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) {
                        throw new IllegalArgumentException("condition.all items must be maps");
                    }
                    validateLeaf(m);
                }
            }
            if (any instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) {
                        throw new IllegalArgumentException("condition.any items must be maps");
                    }
                    validateLeaf(m);
                }
            }
            return;
        }
        validateLeaf(condition);
    }

    private static void validateLeaf(Map<?, ?> condition) {
        String key = stringVal(condition.get("key"));
        if (key == null || key.isBlank()) {
            // Legacy replicas form
            if (condition.get("replicas") instanceof Map<?, ?> replicas) {
                validateLeaf(replicas);
                return;
            }
            throw new IllegalArgumentException("condition.key is required");
        }
        boolean hasOp = false;
        for (Object rawKey : condition.keySet()) {
            String k = String.valueOf(rawKey);
            if ("key".equals(k) || "replicas".equals(k)) {
                continue;
            }
            if (!SCALAR_OPS.contains(k)) {
                throw new IllegalArgumentException("Unknown condition operator: " + k);
            }
            hasOp = true;
        }
        if (!hasOp) {
            throw new IllegalArgumentException("condition for key '" + key + "' requires an operator");
        }
    }

    /**
     * @return true if the condition matches (finding should fire), false if it does not match
     * @throws MissingEvidenceException when an evidence key required by the condition is absent
     */
    public static boolean matches(Map<?, ?> condition, EvidenceContext context) {
        if (condition == null || condition.isEmpty()) {
            throw new IllegalArgumentException("condition must not be empty");
        }
        if (condition.containsKey("all")) {
            List<?> list = (List<?>) condition.get("all");
            for (Object item : list) {
                if (!matches((Map<?, ?>) item, context)) {
                    return false;
                }
            }
            return true;
        }
        if (condition.containsKey("any")) {
            List<?> list = (List<?>) condition.get("any");
            boolean anyMatched = false;
            MissingEvidenceException firstMissing = null;
            for (Object item : list) {
                try {
                    if (matches((Map<?, ?>) item, context)) {
                        anyMatched = true;
                        break;
                    }
                } catch (MissingEvidenceException e) {
                    if (firstMissing == null) {
                        firstMissing = e;
                    }
                }
            }
            if (anyMatched) {
                return true;
            }
            if (firstMissing != null) {
                throw firstMissing;
            }
            return false;
        }
        return matchesLeaf(condition, context);
    }

    /**
     * Keys referenced by a condition (for missing-evidence reporting).
     */
    public static List<String> referencedKeys(Map<?, ?> condition) {
        if (condition == null) {
            return List.of();
        }
        if (condition.get("all") instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> referencedKeys((Map<?, ?>) m))
                    .flatMap(Collection::stream)
                    .distinct()
                    .toList();
        }
        if (condition.get("any") instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> referencedKeys((Map<?, ?>) m))
                    .flatMap(Collection::stream)
                    .distinct()
                    .toList();
        }
        if (condition.get("replicas") instanceof Map<?, ?> replicas) {
            return referencedKeys(replicas);
        }
        String key = stringVal(condition.get("key"));
        return key == null ? List.of() : List.of(key);
    }

    private static boolean matchesLeaf(Map<?, ?> condition, EvidenceContext context) {
        Map<?, ?> leaf = condition;
        if (condition.get("replicas") instanceof Map<?, ?> replicas) {
            leaf = replicas;
            if (stringVal(leaf.get("key")) == null) {
                // inject legacy key
                return evaluateOps("deployment.replicas", leaf, context);
            }
        }
        String key = stringVal(leaf.get("key"));
        if (key == null) {
            throw new IllegalArgumentException("condition.key is required");
        }
        return evaluateOps(key, leaf, context);
    }

    private static boolean evaluateOps(String key, Map<?, ?> leaf, EvidenceContext context) {
        if (leaf.containsKey("exists")) {
            boolean expect = Boolean.parseBoolean(String.valueOf(leaf.get("exists")));
            return context.hasKey(key) == expect;
        }
        if (leaf.containsKey("notExists")) {
            boolean expect = Boolean.parseBoolean(String.valueOf(leaf.get("notExists")));
            return (!context.hasKey(key)) == expect;
        }

        boolean needsValue = leaf.containsKey("equals")
                || leaf.containsKey("notEquals")
                || leaf.containsKey("lessThan")
                || leaf.containsKey("lessThanOrEqual")
                || leaf.containsKey("greaterThan")
                || leaf.containsKey("greaterThanOrEqual")
                || leaf.containsKey("empty")
                || leaf.containsKey("notEmpty")
                || leaf.containsKey("contains")
                || leaf.containsKey("notContains")
                || leaf.containsKey("sizeGreaterThan")
                || leaf.containsKey("sizeLessThan");

        if (needsValue && !context.hasKey(key)
                && !leaf.containsKey("empty")
                && !leaf.containsKey("notEmpty")) {
            // empty/notEmpty can mean "key absent" depending on operator
            if (!leaf.containsKey("exists") && !leaf.containsKey("notExists")) {
                throw new MissingEvidenceException(key);
            }
        }

        Object actual = context.find(key).map(Evidence::value).orElse(null);

        if (leaf.containsKey("empty")) {
            boolean expect = Boolean.parseBoolean(String.valueOf(leaf.get("empty")));
            return isEmpty(actual) == expect;
        }
        if (leaf.containsKey("notEmpty")) {
            boolean expect = Boolean.parseBoolean(String.valueOf(leaf.get("notEmpty")));
            return (!isEmpty(actual)) == expect;
        }

        if (!context.hasKey(key)) {
            throw new MissingEvidenceException(key);
        }

        if (leaf.containsKey("equals")) {
            return valuesEqual(actual, leaf.get("equals"));
        }
        if (leaf.containsKey("notEquals")) {
            return !valuesEqual(actual, leaf.get("notEquals"));
        }
        if (leaf.containsKey("lessThan")) {
            return compareNumbers(actual, leaf.get("lessThan")) < 0;
        }
        if (leaf.containsKey("lessThanOrEqual")) {
            return compareNumbers(actual, leaf.get("lessThanOrEqual")) <= 0;
        }
        if (leaf.containsKey("greaterThan")) {
            return compareNumbers(actual, leaf.get("greaterThan")) > 0;
        }
        if (leaf.containsKey("greaterThanOrEqual")) {
            return compareNumbers(actual, leaf.get("greaterThanOrEqual")) >= 0;
        }
        if (leaf.containsKey("contains")) {
            return contains(actual, leaf.get("contains"));
        }
        if (leaf.containsKey("notContains")) {
            return !contains(actual, leaf.get("notContains"));
        }
        if (leaf.containsKey("sizeGreaterThan")) {
            return sizeOf(actual) > toInt(leaf.get("sizeGreaterThan"));
        }
        if (leaf.containsKey("sizeLessThan")) {
            return sizeOf(actual) < toInt(leaf.get("sizeLessThan"));
        }
        throw new IllegalArgumentException("No supported operator found for key " + key);
    }

    private static boolean isEmpty(Object actual) {
        if (actual == null) {
            return true;
        }
        if (actual instanceof String s) {
            return s.isBlank();
        }
        if (actual instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (actual instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    private static int sizeOf(Object actual) {
        if (actual == null) {
            return 0;
        }
        if (actual instanceof Collection<?> c) {
            return c.size();
        }
        if (actual instanceof Map<?, ?> m) {
            return m.size();
        }
        if (actual instanceof String s) {
            return s.length();
        }
        return 0;
    }

    private static boolean contains(Object actual, Object expected) {
        if (actual == null) {
            return false;
        }
        String needle = String.valueOf(expected);
        if (actual instanceof Collection<?> c) {
            return c.stream().anyMatch(v -> String.valueOf(v).equalsIgnoreCase(needle)
                    || String.valueOf(v).contains(needle));
        }
        return String.valueOf(actual).toLowerCase(Locale.ROOT)
                .contains(needle.toLowerCase(Locale.ROOT));
    }

    private static int compareNumbers(Object actual, Object expected) {
        return Double.compare(toDouble(actual), toDouble(expected));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    static boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            return Boolean.parseBoolean(String.valueOf(actual))
                    == Boolean.parseBoolean(String.valueOf(expected));
        }
        if (actual instanceof Number && expected instanceof Number) {
            return ((Number) actual).doubleValue() == ((Number) expected).doubleValue();
        }
        return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static final class MissingEvidenceException extends RuntimeException {
        private final String key;

        public MissingEvidenceException(String key) {
            super("Missing evidence: " + key);
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
