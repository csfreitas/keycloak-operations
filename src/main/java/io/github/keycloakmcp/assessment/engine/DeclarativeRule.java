package io.github.keycloakmcp.assessment.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Declarative rule loaded from YAML packs.
 */
public final class DeclarativeRule implements Rule {

    public enum Applicability {
        APPLIES,
        SKIPPED,
        NOT_EVALUABLE
    }

    private final String id;
    private final String title;
    private final String category;
    private final Severity severity;
    private final String description;
    private final String impact;
    private final String recommendation;
    private final List<String> references;
    private final String packId;
    private final Map<?, ?> condition;
    private final List<String> runtimes;
    private final List<String> products;
    private final List<String> environments;
    private final List<String> capabilities;
    private final List<String> evidenceRequired;
    private final String minimumVersion;
    private final String maximumVersion;
    private final String supportLevel;
    private final SubjectType subjectTypeHint;

    public DeclarativeRule(
            String id,
            String title,
            String category,
            Severity severity,
            String description,
            String impact,
            String recommendation,
            List<String> references,
            String packId,
            Map<?, ?> condition,
            List<String> runtimes,
            List<String> products,
            List<String> environments,
            List<String> capabilities,
            List<String> evidenceRequired,
            String minimumVersion,
            String maximumVersion,
            String supportLevel,
            SubjectType subjectTypeHint) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.impact = impact;
        this.recommendation = recommendation;
        this.references = references == null ? List.of() : List.copyOf(references);
        this.packId = packId;
        this.condition = condition;
        this.runtimes = runtimes == null ? List.of() : List.copyOf(runtimes);
        this.products = products == null ? List.of() : List.copyOf(products);
        this.environments = environments == null ? List.of() : List.copyOf(environments);
        this.capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        this.evidenceRequired = evidenceRequired == null ? List.of() : List.copyOf(evidenceRequired);
        this.minimumVersion = minimumVersion;
        this.maximumVersion = maximumVersion;
        this.supportLevel = supportLevel;
        this.subjectTypeHint = subjectTypeHint;
    }

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

    public Applicability applicability(EvidenceContext context) {
        if (context == null) {
            return Applicability.SKIPPED;
        }
        if (!runtimes.isEmpty()) {
            Optional<String> runtime = context.findString("runtime.type");
            if (runtime.isEmpty()) {
                return Applicability.NOT_EVALUABLE;
            }
            if (runtimes.stream().noneMatch(r -> r.equalsIgnoreCase(runtime.get()))) {
                return Applicability.SKIPPED;
            }
        }
        if (!products.isEmpty()) {
            Optional<String> product = context.findString("keycloak.product");
            if (product.isEmpty()) {
                return Applicability.NOT_EVALUABLE;
            }
            if (products.stream().noneMatch(p -> p.equalsIgnoreCase(product.get()))) {
                return Applicability.SKIPPED;
            }
        }
        if (!environments.isEmpty()) {
            Optional<String> env = context.findString("target.environment");
            if (env.isEmpty()) {
                return Applicability.NOT_EVALUABLE;
            }
            if (environments.stream().noneMatch(e -> e.equalsIgnoreCase(env.get()))) {
                return Applicability.SKIPPED;
            }
        }
        if (!capabilities.isEmpty()) {
            Optional<String> caps = context.findString("keycloak.capabilities");
            if (caps.isEmpty() && !context.hasKey("keycloak.capabilities")) {
                return Applicability.NOT_EVALUABLE;
            }
        }
        for (String required : evidenceRequired) {
            if (!context.hasKey(required)) {
                return Applicability.NOT_EVALUABLE;
            }
        }
        if (minimumVersion != null && !minimumVersion.isBlank()) {
            Optional<String> version = context.findString("keycloak.version");
            if (version.isEmpty()) {
                return Applicability.NOT_EVALUABLE;
            }
            if (compareVersions(version.get(), minimumVersion) < 0) {
                return Applicability.SKIPPED;
            }
        }
        if (maximumVersion != null && !maximumVersion.isBlank()) {
            Optional<String> version = context.findString("keycloak.version");
            if (version.isEmpty()) {
                return Applicability.NOT_EVALUABLE;
            }
            if (compareVersions(version.get(), maximumVersion) > 0) {
                return Applicability.SKIPPED;
            }
        }
        return Applicability.APPLIES;
    }

    @Override
    public boolean applies(EvidenceContext context) {
        return applicability(context) == Applicability.APPLIES;
    }

    public List<String> missingEvidenceKeys(EvidenceContext context) {
        List<String> missing = new ArrayList<>();
        for (String required : evidenceRequired) {
            if (!context.hasKey(required)) {
                missing.add(required);
            }
        }
        for (String key : ConditionEvaluator.referencedKeys(condition)) {
            if (!context.hasKey(key) && !missing.contains(key)) {
                // exists/notExists don't require presence — leave to evaluate
                missing.add(key);
            }
        }
        return List.copyOf(missing);
    }

    @Override
    public Optional<Finding> evaluate(EvidenceContext context) {
        try {
            boolean matched = ConditionEvaluator.matches(condition, context);
            if (!matched) {
                return Optional.of(passFinding(context));
            }
            return Optional.of(openFinding(context));
        } catch (ConditionEvaluator.MissingEvidenceException e) {
            return Optional.of(notEvaluatedFinding(context, e.key()));
        }
    }

    private Finding openFinding(EvidenceContext context) {
        Map<String, Object> ev = new LinkedHashMap<>();
        for (String key : ConditionEvaluator.referencedKeys(condition)) {
            context.find(key).ifPresent(e -> ev.put(key, e.value()));
        }
        ev.put("pack", packId);
        if (supportLevel != null) {
            ev.put("supportLevel", supportLevel);
        }
        EvidenceSubject subject = resolveSubject(context);
        return new Finding(
                context.targetId(),
                id,
                title,
                category,
                severity,
                FindingStatus.OPEN,
                description != null ? description : title,
                Map.copyOf(ev),
                impact != null ? impact : "",
                recommendation != null ? recommendation : "",
                references,
                subject);
    }

    private Finding passFinding(EvidenceContext context) {
        return new Finding(
                context.targetId(),
                id,
                title,
                category,
                severity,
                FindingStatus.PASS,
                "Rule condition not matched",
                Map.of("pack", packId),
                "",
                "",
                references,
                resolveSubject(context));
    }

    public Finding notEvaluated(EvidenceContext context, String missingKey) {
        return new Finding(
                context.targetId(),
                id,
                title,
                category,
                severity,
                FindingStatus.NOT_EVALUATED,
                "Required evidence missing: " + missingKey,
                Map.of("missingEvidence", missingKey, "pack", packId),
                "",
                "Ensure the collector has permission or configuration to provide " + missingKey,
                references,
                resolveSubject(context));
    }

    private Finding notEvaluatedFinding(EvidenceContext context, String missingKey) {
        return notEvaluated(context, missingKey);
    }

    private EvidenceSubject resolveSubject(EvidenceContext context) {
        if (subjectTypeHint == SubjectType.REALM) {
            return context.findString("realm.name")
                    .map(EvidenceSubject::realm)
                    .orElse(null);
        }
        if (subjectTypeHint == SubjectType.CLIENT) {
            return context.findString("client.clientId")
                    .map(EvidenceSubject::client)
                    .orElse(null);
        }
        return EvidenceSubject.target(context.targetId());
    }

    /** Simple dotted numeric compare: 26.6.0 vs 26.7.1 */
    static int compareVersions(String a, String b) {
        String[] aa = a.split("[^0-9]+");
        String[] bb = b.split("[^0-9]+");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            int av = i < aa.length && !aa[i].isBlank() ? Integer.parseInt(aa[i]) : 0;
            int bv = i < bb.length && !bb[i].isBlank() ? Integer.parseInt(bb[i]) : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    public static Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
