package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.AuditEventEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuditRepository implements PanacheRepositoryBase<AuditEventEntity, String> {

    public PageResult<AuditEventEntity> listForTargets(
            java.util.Set<String> targetIds, Optional<String> source, int page, int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);
        if (targetIds.isEmpty()) {
            return new PageResult<>(List.of(), p, s, 0);
        }
        String jpql = "targetId in ?1";
        var params = new java.util.ArrayList<Object>();
        params.add(targetIds);
        if (source.isPresent() && !source.get().isBlank()) {
            jpql += " and source = ?2";
            params.add(source.get().trim().toUpperCase(java.util.Locale.ROOT));
        }
        var query = find(jpql, Sort.by("createdAt").descending(), params.toArray());
        long total = query.count();
        return new PageResult<>(query.page(Page.of(p, s)).list(), p, s, total);
    }

    public PageResult<AuditEventEntity> list(
            Optional<String> targetId,
            Optional<String> source,
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
        if (source.isPresent() && !source.get().isBlank()) {
            jpql.append(" and source = ?").append(idx);
            params.add(source.get().trim().toUpperCase());
        }

        var query = params.isEmpty()
                ? find(jpql.toString(), Sort.by("createdAt").descending())
                : find(jpql.toString(), Sort.by("createdAt").descending(), params.toArray());
        long total = query.count();
        List<AuditEventEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }

    public PageResult<AuditEventEntity> listByTarget(String targetId, int page, int size) {
        return list(Optional.ofNullable(targetId), Optional.empty(), page, size);
    }

    public Optional<AuditEventEntity> findByTraceId(String traceId) {
        return find("traceId = ?1", Sort.by("createdAt").descending(), traceId).firstResultOptional();
    }
}
