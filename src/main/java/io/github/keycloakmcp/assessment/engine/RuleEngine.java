package io.github.keycloakmcp.assessment.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RuleEngine {

    public List<Finding> evaluate(List<Rule> rules, EvidenceContext context) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule == null || !rule.applies(context)) {
                continue;
            }
            Optional<Finding> finding = rule.evaluate(context);
            finding.ifPresent(findings::add);
        }
        return List.copyOf(findings);
    }
}
