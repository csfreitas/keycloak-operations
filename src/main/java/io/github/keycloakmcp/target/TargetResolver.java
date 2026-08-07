package io.github.keycloakmcp.target;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.error.McpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TargetResolver {

    private final TargetRegistry registry;

    @Inject
    public TargetResolver(TargetRegistry registry) {
        this.registry = registry;
    }

    public List<Target> list() {
        return registry.list();
    }

    public Optional<Target> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            TargetId.of(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return registry.findById(id.trim());
    }

    /**
     * Validates the id, resolves the target, and rejects disabled targets.
     *
     * @throws McpException {@code TARGET_NOT_FOUND} or {@code TARGET_DISABLED}
     */
    public Target require(String id) {
        if (id == null || id.isBlank()) {
            throw McpException.invalidArgument("targetId must not be blank");
        }
        TargetId targetId;
        try {
            targetId = TargetId.of(id);
        } catch (IllegalArgumentException e) {
            throw McpException.invalidArgument(e.getMessage());
        }
        Target target = registry.require(targetId.value());
        if (!target.enabled()) {
            throw McpException.targetDisabled(targetId.value());
        }
        return target;
    }
}
