package io.github.keycloakmcp.domain.change;

import java.util.Objects;

public record ChangeDiffEntry(
        String property,
        DiffKind kind,
        String before,
        String after) {

    public ChangeDiffEntry {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(kind, "kind");
    }
}
