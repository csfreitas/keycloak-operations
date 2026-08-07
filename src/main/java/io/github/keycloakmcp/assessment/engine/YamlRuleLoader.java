package io.github.keycloakmcp.assessment.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import io.github.keycloakmcp.assessment.engine.rules.MinimumReplicasRule;
import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.config.AssessmentConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads YAML rule packs listed in {@code rules/index.yaml} (JAR-safe).
 * Duplicate rule IDs across packs fail startup with a clear error.
 */
@ApplicationScoped
public class YamlRuleLoader {

    private static final Logger LOG = Logger.getLogger(YamlRuleLoader.class);
    private static final String INDEX = "rules/index.yaml";

    private final AssessmentConfig assessmentConfig;
    private final Map<String, List<Rule>> rulesByPack = new LinkedHashMap<>();
    private final List<Rule> allRules = new ArrayList<>();

    @Inject
    public YamlRuleLoader(AssessmentConfig assessmentConfig) {
        this.assessmentConfig = assessmentConfig;
    }

    @PostConstruct
    void init() {
        loadIndex();
    }

    public List<Rule> loadBuiltInAndClasspathRules() {
        return List.copyOf(allRules);
    }

    public List<Rule> loadForProfile(AssessmentProfile profile) {
        if (profile == null || profile.rulePackIds() == null || profile.rulePackIds().isEmpty()) {
            return loadBuiltInAndClasspathRules();
        }
        LinkedHashMap<String, Rule> selected = new LinkedHashMap<>();
        for (String packId : profile.rulePackIds()) {
            List<Rule> packRules = rulesByPack.get(packId);
            if (packRules == null) {
                LOG.warnf("Assessment profile %s references unknown rule pack '%s'", profile.name(), packId);
                continue;
            }
            for (Rule rule : packRules) {
                selected.putIfAbsent(rule.id(), rule);
            }
        }
        // Always include Java MinimumReplicasRule when HA pack is selected and YAML did not supply it
        if (profile.rulePackIds().contains("ha") && !selected.containsKey(MinimumReplicasRule.FINDING_ID)) {
            selected.put(MinimumReplicasRule.FINDING_ID, new MinimumReplicasRule());
        }
        return List.copyOf(selected.values());
    }

    public Map<String, List<Rule>> rulesByPack() {
        return Map.copyOf(rulesByPack);
    }

    private void loadIndex() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(INDEX)) {
            if (in == null) {
                LOG.warnf("Rule pack index %s not found; falling back to MinimumReplicasRule only", INDEX);
                registerBuiltInHaFallback();
                return;
            }
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!(loaded instanceof Map<?, ?> root) || !(root.get("packs") instanceof List<?> packs)) {
                LOG.warnf("Invalid rule pack index at %s", INDEX);
                registerBuiltInHaFallback();
                return;
            }

            Set<String> seenIds = new LinkedHashSet<>();
            for (Object packObj : packs) {
                if (!(packObj instanceof Map<?, ?> packMap)) {
                    continue;
                }
                String packId = stringVal(packMap.get("id"));
                String path = stringVal(packMap.get("path"));
                if (packId == null || path == null) {
                    continue;
                }
                List<Rule> packRules = loadPackFile(path, packId);
                for (Rule rule : packRules) {
                    if (!seenIds.add(rule.id())) {
                        throw new IllegalStateException(
                                "Duplicate assessment rule id '" + rule.id()
                                        + "' detected while loading pack '" + packId
                                        + "' from " + path);
                    }
                }
                rulesByPack.merge(packId, packRules, (a, b) -> {
                    List<Rule> merged = new ArrayList<>(a);
                    merged.addAll(b);
                    return merged;
                });
                allRules.addAll(packRules);
            }

            if (!seenIds.contains(MinimumReplicasRule.FINDING_ID)) {
                MinimumReplicasRule javaRule = new MinimumReplicasRule();
                rulesByPack.computeIfAbsent("ha", k -> new ArrayList<>()).add(javaRule);
                allRules.add(javaRule);
                seenIds.add(javaRule.id());
            }

            LOG.infof("Loaded %d assessment rules across %d pack(s)", allRules.size(), rulesByPack.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rule pack index " + INDEX, e);
        }
    }

    private void registerBuiltInHaFallback() {
        MinimumReplicasRule javaRule = new MinimumReplicasRule();
        rulesByPack.put("ha", List.of(javaRule));
        allRules.add(javaRule);
    }

    private List<Rule> loadPackFile(String classpathLocation, String packId) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                LOG.warnf("Rule pack file missing: %s (pack=%s)", classpathLocation, packId);
                return List.of();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml().load(reader);
                if (!(loaded instanceof Map<?, ?> map)) {
                    return List.of();
                }
                if (Boolean.TRUE.equals(map.get("deprecated"))) {
                    return List.of();
                }
                List<Rule> rules = new ArrayList<>();
                if (map.get("rules") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> ruleMap) {
                            Rule rule = toRule(ruleMap, packId);
                            if (rule != null) {
                                rules.add(rule);
                            }
                        }
                    }
                } else if (map.get("id") != null) {
                    // Legacy single-rule document
                    Rule rule = toRule(map, packId);
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
                return rules;
            }
        } catch (IOException e) {
            LOG.warnf(e, "Failed to load rule pack from %s", classpathLocation);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Rule toRule(Map<?, ?> map, String packId) {
        String id = stringVal(map.get("id"));
        String title = stringVal(map.get("title"));
        String category = stringVal(map.get("category"));
        Severity severity = parseSeverity(stringVal(map.get("severity")));
        if (id == null || title == null || category == null || severity == null) {
            return null;
        }

        String description = stringVal(map.get("description"));
        String impact = stringVal(map.get("impact"));
        String recommendation = stringVal(map.get("recommendation"));
        List<String> references = new ArrayList<>();
        if (map.get("references") instanceof List<?> refs) {
            for (Object r : refs) {
                if (r != null) {
                    references.add(String.valueOf(r));
                }
            }
        }

        Map<?, ?> appliesWhen = map.get("appliesWhen") instanceof Map<?, ?> aw ? aw : Map.of();
        List<String> runtimes = stringList(appliesWhen.get("runtime"));
        List<String> products = stringList(appliesWhen.get("product"));
        List<String> environments = stringList(appliesWhen.get("environment"));
        List<String> evidenceRequired = stringList(appliesWhen.get("evidenceRequired"));

        Object condition = map.get("condition");
        if (!(condition instanceof Map<?, ?> conditionMap)) {
            return null;
        }

        // New style: condition.key + lessThan/equals
        String evidenceKey = stringVal(conditionMap.get("key"));
        Integer lessThan = null;
        Object equalsValue = null;
        if (evidenceKey != null) {
            Object lt = conditionMap.get("lessThan");
            if (lt instanceof Number n) {
                lessThan = n.intValue();
            }
            if (conditionMap.containsKey("equals")) {
                equalsValue = conditionMap.get("equals");
            }
        } else if (conditionMap.get("replicas") instanceof Map<?, ?> replicasMap) {
            // Legacy style: condition.replicas.lessThan
            evidenceKey = stringVal(replicasMap.get("key"));
            if (evidenceKey == null) {
                evidenceKey = MinimumReplicasRule.EVIDENCE_KEY;
            }
            Object lt = replicasMap.get("lessThan");
            if (lt instanceof Number n) {
                lessThan = n.intValue();
            }
        }

        if (evidenceKey == null) {
            return null;
        }

        String finalEvidenceKey = evidenceKey;
        Integer finalLessThan = lessThan;
        Object finalEquals = equalsValue;
        String finalDescription = description;
        String finalImpact = impact;
        String finalRecommendation = recommendation;
        List<String> finalRefs = List.copyOf(references);

        return new Rule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String title() {
                return title;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public Severity severity() {
                return severity;
            }

            @Override
            public boolean applies(EvidenceContext context) {
                if (context == null) {
                    return false;
                }
                for (String required : evidenceRequired) {
                    if (!context.hasKey(required)) {
                        return false;
                    }
                }
                if (!runtimes.isEmpty()) {
                    Optional<String> runtime = context.findString("runtime.type");
                    if (runtime.isEmpty() || runtimes.stream().noneMatch(r -> r.equalsIgnoreCase(runtime.get()))) {
                        // If runtime evidence absent, still allow HA rules that only need replica evidence
                        if (!context.hasKey(finalEvidenceKey)) {
                            return false;
                        }
                    }
                }
                if (!products.isEmpty()) {
                    Optional<String> product = context.findString("keycloak.product");
                    if (product.isPresent()
                            && products.stream().noneMatch(p -> p.equalsIgnoreCase(product.get()))) {
                        return false;
                    }
                }
                if (!environments.isEmpty()) {
                    Optional<String> env = context.findString("target.environment");
                    if (env.isPresent()
                            && environments.stream().noneMatch(e -> e.equalsIgnoreCase(env.get()))) {
                        return false;
                    }
                }
                return context.hasKey(finalEvidenceKey);
            }

            @Override
            public Optional<Finding> evaluate(EvidenceContext context) {
                if (finalLessThan != null) {
                    EvidenceContext.OptionalIntResult value = context.getInt(finalEvidenceKey);
                    if (!value.present() || value.value() >= finalLessThan) {
                        return Optional.empty();
                    }
                    return Optional.of(new Finding(
                            context.targetId(),
                            id,
                            title,
                            category,
                            severity,
                            FindingStatus.OPEN,
                            finalDescription != null ? finalDescription
                                    : "Evidence '" + finalEvidenceKey + "' is " + value.value()
                                    + ", which is less than required minimum " + finalLessThan + ".",
                            Map.of(finalEvidenceKey, value.value(), "threshold", finalLessThan, "pack", packId),
                            finalImpact != null ? finalImpact
                                    : "Insufficient capacity/availability for production workloads.",
                            finalRecommendation != null ? finalRecommendation
                                    : "Increase " + finalEvidenceKey + " to at least " + finalLessThan + ".",
                            finalRefs));
                }
                if (finalEquals != null) {
                    Object actual = context.find(finalEvidenceKey).map(Evidence::value).orElse(null);
                    if (!valuesEqual(actual, finalEquals)) {
                        return Optional.empty();
                    }
                    return Optional.of(new Finding(
                            context.targetId(),
                            id,
                            title,
                            category,
                            severity,
                            FindingStatus.OPEN,
                            finalDescription != null ? finalDescription
                                    : "Evidence '" + finalEvidenceKey + "' equals " + finalEquals,
                            Map.of(finalEvidenceKey, actual == null ? "null" : actual, "pack", packId),
                            finalImpact != null ? finalImpact : "",
                            finalRecommendation != null ? finalRecommendation : "",
                            finalRefs));
                }
                return Optional.empty();
            }
        };
    }

    private static boolean valuesEqual(Object actual, Object expected) {
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

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return List.copyOf(out);
    }

    private static Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
