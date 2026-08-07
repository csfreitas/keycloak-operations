package io.github.keycloakmcp.discovery;

import java.util.List;

public record EnvironmentInfo(
        RuntimeType runtime,
        DetectionConfidence confidence,
        String platform,
        String namespace,
        List<String> evidence) {
}
