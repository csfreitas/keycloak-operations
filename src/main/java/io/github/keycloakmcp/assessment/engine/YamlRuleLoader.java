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
import java.util.Set;

import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import io.github.keycloakmcp.assessment.profile.AssessmentProfile;
import io.github.keycloakmcp.config.AssessmentConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Loads YAML rule packs listed in {@code rules/index.yaml} (JAR-safe).
 * Invalid packs / duplicate IDs fail startup.
 */
@ApplicationScoped
public class YamlRuleLoader {

    private static final Logger LOG = Logger.getLogger(YamlRuleLoader.class);
    private static final String INDEX = "rules/index.yaml";

    private final AssessmentConfig assessmentConfig;
    private final Map<String, List<Rule>> rulesByPack = new LinkedHashMap<>();
    private final List<Rule> allRules = new ArrayList<>();
    private final Set<String> knownPackIds = new LinkedHashSet<>();

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
            if (!knownPackIds.contains(packId) && !rulesByPack.containsKey(packId)) {
                throw new IllegalStateException(
                        "Assessment profile '" + profile.name()
                                + "' references unknown rule pack '" + packId + "'");
            }
            List<Rule> packRules = rulesByPack.getOrDefault(packId, List.of());
            for (Rule rule : packRules) {
                selected.putIfAbsent(rule.id(), rule);
            }
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
                throw new IllegalStateException("Rule pack index " + INDEX + " not found on classpath");
            }
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            if (!(loaded instanceof Map<?, ?> root) || !(root.get("packs") instanceof List<?> packs)) {
                throw new IllegalStateException("Invalid rule pack index at " + INDEX);
            }

            Set<String> seenIds = new LinkedHashSet<>();
            for (Object packObj : packs) {
                if (!(packObj instanceof Map<?, ?> packMap)) {
                    throw new IllegalStateException("Each pack entry in " + INDEX + " must be a map");
                }
                String packId = stringVal(packMap.get("id"));
                String path = stringVal(packMap.get("path"));
                if (packId == null || packId.isBlank()) {
                    throw new IllegalStateException("Rule pack missing id in " + INDEX);
                }
                if (path == null || path.isBlank()) {
                    throw new IllegalStateException("Rule pack '" + packId + "' missing path in " + INDEX);
                }
                knownPackIds.add(packId);
                List<Rule> packRules = loadPackFile(path, packId);
                for (Rule rule : packRules) {
                    if (!seenIds.add(rule.id())) {
                        throw new IllegalStateException(
                                "Duplicate assessment rule id '" + rule.id()
                                        + "' detected while loading pack '" + packId
                                        + "' from " + path);
                    }
                }
                rulesByPack.put(packId, List.copyOf(packRules));
                allRules.addAll(packRules);
            }

            LOG.infof("Loaded %d assessment rules across %d pack(s)", allRules.size(), rulesByPack.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rule pack index " + INDEX, e);
        }
    }

    private List<Rule> loadPackFile(String classpathLocation, String packId) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Rule pack file missing: " + classpathLocation + " (pack=" + packId + ")");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml().load(reader);
                if (!(loaded instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("Rule pack " + classpathLocation + " must be a YAML map");
                }
                if (Boolean.TRUE.equals(map.get("deprecated"))) {
                    LOG.infof("Skipping deprecated rule pack file %s", classpathLocation);
                    return List.of();
                }
                List<Rule> rules = new ArrayList<>();
                if (map.get("rules") instanceof List<?> list) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> ruleMap)) {
                            throw new IllegalStateException(
                                    "Rule entries in " + classpathLocation + " must be maps");
                        }
                        rules.add(toRule(ruleMap, packId, classpathLocation));
                    }
                } else if (map.get("id") != null) {
                    rules.add(toRule(map, packId, classpathLocation));
                }
                return rules;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load rule pack from " + classpathLocation, e);
        }
    }

    private Rule toRule(Map<?, ?> map, String packId, String sourcePath) {
        String id = stringVal(map.get("id"));
        String title = stringVal(map.get("title"));
        String category = stringVal(map.get("category"));
        Severity severity = DeclarativeRule.parseSeverity(stringVal(map.get("severity")));

        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Rule missing id in " + sourcePath);
        }
        if (title == null || title.isBlank()) {
            throw new IllegalStateException("Rule '" + id + "' missing title in " + sourcePath);
        }
        if (category == null || category.isBlank()) {
            throw new IllegalStateException("Rule '" + id + "' missing category in " + sourcePath);
        }
        if (severity == null) {
            throw new IllegalStateException("Rule '" + id + "' has invalid severity in " + sourcePath);
        }

        String description = stringVal(map.get("description"));
        String impact = stringVal(map.get("impact"));
        String recommendation = stringVal(map.get("recommendation"));
        List<String> references = stringList(map.get("references"));

        Map<?, ?> appliesWhen = map.get("appliesWhen") instanceof Map<?, ?> aw ? aw : Map.of();
        List<String> runtimes = stringList(appliesWhen.get("runtime"));
        List<String> products = stringList(appliesWhen.get("product"));
        List<String> environments = stringList(appliesWhen.get("environment"));
        List<String> capabilities = stringList(appliesWhen.get("capability"));
        List<String> evidenceRequired = stringList(appliesWhen.get("evidenceRequired"));
        String minimumVersion = stringVal(appliesWhen.get("minimumVersion"));
        String maximumVersion = stringVal(appliesWhen.get("maximumVersion"));
        String supportLevel = stringVal(map.get("supportLevel"));

        Object condition = map.get("condition");
        if (!(condition instanceof Map<?, ?> conditionMap)) {
            throw new IllegalStateException("Rule '" + id + "' missing condition map in " + sourcePath);
        }
        try {
            ConditionEvaluator.validate(conditionMap);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Rule '" + id + "' has invalid condition in " + sourcePath + ": " + e.getMessage(), e);
        }

        SubjectType subjectHint = null;
        String subjectType = stringVal(map.get("subjectType"));
        if (subjectType != null) {
            try {
                subjectHint = SubjectType.valueOf(subjectType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Rule '" + id + "' has invalid subjectType in " + sourcePath);
            }
        }

        return new DeclarativeRule(
                id,
                title,
                category,
                severity,
                description,
                impact,
                recommendation,
                references,
                packId,
                conditionMap,
                runtimes,
                products,
                environments,
                capabilities,
                evidenceRequired,
                minimumVersion,
                maximumVersion,
                supportLevel,
                subjectHint);
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

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
