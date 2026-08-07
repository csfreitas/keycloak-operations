package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.AssessmentRunEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AssessmentRepository implements PanacheRepositoryBase<AssessmentRunEntity, String> {

    public PageResult<AssessmentRunEntity> listByTarget(String targetId, int page, int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);
        var query = find("targetId = ?1", Sort.by("createdAt").descending(), targetId);
        long total = query.count();
        List<AssessmentRunEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }

    public Optional<AssessmentRunEntity> findLatest(String targetId) {
        return find("targetId = ?1", Sort.by("createdAt").descending(), targetId)
                .firstResultOptional();
    }

    public Optional<AssessmentRunEntity> findByIdForTarget(String id, String targetId) {
        return find("id = ?1 and targetId = ?2", id, targetId).firstResultOptional();
    }
}
