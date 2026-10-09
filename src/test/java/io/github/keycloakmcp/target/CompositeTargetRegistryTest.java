package io.github.keycloakmcp.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.config.PlatformConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;

class CompositeTargetRegistryTest {
    private static final String INITIALIZATION_ERROR =
            "Target registry initialization failed; configuration was not used as a fallback";
    private final PlatformConfig platform = mock(PlatformConfig.class);
    private final ConfigurationTargetRegistry configured = mock(ConfigurationTargetRegistry.class);
    private final DatabaseTargetRegistry database = mock(DatabaseTargetRegistry.class);
    private final TargetBootstrapService bootstrap = mock(TargetBootstrapService.class);
    private final CompositeTargetRegistry registry = new CompositeTargetRegistry(platform, configured, database, bootstrap);

    @ParameterizedTest
    @ValueSource(strings = {"configuration", "config", "CONFIGURATION", " config "})
    void explicitConfigurationModeReadsOnlyTheConfiguration(String mode) {
        when(platform.targetRegistry()).thenReturn(mode);
        Target target = target("config-only", true);
        when(configured.list()).thenReturn(List.of(target));
        when(configured.findById("config-only")).thenReturn(Optional.of(target));

        registry.init();

        assertThat(registry.list()).containsExactly(target);
        assertThat(registry.findById("config-only")).contains(target);
        verifyNoInteractions(database, bootstrap);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "db", "composite", "", "unknown-legacy-default"})
    void persistentModesInitializeThenUseOnlyTheDatabase(String mode) {
        when(platform.targetRegistry()).thenReturn(mode);
        Target target = target("database-only", true);
        when(database.list()).thenReturn(List.of(target));
        when(database.findById("database-only")).thenReturn(Optional.of(target));

        registry.init();

        assertThat(registry.list()).containsExactly(target);
        assertThat(registry.findById("database-only")).contains(target);
        verify(bootstrap).syncConfigTargetsToDatabase();
        verifyNoInteractions(configured);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "composite"})
    void emptyDatabaseNeverResurrectsConfiguredTargets(String mode) {
        when(platform.targetRegistry()).thenReturn(mode);
        when(database.list()).thenReturn(List.of());
        when(database.findById("configuration-candidate")).thenReturn(Optional.empty());
        when(configured.list()).thenReturn(List.of(target("configuration-candidate", true)));
        when(configured.findById("configuration-candidate")).thenReturn(Optional.of(target("configuration-candidate", true)));

        registry.init();

        assertThat(registry.list()).isEmpty();
        assertThat(registry.findById("configuration-candidate")).isEmpty();
        verifyNoInteractions(configured);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "composite"})
    void disabledDatabaseRecordRemainsPresentAndCannotFallBackToEnabledConfiguration(String mode) {
        when(platform.targetRegistry()).thenReturn(mode);
        Target disabled = target("disabled", false);
        when(database.list()).thenReturn(List.of(disabled));
        when(database.findById("disabled")).thenReturn(Optional.of(disabled));
        when(configured.findById("disabled")).thenReturn(Optional.of(target("disabled", true)));

        registry.init();

        assertThat(registry.list()).containsExactly(disabled);
        assertThat(registry.findById("disabled")).contains(disabled);
        assertThatThrownBy(() -> new TargetResolver(registry).require("disabled"))
                .isInstanceOfSatisfying(McpException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo(ErrorCode.TARGET_DISABLED));
        verifyNoInteractions(configured);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "composite"})
    void bootstrapFailureStopsInitializationWithFixedDiagnosticsAndNoCause(String mode) {
        when(platform.targetRegistry()).thenReturn(mode);
        when(bootstrap.syncConfigTargetsToDatabase()).thenThrow(new IllegalStateException(
                "CANARY_PRIVATE_ENDPOINT password=synthetic-canary", new RuntimeException("CANARY_NESTED")));

        assertThatThrownBy(registry::init).isInstanceOf(IllegalStateException.class)
                .hasMessage(INITIALIZATION_ERROR).hasNoCause();

        verifyNoInteractions(configured, database);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "composite"})
    void databaseReadFailuresPropagateInsteadOfFallbackOrAnEmptyFleet(String mode) {
        when(platform.targetRegistry()).thenReturn(mode);
        registry.init();
        RuntimeException failure = new IllegalStateException("synthetic database unavailable");
        when(database.list()).thenThrow(failure);
        when(database.findById("candidate")).thenThrow(failure);

        assertThatThrownBy(registry::list).isSameAs(failure);
        assertThatThrownBy(() -> registry.findById("candidate")).isSameAs(failure);

        verifyNoInteractions(configured);
    }

    @Test
    void missingIdDoesNotInspectOtherTargetsToMakeAFallbackDecision() {
        when(platform.targetRegistry()).thenReturn("composite");
        when(database.findById("missing")).thenReturn(Optional.empty());
        registry.init();

        assertThat(registry.findById("missing")).isEmpty();

        verify(database, never()).list();
        verifyNoInteractions(configured);
    }

    private static Target target(String id, boolean enabled) {
        return new Target(TargetId.of(id), "Synthetic target", TargetType.KEYCLOAK, TargetEnvironment.TEST, enabled,
                new KeycloakTargetConfiguration("https://source.example.invalid", "master", "reader", "reference"),
                null, null, Map.of());
    }
}
