package io.github.keycloakmcp.target;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

/**
 * Targets loaded from PostgreSQL. Credential refs only — secrets stay in config/vault.
 */
@ApplicationScoped
@Typed(DatabaseTargetRegistry.class)
public class DatabaseTargetRegistry implements TargetRegistry {

    private final TargetRepository targetRepository;
    private final TargetPersistenceMapper mapper;

    @Inject
    public DatabaseTargetRegistry(TargetRepository targetRepository, TargetPersistenceMapper mapper) {
        this.targetRepository = targetRepository;
        this.mapper = mapper;
    }

    @Override
    public List<Target> list() {
        List<Target> result = new ArrayList<>();
        for (var entity : targetRepository.listAllOrdered()) {
            result.add(mapper.toDomain(entity));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<Target> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return targetRepository.findOptionalById(id.trim()).map(mapper::toDomain);
    }
}
