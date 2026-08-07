package io.github.keycloakmcp.target;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.github.keycloakmcp.config.PlatformConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Default {@link TargetRegistry}: prefers database after seeding from configuration.
 * Modes: {@code configuration}, {@code database}, {@code composite} (default).
 */
@ApplicationScoped
public class CompositeTargetRegistry implements TargetRegistry {

    private static final Logger LOG = Logger.getLogger(CompositeTargetRegistry.class);

    private final PlatformConfig platformConfig;
    private final ConfigurationTargetRegistry configurationRegistry;
    private final DatabaseTargetRegistry databaseRegistry;
    private final TargetBootstrapService bootstrapService;

    private volatile Mode mode = Mode.COMPOSITE;

    @Inject
    public CompositeTargetRegistry(
            PlatformConfig platformConfig,
            ConfigurationTargetRegistry configurationRegistry,
            DatabaseTargetRegistry databaseRegistry,
            TargetBootstrapService bootstrapService) {
        this.platformConfig = platformConfig;
        this.configurationRegistry = configurationRegistry;
        this.databaseRegistry = databaseRegistry;
        this.bootstrapService = bootstrapService;
    }

    @PostConstruct
    void init() {
        mode = Mode.parse(platformConfig.targetRegistry());
        LOG.infof("CompositeTargetRegistry mode=%s", mode);
        if (mode == Mode.CONFIGURATION) {
            return;
        }
        try {
            bootstrapService.syncConfigTargetsToDatabase();
        } catch (RuntimeException e) {
            LOG.warnf(e, "Target bootstrap from configuration failed; falling back where possible");
        }
    }

    @Override
    public List<Target> list() {
        return switch (mode) {
            case CONFIGURATION -> configurationRegistry.list();
            case DATABASE -> databaseRegistry.list();
            case COMPOSITE -> preferDatabaseOrConfig();
        };
    }

    @Override
    public Optional<Target> findById(String id) {
        return switch (mode) {
            case CONFIGURATION -> configurationRegistry.findById(id);
            case DATABASE -> databaseRegistry.findById(id);
            case COMPOSITE -> {
                Optional<Target> fromDb = databaseRegistry.findById(id);
                if (fromDb.isPresent()) {
                    yield fromDb;
                }
                if (databaseRegistry.list().isEmpty()) {
                    yield configurationRegistry.findById(id);
                }
                yield Optional.empty();
            }
        };
    }

    private List<Target> preferDatabaseOrConfig() {
        List<Target> db = databaseRegistry.list();
        if (!db.isEmpty()) {
            return db;
        }
        List<Target> config = configurationRegistry.list();
        if (!config.isEmpty()) {
            LOG.debug("Database target table empty; using configuration targets");
        }
        return config;
    }

    enum Mode {
        CONFIGURATION,
        DATABASE,
        COMPOSITE;

        static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return COMPOSITE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "configuration", "config" -> CONFIGURATION;
                case "database", "db" -> DATABASE;
                default -> COMPOSITE;
            };
        }
    }
}
