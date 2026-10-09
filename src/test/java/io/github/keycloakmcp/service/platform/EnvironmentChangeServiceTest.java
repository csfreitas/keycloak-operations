package io.github.keycloakmcp.service.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.keycloakmcp.domain.platform.EnvironmentChange;
import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.domain.platform.SnapshotSummary;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.security.SensitiveDataFilter;
import io.github.keycloakmcp.target.*;

class EnvironmentChangeServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TargetResolver resolver = mock(TargetResolver.class);
    private final TargetAuthorizationService authorization = mock(TargetAuthorizationService.class);
    private final SnapshotService snapshots = mock(SnapshotService.class);
    private final Target target = new Target(TargetId.of("target-a"), "Target A", TargetType.KEYCLOAK,
            TargetEnvironment.TEST, true,
            new KeycloakTargetConfiguration("https://keycloak.example.test", "master", "reader", "ref"),
            null, null, Map.of());
    private final EnvironmentChangeService service = new EnvironmentChangeService(resolver, authorization, snapshots,
            new SensitiveDataFilter(objectMapper));

    @BeforeEach
    void setUp() {
        when(resolver.require("target-a")).thenReturn(target);
    }

    @Test
    void secretOnlyHistoricalMetadataChangesAreNotDisplayedAndStoredPayloadsStayUntouched() throws Exception {
        var before = snapshot("before", Map.of("notes", "password=first-canary",
                "tags", Map.of("token=first-key-canary", List.of("secret=first-list-canary"))));
        var after = snapshot("after", Map.of("notes", "password=second-canary",
                "tags", Map.of("token=second-key-canary", List.of("secret=second-list-canary"))));
        String originalBefore = objectMapper.writeValueAsString(before.summary);
        String originalAfter = objectMapper.writeValueAsString(after.summary);

        assertThat(service.compare("target-a", "before", "after")).isEmpty();

        assertThat(objectMapper.writeValueAsString(before.summary)).isEqualTo(originalBefore);
        assertThat(objectMapper.writeValueAsString(after.summary)).isEqualTo(originalAfter);
        assertThat(before.snapshotHash).isEqualTo("before-hash");
        assertThat(after.snapshotHash).isEqualTo("after-hash");
    }

    @Test
    void typedFactsAndHistoricalDerivedHashDifferencesRemainVisible() throws Exception {
        var previous = new LinkedHashMap<String, Object>();
        previous.put("readyReplicas", null);
        previous.put("collectionComplete", false);
        previous.put("notes", "token=old-canary");
        snapshot("before", Map.of("inventory", previous, "configurationHash", "old-policy-hash"));
        snapshot("after", Map.of("inventory", Map.of("readyReplicas", 2, "collectionComplete", true,
                "notes", "token=new-canary"), "configurationHash", "new-policy-hash"));

        var changes = service.compare("target-a", "before", "after");

        assertThat(changes).containsExactlyInAnyOrder(
                new EnvironmentChange("inventory.readyReplicas", "CHANGED", null, 2),
                new EnvironmentChange("inventory.collectionComplete", "CHANGED", false, true),
                new EnvironmentChange("configurationHash", "CHANGED", "old-policy-hash", "new-policy-hash"));
        assertThat(objectMapper.writeValueAsString(changes)).doesNotContain("-canary");
    }

    @Test
    void addedAndRemovedDynamicKeysAndValuesAreSafeInDiffPaths() throws Exception {
        snapshot("before", Map.of("tags", Map.of("token=path-canary", "password=removed-canary")));
        snapshot("after", Map.of("tags", Map.of(), "notes", List.of("secret=added-canary")));

        var changes = service.compare("target-a", "before", "after");

        assertThat(changes).hasSize(2).anySatisfy(change -> {
            assertThat(change.path()).isEqualTo("tags.[REDACTED KEY]");
            assertThat(change.changeType()).isEqualTo("REMOVED");
        });
        assertThat(objectMapper.writeValueAsString(changes)).doesNotContain("-canary");
    }

    @Test
    void omittedIdsUseTheSameAuthorizedSanitizedComparison() {
        snapshot("before", null);
        snapshot("after", Map.of("notes", "password=latest-canary", "readyReplicas", 0));
        when(snapshots.list("target-a", 0, 2)).thenReturn(new PageResult<>(List.of(
                new SnapshotSummary("after", "target-a", "after-hash", Instant.EPOCH),
                new SnapshotSummary("before", "target-a", "before-hash", Instant.EPOCH)), 0, 2, 2));

        var changes = service.compare("target-a", null, null);

        assertThat(changes).contains(new EnvironmentChange("readyReplicas", "ADDED", null, 0));
        assertThat(changes.toString()).doesNotContain("latest-canary");
        verify(authorization).assertAllowed(target, TargetPermission.READ);
        verify(snapshots).findEntity("target-a", "before");
        verify(snapshots).findEntity("target-a", "after");
    }

    @Test
    void authorizationFailurePrecedesAllSnapshotReads() {
        doThrow(new SecurityException("denied")).when(authorization).assertAllowed(target, TargetPermission.READ);

        assertThatThrownBy(() -> service.compare("target-a", "before", "after"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(snapshots);
    }

    private EnvironmentSnapshotEntity snapshot(String id, Map<String, Object> summary) {
        var entity = new EnvironmentSnapshotEntity();
        entity.id = id;
        entity.targetId = "target-a";
        entity.snapshotHash = id + "-hash";
        entity.summary = summary;
        when(snapshots.findEntity("target-a", id)).thenReturn(Optional.of(entity));
        return entity;
    }
}
