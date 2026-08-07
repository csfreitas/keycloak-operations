package io.github.keycloakmcp.domain.group;

public record GroupDetails(
        String id,
        String name,
        String path,
        Long subGroupCount) {
}
