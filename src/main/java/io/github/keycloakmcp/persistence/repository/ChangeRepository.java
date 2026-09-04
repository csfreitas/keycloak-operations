package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.ChangeRecordEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class ChangeRepository implements PanacheRepositoryBase<ChangeRecordEntity, String> {

    /** Serializes lifecycle mutations of one plan; not a distributed transaction with Keycloak. */
    public Optional<ChangeRecordEntity> findByIdForUpdate(String id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public PageResult<ChangeRecordEntity> list(
            Optional<String> targetId,
            Optional<String> status,
            int page,
            int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);

        StringBuilder jpql = new StringBuilder("1 = 1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        int idx = 1;
        if (targetId.isPresent() && !targetId.get().isBlank()) {
            jpql.append(" and targetId = ?").append(idx++);
            params.add(targetId.get().trim());
        }
        if (status.isPresent() && !status.get().isBlank()) {
            jpql.append(" and status = ?").append(idx);
            params.add(status.get().trim().toUpperCase());
        }

        var query = params.isEmpty()
                ? find(jpql.toString(), Sort.by("createdAt").descending())
                : find(jpql.toString(), Sort.by("createdAt").descending(), params.toArray());
        long total = query.count();
        List<ChangeRecordEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }

    public Optional<ChangeRecordEntity> findByIdempotency(String targetId, String idempotencyKey) {
        if (targetId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return find("targetId = ?1 and idempotencyKey = ?2", targetId, idempotencyKey).firstResultOptional();
    }
}
