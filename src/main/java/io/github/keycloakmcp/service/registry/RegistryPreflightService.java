package io.github.keycloakmcp.service.registry;

import java.io.InputStream;
import java.util.ArrayList;

import io.github.keycloakmcp.config.McpRuntimeConfig;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Issue;
import io.github.keycloakmcp.service.registry.RegistryPreflightResult.Status;
import io.github.keycloakmcp.target.ConfigurationTargetRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Reads only local registry metadata. Deliberately has no client, credential provider or mutator. */
@ApplicationScoped
public class RegistryPreflightService {
    private final RegistryPreflightPolicy policy;
    private final RegistryPreflightValidator validator;
    private final ConfigurationTargetRegistry configured;
    private final TargetRepository repository;
    private final McpRuntimeConfig runtime;

    @Inject
    public RegistryPreflightService(RegistryPreflightPolicy policy, RegistryPreflightValidator validator,
            ConfigurationTargetRegistry configured, TargetRepository repository, McpRuntimeConfig runtime) {
        this.policy = policy;
        this.validator = validator;
        this.configured = configured;
        this.repository = repository;
        this.runtime = runtime;
    }

    public RegistryPreflightResult preflight(InputStream body) {
        policy.assertAllowed();
        var validation = validator.validate(body);
        var issues = new ArrayList<>(validation.issues());
        if (!issues.isEmpty()) return RegistryPreflightResult.local(Status.REJECTED, issues, runtime.readOnly());
        var draft = validation.draft();
        // Membership is not credential resolution, validation, or authorization to use the reference.
        if (!runtime.credentials().containsKey(draft.keycloak().credentialRef())) {
            issues.add(new Issue("keycloak.credentialRef", "UNKNOWN_CREDENTIAL_REFERENCE"));
        }
        try {
            // Do not use CompositeTargetRegistry: its bootstrap/fallback must not turn a failed read into success.
            if (configured.findById(draft.targetId()).isPresent() || repository.existsById(draft.targetId())) {
                issues.add(new Issue("targetId", "ALREADY_EXISTS"));
            }
        } catch (RuntimeException unavailable) {
            issues.add(new Issue("registry", "REGISTRY_UNAVAILABLE"));
            return RegistryPreflightResult.local(Status.INCONCLUSIVE, issues, runtime.readOnly());
        }
        return RegistryPreflightResult.local(issues.isEmpty() ? Status.LOCALLY_VALID : Status.REJECTED,
                issues, runtime.readOnly());
    }
}
