package io.github.keycloakmcp.persistence.repository;

import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TargetRepository implements PanacheRepositoryBase<TargetEntity, String> {

    public List<TargetEntity> listAllOrdered() {
        return listAll(Sort.by("id"));
    }

    public Optional<TargetEntity> findOptionalById(String id) {
        return findByIdOptional(id);
    }

    public boolean existsById(String id) {
        return findByIdOptional(id).isPresent();
    }
}
