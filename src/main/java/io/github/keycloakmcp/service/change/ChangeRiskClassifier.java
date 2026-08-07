package io.github.keycloakmcp.service.change;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Deterministic risk classification. Never delegated to an LLM.
 */
@ApplicationScoped
public class ChangeRiskClassifier {

    private static final Set<String> LOW = Set.of("name", "description");
    private static final Set<String> MEDIUM = Set.of("pkcecodechallengemethod", "pkce.code.challenge.method");

    public ChangeRisk classify(List<ChangeOperation> operations) {
        ChangeRisk highest = ChangeRisk.LOW;
        for (ChangeOperation op : operations) {
            ChangeRisk risk = classifyProperty(op.property());
            if (risk.ordinal() > highest.ordinal()) {
                highest = risk;
            }
        }
        return highest;
    }

    public ChangeRisk classifyProperty(String property) {
        if (property == null || property.isBlank()) {
            return ChangeRisk.HIGH;
        }
        String normalized = property.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        if (LOW.contains(normalized) || LOW.contains(property.toLowerCase(Locale.ROOT))) {
            return ChangeRisk.LOW;
        }
        if (MEDIUM.contains(normalized) || MEDIUM.contains(property.toLowerCase(Locale.ROOT))) {
            return ChangeRisk.MEDIUM;
        }
        // Unknown properties are treated as HIGH until a later milestone allowlists them.
        return ChangeRisk.HIGH;
    }
}
