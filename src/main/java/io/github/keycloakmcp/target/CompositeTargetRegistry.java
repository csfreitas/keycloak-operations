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
 * Default {@link TargetRegistry}: database authority after successful configuration bootstrap.
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
        } catch (RuntimeException failedBootstrap) {
            // Do not activate a different source after ownership, transaction or database failure.
            LOG.warn("Target registry initialization failed; configuration was not used as a fallback");
            throw new IllegalStateException("Target registry initialization failed; configuration was not used as a fallback");
        }
    }

    @Override
    public List<Target> list() {
        return switch (mode) {
            case CONFIGURATION -> configurationRegistry.list();
            case DATABASE, COMPOSITE -> databaseRegistry.list();
        };
    }

    @Override
    public Optional<Target> findById(String id) {
        return switch (mode) {
            case CONFIGURATION -> configurationRegistry.findById(id);
            case DATABASE, COMPOSITE -> databaseRegistry.findById(id);
        };
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
