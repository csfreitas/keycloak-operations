package io.github.keycloakmcp.service.change;

import io.github.keycloakmcp.domain.change.ChangeOperationType;
import io.github.keycloakmcp.domain.change.ChangePolicyDecision;
import io.github.keycloakmcp.domain.change.ChangeRisk;
import io.github.keycloakmcp.target.TargetEnvironment;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Environment-aware policy evaluation. Deterministic backend decision.
 */
@ApplicationScoped
public class ChangePolicyEvaluator {

    public record PolicyResult(ChangePolicyDecision decision, String reason, boolean requiresApproval) {
    }

    public PolicyResult evaluate(
            TargetEnvironment environment,
            ChangeOperationType operationType,
            ChangeRisk risk,
            boolean destructive) {
        if (destructive || isDelete(operationType)) {
            return new PolicyResult(
                    ChangePolicyDecision.DENY,
                    "Destructive operations are denied in the 0.8 foundation",
                    true);
        }
        TargetEnvironment env = environment == null ? TargetEnvironment.UNKNOWN : environment;
        return switch (env) {
            case DEV -> evaluateDev(risk);
            case TEST, HML, STAGING -> new PolicyResult(
                    ChangePolicyDecision.APPROVAL_REQUIRED,
                    env.name() + " writes require explicit approval",
                    true);
            case PRD -> new PolicyResult(
                    ChangePolicyDecision.APPROVAL_REQUIRED,
                    "Production writes require explicit approval",
                    true);
            case UNKNOWN -> new PolicyResult(
                    ChangePolicyDecision.APPROVAL_REQUIRED,
                    "Unknown environment requires explicit approval",
                    true);
        };
    }

    public PolicyResult evaluateClientUrls(
            TargetEnvironment environment,
            ChangeRisk risk,
            boolean denyInProduction) {
        TargetEnvironment env = environment == null ? TargetEnvironment.UNKNOWN : environment;
        if (env == TargetEnvironment.PRD && denyInProduction) {
            return new PolicyResult(
                    ChangePolicyDecision.DENY,
                    "Production policy denies wildcard, Web Origin '+', and non-loopback HTTP additions",
                    true);
        }
        return evaluate(env, ChangeOperationType.UPDATE, risk, false);
    }

    private PolicyResult evaluateDev(ChangeRisk risk) {
        if (risk == ChangeRisk.LOW) {
            return new PolicyResult(
                    ChangePolicyDecision.ALLOW,
                    "DEV allows LOW-risk writes without additional approval",
                    false);
        }
        return new PolicyResult(
                ChangePolicyDecision.APPROVAL_REQUIRED,
                "DEV requires approval for " + risk + " risk changes",
                true);
    }

    private static boolean isDelete(ChangeOperationType operationType) {
        return operationType != null && "DELETE".equalsIgnoreCase(operationType.name());
    }
}
