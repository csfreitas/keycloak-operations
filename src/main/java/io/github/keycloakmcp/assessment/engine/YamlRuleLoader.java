package io.github.keycloakmcp.assessment.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.yaml.snakeyaml.Yaml;

import io.github.keycloakmcp.assessment.engine.rules.MinimumReplicasRule;
import io.github.keycloakmcp.config.AssessmentConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Minimal YAML rule loader for classpath rule packs (future expansion).
 * Supports simple replica less-than conditions in 0.1.0.
 */
@ApplicationScoped
public class YamlRuleLoader {

    private static final Logger LOG = Logger.getLogger(YamlRuleLoader.class);

    private final AssessmentConfig assessmentConfig;

    @Inject
    public YamlRuleLoader(AssessmentConfig assessmentConfig) {
        this.assessmentConfig = assessmentConfig;
    }

    public List<Rule> loadBuiltInAndClasspathRules() {
        List<Rule> rules = new ArrayList<>();
        rules.add(new MinimumReplicasRule());
        rules.addAll(loadFromClasspathDirectory(assessmentConfig.rulesPath()));
        return List.copyOf(rules);
    }

    public List<Rule> loadFromClasspathDirectory(String directory) {
        String base = directory == null || directory.isBlank() ? "rules" : directory;
        List<Rule> rules = new ArrayList<>();
        // Known starter rule file for 0.1.0; pack discovery can expand later
        String[] candidates = {
                base + "/minimum-replicas.yaml",
                base + "/kc-ocp-ha-001.yaml"
        };
        for (String candidate : candidates) {
            loadSingle(candidate).ifPresent(rules::add);
        }
        return rules;
    }

    public Optional<Rule> loadSingle(String classpathLocation) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                return Optional.empty();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Yaml yaml = new Yaml();
                Object loaded = yaml.load(reader);
                if (!(loaded instanceof Map<?, ?> map)) {
                    LOG.warnf("Ignoring YAML rule at %s: root is not a map", classpathLocation);
                    return Optional.empty();
                }
                return Optional.ofNullable(toRule(map));
            }
        } catch (IOException e) {
            LOG.warnf(e, "Failed to load YAML rule from %s", classpathLocation);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Rule toRule(Map<?, ?> map) {
        String id = stringVal(map.get("id"));
        String title = stringVal(map.get("title"));
        String category = stringVal(map.get("category"));
        Severity severity = parseSeverity(stringVal(map.get("severity")));
        if (id == null || title == null || category == null || severity == null) {
            return null;
        }

        Object condition = map.get("condition");
        if (!(condition instanceof Map<?, ?> conditionMap)) {
            return null;
        }
        Object replicas = conditionMap.get("replicas");
        if (!(replicas instanceof Map<?, ?> replicasMap)) {
            return null;
        }
        Object lessThan = replicasMap.get("lessThan");
        if (!(lessThan instanceof Number number)) {
            return null;
        }
        int threshold = number.intValue();
        String evidenceKey = stringVal(replicasMap.get("key"));
        if (evidenceKey == null) {
            evidenceKey = MinimumReplicasRule.EVIDENCE_KEY;
        }
        String finalEvidenceKey = evidenceKey;

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
                return context != null && context.hasKey(finalEvidenceKey);
            }

            @Override
            public Optional<Finding> evaluate(EvidenceContext context) {
                EvidenceContext.OptionalIntResult value = context.getInt(finalEvidenceKey);
                if (!value.present() || value.value() >= threshold) {
                    return Optional.empty();
                }
                return Optional.of(new Finding(
                        context.targetId(),
                        id,
                        title,
                        category,
                        severity,
                        FindingStatus.OPEN,
                        "Evidence '" + finalEvidenceKey + "' is " + value.value()
                                + ", which is less than required minimum " + threshold + ".",
                        Map.of(finalEvidenceKey, value.value(), "threshold", threshold),
                        "Insufficient capacity/availability for production workloads.",
                        "Increase " + finalEvidenceKey + " to at least " + threshold + ".",
                        List.of()));
            }
        };
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
