package io.github.keycloakmcp.domain.configuration;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Facts only. COMPLETE describes the configured fields, not health or full environment coverage. */
public record ConfigurationObservation(String schemaVersion, String observationId,
        ConfigurationScope scope, Instant collectedAt, String source, String productVersion,
        String status, Map<String, Boolean> facts, List<String> missingFields) {
    public ConfigurationObservation {
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        missingFields = List.copyOf(missingFields);
    }
}
