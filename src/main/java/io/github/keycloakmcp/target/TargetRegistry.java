package io.github.keycloakmcp.target;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.error.McpException;

public interface TargetRegistry {

    List<Target> list();

    Optional<Target> findById(String id);

    /**
     * @throws McpException with {@code TARGET_NOT_FOUND} when the id is unknown
     */
    default Target require(String id) {
        return findById(id).orElseThrow(() -> McpException.targetNotFound(id));
    }
}
