package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.domain.platform.PageResult;
import io.github.keycloakmcp.persistence.entity.HealthCheckResultEntity;
import io.github.keycloakmcp.persistence.entity.HealthCheckRunEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class HealthCheckRepository implements PanacheRepositoryBase<HealthCheckRunEntity, String> {

    @Inject
    HealthCheckResultRepository resultRepository;

    public PageResult<HealthCheckRunEntity> listByTarget(String targetId, int page, int size) {
        int p = PageResult.clampPage(page);
        int s = PageResult.clampSize(size);
        var query = find("targetId = ?1", Sort.by("createdAt").descending(), targetId);
        long total = query.count();
        List<HealthCheckRunEntity> items = query.page(Page.of(p, s)).list();
        return new PageResult<>(items, p, s, total);
    }

    public Optional<HealthCheckRunEntity> findLatest(String targetId) {
        return find("targetId = ?1", Sort.by("createdAt").descending(), targetId)
                .firstResultOptional();
    }

    public Optional<HealthCheckRunEntity> findByIdForTarget(String id, String targetId) {
        return find("id = ?1 and targetId = ?2", id, targetId).firstResultOptional();
    }

    public List<HealthCheckResultEntity> listResults(String healthCheckId) {
        return resultRepository.listByHealthCheck(healthCheckId);
    }

    @Transactional
    public void persistRunWithResults(HealthCheckRunEntity run, List<HealthCheckResultEntity> results) {
        persist(run);
        for (HealthCheckResultEntity result : results) {
            resultRepository.persist(result);
        }
    }

    @ApplicationScoped
    public static class HealthCheckResultRepository
            implements PanacheRepositoryBase<HealthCheckResultEntity, String> {

        public List<HealthCheckResultEntity> listByHealthCheck(String healthCheckId) {
            return list("healthCheckId = ?1", Sort.by("checkName"), healthCheckId);
        }
    }
}
