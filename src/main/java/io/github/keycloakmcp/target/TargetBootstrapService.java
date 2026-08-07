package io.github.keycloakmcp.target;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.persistence.entity.TargetEntity;
import io.github.keycloakmcp.persistence.mapper.TargetPersistenceMapper;
import io.github.keycloakmcp.persistence.repository.TargetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Seeds / syncs configuration targets into the database without writing secrets.
 */
@ApplicationScoped
public class TargetBootstrapService {

    private static final Logger LOG = Logger.getLogger(TargetBootstrapService.class);

    private final ConfigurationTargetRegistry configurationRegistry;
    private final TargetRepository targetRepository;
    private final TargetPersistenceMapper mapper;

    @Inject
    public TargetBootstrapService(
            ConfigurationTargetRegistry configurationRegistry,
            TargetRepository targetRepository,
            TargetPersistenceMapper mapper) {
        this.configurationRegistry = configurationRegistry;
        this.targetRepository = targetRepository;
        this.mapper = mapper;
    }

    /**
     * Inserts missing config targets into DB; updates existing rows' non-secret fields.
     *
     * @return number of targets upserted
     */
    @Transactional
    public int syncConfigTargetsToDatabase() {
        int count = 0;
        for (Target target : configurationRegistry.list()) {
            upsert(target);
            count++;
        }
        LOG.infof("Bootstrapped/synced %d target(s) from configuration into database", count);
        return count;
    }

    private void upsert(Target target) {
        var existing = targetRepository.findOptionalById(target.id().value());
        if (existing.isPresent()) {
            TargetEntity entity = existing.get();
            mapper.updateEntity(target, entity);
        } else {
            TargetEntity entity = mapper.toEntity(target);
            targetRepository.persist(entity);
        }
    }
}
