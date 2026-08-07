package io.github.keycloakmcp.assessment.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RuleEngine {

    /**
     * Returns only actionable findings (OPEN / FAIL / WARNING).
     */
    public List<Finding> evaluate(List<Rule> rules, EvidenceContext context) {
        return evaluateDetailed(rules, context).findings().stream()
                .filter(f -> f.status() == FindingStatus.OPEN
                        || f.status() == FindingStatus.FAIL
                        || f.status() == FindingStatus.WARNING)
                .toList();
    }

    public RuleEvaluationResult evaluateDetailed(List<Rule> rules, EvidenceContext context) {
        if (rules == null || rules.isEmpty()) {
            return RuleEvaluationResult.empty();
        }
        List<Finding> all = new ArrayList<>();
        int evaluated = 0;
        int matched = 0;
        int skipped = 0;
        int notEvaluated = 0;
        Set<String> missing = new LinkedHashSet<>();

        for (Rule rule : rules) {
            if (rule == null) {
                continue;
            }
            if (rule instanceof DeclarativeRule declarative) {
                DeclarativeRule.Applicability applicability = declarative.applicability(context);
                if (applicability == DeclarativeRule.Applicability.SKIPPED) {
                    skipped++;
                    continue;
                }
                if (applicability == DeclarativeRule.Applicability.NOT_EVALUABLE) {
                    notEvaluated++;
                    List<String> keys = declarative.missingEvidenceKeys(context);
                    missing.addAll(keys);
                    String missingKey = keys.isEmpty() ? "required-evidence" : keys.get(0);
                    all.add(declarative.notEvaluated(context, missingKey));
                    continue;
                }
                evaluated++;
                Optional<Finding> finding = declarative.evaluate(context);
                if (finding.isEmpty()) {
                    continue;
                }
                Finding f = finding.get();
                all.add(f);
                if (f.status() == FindingStatus.OPEN || f.status() == FindingStatus.FAIL) {
                    matched++;
                } else if (f.status() == FindingStatus.NOT_EVALUATED) {
                    evaluated--;
                    notEvaluated++;
                    Object m = f.evidence() == null ? null : f.evidence().get("missingEvidence");
                    if (m != null) {
                        missing.add(String.valueOf(m));
                    }
                }
                continue;
            }

            if (!rule.applies(context)) {
                skipped++;
                continue;
            }
            evaluated++;
            Optional<Finding> finding = rule.evaluate(context);
            if (finding.isPresent()) {
                all.add(finding.get());
                matched++;
            }
        }
        return new RuleEvaluationResult(
                List.copyOf(all),
                evaluated,
                matched,
                skipped,
                notEvaluated,
                List.copyOf(missing));
    }
}
