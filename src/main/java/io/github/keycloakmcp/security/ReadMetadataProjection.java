package io.github.keycloakmcp.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.keycloakmcp.domain.inventory.InfrastructureInventory;
import io.github.keycloakmcp.domain.inventory.TopologyInfo;
import io.github.keycloakmcp.domain.platform.TargetOverview;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Lossy read-transport copies only: never use these results as collection or mutation inputs. */
@ApplicationScoped
public class ReadMetadataProjection {
    private final ObjectMapper mapper;
    private final SensitiveDataFilter filter;

    @Inject
    public ReadMetadataProjection(ObjectMapper mapper, SensitiveDataFilter filter) {
        this.mapper = mapper;
        this.filter = filter;
    }

    /** Identity paths are fixed by the transport contract, never supplied by a caller. */
    public <T> T project(T value, String... identityPaths) {
        return project(value, identityPaths, new String[0]);
    }

    public InfrastructureInventory inventory(InfrastructureInventory value) {
        return project(value, new String[] {"targetId", "discovery.targetId"},
                new String[] {"topology.podsByZone", "topology.podsByNode"});
    }

    public TopologyInfo topology(TopologyInfo value) {
        return project(value, new String[0], new String[] {"podsByZone", "podsByNode"});
    }

    public TargetOverview overview(TargetOverview value) {
        return project(value, new String[] {"targetId", "latestAssessment.id", "latestAssessment.targetId",
                "latestHealthCheck.id", "latestHealthCheck.targetId", "latestSnapshot.id",
                "latestSnapshot.targetId", "latestSnapshot.snapshotHash"},
                new String[] {"latestAssessment.categoryScores", "latestAssessment.findingCounts"});
    }

    @SuppressWarnings("unchecked")
    private <T> T project(T value, String[] identityPaths, String[] integerMapPaths) {
        if (value == null) return null;
        Map<String, Object> original = mapper.convertValue(value, Map.class);
        Map<String, Object> safe = filter.redactMetadata(original);
        for (String path : identityPaths) restorePath(original, safe, path, false);
        for (String path : integerMapPaths) restorePath(original, safe, path, true);
        return value instanceof Map<?, ?> ? (T) safe : (T) mapper.convertValue(safe, value.getClass());
    }

    @SuppressWarnings("unchecked")
    private void restorePath(Map<String, Object> original, Map<String, Object> safe, String path, boolean integerMap) {
        String[] parts = path.split("\\.");
        for (int index = 0; index < parts.length - 1; index++) {
            if (!(original.get(parts[index]) instanceof Map<?, ?> source)
                    || !(safe.get(parts[index]) instanceof Map<?, ?> output)) return;
            original = (Map<String, Object>) source;
            safe = (Map<String, Object>) output;
        }
        String leaf = parts[parts.length - 1];
        if (!original.containsKey(leaf)) return;
        Object source = original.get(leaf);
        safe.put(leaf, integerMap && source instanceof Map<?, ?> counts ? countedNames(counts) : source);
    }

    /**
     * Only declared Map<String,Integer> count fields reach this method. Names still
     * receive collision-safe key projection, while observed counts remain integers.
     * Arbitrary details/tags must not regain numeric or boolean credential values.
     */
    private Map<String, Object> countedNames(Map<?, ?> counts) {
        Map<?, ?> safeNames = filter.redactMetadata(counts);
        List<?> values = new ArrayList<>(counts.values());
        Map<String, Object> safeCounts = new LinkedHashMap<>();
        int index = 0;
        for (Object name : safeNames.keySet()) safeCounts.put((String) name, values.get(index++));
        return safeCounts;
    }
}
