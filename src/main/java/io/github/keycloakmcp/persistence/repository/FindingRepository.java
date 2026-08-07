package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.AssessmentFindingEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FindingRepository implements PanacheRepositoryBase<AssessmentFindingEntity, String> {

    public List<AssessmentFindingEntity> listByAssessment(String assessmentId) {
        return list("assessmentId = ?1", Sort.by("severity").and("createdAt"), assessmentId);
    }

    public PageResult<AssessmentFindingEntity> listByTarget(
            String targetId,
            Optional<String> lifecycleStatus,
            Optional<String> severity,
            int page,
            int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);

        StringBuilder jpql = new StringBuilder("targetId = ?1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(targetId);
        int idx = 2;
        if (lifecycleStatus.isPresent() && !lifecycleStatus.get().isBlank()) {
            jpql.append(" and lifecycleStatus = ?").append(idx++);
            params.add(lifecycleStatus.get().trim().toUpperCase());
        }
        if (severity.isPresent() && !severity.get().isBlank()) {
            jpql.append(" and severity = ?").append(idx);
            params.add(severity.get().trim().toUpperCase());
        }

        var query = find(jpql.toString(), Sort.by("createdAt").descending(), params.toArray());
        long total = query.count();
        List<AssessmentFindingEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }
}
