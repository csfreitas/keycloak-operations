package io.github.keycloakmcp.adapter.infrastructure;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.InfrastructureType;
import io.github.keycloakmcp.target.Target;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stub factory for OpenShift / Kubernetes / VM clients.
 * <p>
 * Future work: return typed clients based on {@link Target#infrastructure()}.
 * {@link InfrastructureType#NONE} is a no-op and does not throw.
 */
@ApplicationScoped
public class InfrastructureClientFactory {

    private static final Logger LOG = Logger.getLogger(InfrastructureClientFactory.class);

    /**
     * Resolves an infrastructure client handle for the target, if configured.
     *
     * @return empty for {@link InfrastructureType#NONE} or when infrastructure is absent
     * @throws McpException with {@code UNSUPPORTED_CAPABILITY} for configured types not yet implemented
     */
    public Optional<Object> resolve(Target target) {
        if (target == null || !target.hasInfrastructure()) {
            return Optional.empty();
        }
        InfrastructureType type = target.infrastructureTypeOrNone();
        if (type == InfrastructureType.NONE) {
            return Optional.empty();
        }
        LOG.debugf(
                "Infrastructure client requested for target=%s type=%s (not yet implemented)",
                target.id().value(),
                type);
        throw McpException.unsupportedCapability(
                "Infrastructure type " + type + " is not yet supported for target '"
                        + target.id().value() + "'");
    }
}
