package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.EnvironmentSnapshotEntity;
import io.github.keycloakmcp.persistence.entity.InventorySnapshotEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SnapshotRepository implements PanacheRepositoryBase<EnvironmentSnapshotEntity, String> {

    @Inject
    InventorySnapshotRepository inventoryRepository;

    public PageResult<EnvironmentSnapshotEntity> listByTarget(String targetId, int page, int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);
        var query = find("targetId = ?1", Sort.by("createdAt").descending(), targetId);
        long total = query.count();
        List<EnvironmentSnapshotEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }

    public Optional<EnvironmentSnapshotEntity> findLatest(String targetId) {
        return find("targetId = ?1", Sort.by("createdAt").descending(), targetId)
                .firstResultOptional();
    }

    public Optional<EnvironmentSnapshotEntity> findByIdForTarget(String id, String targetId) {
        return find("id = ?1 and targetId = ?2", id, targetId).firstResultOptional();
    }

    @Transactional
    public void persistWithInventory(EnvironmentSnapshotEntity env, InventorySnapshotEntity inventory) {
        persist(env);
        if (inventory != null) {
            inventoryRepository.persist(inventory);
        }
    }

    @ApplicationScoped
    public static class InventorySnapshotRepository
            implements PanacheRepositoryBase<InventorySnapshotEntity, String> {
    }
}
