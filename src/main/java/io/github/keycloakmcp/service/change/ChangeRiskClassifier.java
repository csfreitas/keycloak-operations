package io.github.keycloakmcp.service.change;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.keycloakmcp.domain.change.ChangeOperation;
import io.github.keycloakmcp.domain.change.ChangeOperationType;
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

    /** Transition-aware classification for client redirect URI and Web Origin set changes. */
    public ChangeRisk classifyClientUrls(List<ChangeOperation> operations) {
        ChangeRisk highest = ChangeRisk.MEDIUM;
        for (ChangeOperation operation : operations) {
            Set<String> before = asStringSet(operation.before());
            for (String value : asStringSet(operation.after())) {
                if (!before.contains(value)) {
                    ChangeRisk valueRisk = classifyAddedClientUrl(operation.property(), value);
                    if (valueRisk.ordinal() > highest.ordinal()) {
                        highest = valueRisk;
                    }
                }
            }
        }
        return highest;
    }

    /** Transition-aware classification for client authentication and OAuth/OIDC flow settings. */
    public ChangeRisk classifyClientSecurity(List<ChangeOperation> operations) {
        ChangeRisk highest = ChangeRisk.MEDIUM;
        for (ChangeOperation operation : operations) {
            ChangeRisk transitionRisk = switch (operation.property()) {
                case ClientSecuritySettingsChangeSupport.PKCE ->
                    ClientSecuritySettingsChangeSupport.PKCE_S256.equals(operation.before())
                                    && ClientSecuritySettingsChangeSupport.PKCE_NONE.equals(operation.after())
                            ? ChangeRisk.HIGH
                            : ChangeRisk.MEDIUM;
                case ClientSecuritySettingsChangeSupport.IMPLICIT_FLOW,
                        ClientSecuritySettingsChangeSupport.DIRECT_ACCESS_GRANTS,
                        ClientSecuritySettingsChangeSupport.SERVICE_ACCOUNTS ->
                    becameEnabled(operation) ? ChangeRisk.HIGH : ChangeRisk.MEDIUM;
                case ClientSecuritySettingsChangeSupport.PUBLIC_CLIENT -> ChangeRisk.HIGH;
                case ClientSecuritySettingsChangeSupport.STANDARD_FLOW -> ChangeRisk.MEDIUM;
                default -> ChangeRisk.HIGH;
            };
            if (transitionRisk.ordinal() > highest.ordinal()) {
                highest = transitionRisk;
            }
        }
        return highest;
    }

    /** Creating a security principal is always treated as HIGH risk. */
    public ChangeRisk classifyClientCreate(List<ChangeOperation> operations) {
        if (operations == null || operations.isEmpty()
                || operations.stream().anyMatch(op -> op.operationType() != ChangeOperationType.CREATE)) {
            return ChangeRisk.CRITICAL;
        }
        return ChangeRisk.HIGH;
    }

    /** Enabling exposure is HIGH risk; disabling an existing client is MEDIUM risk. */
    public ChangeRisk classifyClientEnabled(List<ChangeOperation> operations) {
        if (operations == null || operations.size() != 1
                || !ClientLifecycleChangeSupport.ENABLED.equals(operations.get(0).property())) {
            return ChangeRisk.CRITICAL;
        }
        return Boolean.TRUE.equals(operations.get(0).after()) ? ChangeRisk.HIGH : ChangeRisk.MEDIUM;
    }

    private static ChangeRisk classifyAddedClientUrl(String property, String value) {
        if ("*".equals(value)) {
            return ChangeRisk.CRITICAL;
        }
        if (ClientUrlSettingsChangeSupport.WEB_ORIGINS.equals(property) && "+".equals(value)) {
            return ChangeRisk.HIGH;
        }
        if (ClientUrlSettingsChangeSupport.containsPathWildcard(value)) {
            return ChangeRisk.HIGH;
        }
        try {
            URI uri = new URI(value);
            if ("http".equalsIgnoreCase(uri.getScheme())
                    && !ClientUrlSettingsChangeSupport.isLoopbackHttp(value)) {
                return ChangeRisk.HIGH;
            }
        } catch (URISyntaxException e) {
            return ChangeRisk.HIGH;
        }
        return ChangeRisk.MEDIUM;
    }

    private static Set<String> asStringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
    }

    private static boolean becameEnabled(ChangeOperation operation) {
        return !Boolean.TRUE.equals(operation.before()) && Boolean.TRUE.equals(operation.after());
    }
}
