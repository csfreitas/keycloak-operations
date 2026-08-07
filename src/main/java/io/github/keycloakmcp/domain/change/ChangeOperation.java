package io.github.keycloakmcp.domain.change;

import java.util.Objects;

public record ChangeOperation(
        String property,
        ChangeOperationType operationType,
        String before,
        String after) {

    public ChangeOperation {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(operationType, "operationType");
    }
}
