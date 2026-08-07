package io.github.keycloakmcp.service.platform;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.domain.platform.EnvironmentChange;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetAuthorizationService;
import io.github.keycloakmcp.target.TargetPermission;
import io.github.keycloakmcp.target.TargetResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EnvironmentChangeService {

    private final TargetResolver targetResolver;
    private final TargetAuthorizationService targetAuthorization;
    private final SnapshotService snapshotService;

    @Inject
    public EnvironmentChangeService(
            TargetResolver targetResolver,
            TargetAuthorizationService targetAuthorization,
            SnapshotService snapshotService) {
        this.targetResolver = targetResolver;
        this.targetAuthorization = targetAuthorization;
        this.snapshotService = snapshotService;
    }

    public List<EnvironmentChange> compare(String targetId, String fromSnapshotId, String toSnapshotId) {
        Target target = targetResolver.require(targetId);
        targetAuthorization.assertAllowed(target, TargetPermission.READ);

        ResolvedSnapshotIds ids = resolveSnapshotIds(targetId, fromSnapshotId, toSnapshotId);

        EnvironmentSnapshotEntity from = snapshotService.findEntity(targetId, ids.fromId())
                .orElseThrow(() -> McpException.invalidArgument("from snapshot not found: " + ids.fromId()));
        EnvironmentSnapshotEntity to = snapshotService.findEntity(targetId, ids.toId())
                .orElseThrow(() -> McpException.invalidArgument("to snapshot not found: " + ids.toId()));

        return diffMaps("", from.summary == null ? Map.of() : from.summary,
                to.summary == null ? Map.of() : to.summary);
    }

    private ResolvedSnapshotIds resolveSnapshotIds(String targetId, String fromSnapshotId, String toSnapshotId) {
        boolean missingFrom = fromSnapshotId == null || fromSnapshotId.isBlank();
        boolean missingTo = toSnapshotId == null || toSnapshotId.isBlank();
        if (!missingFrom && !missingTo) {
            return new ResolvedSnapshotIds(fromSnapshotId, toSnapshotId);
        }

        var page = snapshotService.list(targetId, 0, 2);
        if (page.items().size() < 2) {
            throw McpException.invalidArgument(
                    "at least two snapshots are required when from/to are omitted");
        }
        // list is newest-first
        String toId = missingTo ? page.items().get(0).id() : toSnapshotId;
        String fromId = missingFrom ? page.items().get(1).id() : fromSnapshotId;
        return new ResolvedSnapshotIds(fromId, toId);
    }

    private record ResolvedSnapshotIds(String fromId, String toId) {
    }

    @SuppressWarnings("unchecked")
    private List<EnvironmentChange> diffMaps(String prefix, Map<String, Object> before, Map<String, Object> after) {
        List<EnvironmentChange> changes = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object b = before.get(key);
            Object a = after.get(key);
            if (!before.containsKey(key)) {
                changes.add(new EnvironmentChange(path, "ADDED", null, a));
            } else if (!after.containsKey(key)) {
                changes.add(new EnvironmentChange(path, "REMOVED", b, null));
            } else if (b instanceof Map<?, ?> bm && a instanceof Map<?, ?> am) {
                changes.addAll(diffMaps(path, (Map<String, Object>) bm, (Map<String, Object>) am));
            } else if (!Objects.equals(b, a)) {
                changes.add(new EnvironmentChange(path, "CHANGED", b, a));
            }
        }
        return changes;
    }
}
