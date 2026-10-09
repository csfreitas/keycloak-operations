package io.github.keycloakmcp.service.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.config.ConfigurationReadConfig;
import io.github.keycloakmcp.config.PlatformAuthorizationConfig;
import io.github.keycloakmcp.domain.configuration.ConfigurationKind;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Caller;
import io.github.keycloakmcp.service.configuration.ConfigurationReadPolicy.Channel;
import io.quarkus.security.identity.SecurityIdentity;

class ConfigurationReadPolicyTest {
    private static final String ROLE = "application-reader";
    private static final String TARGET = "target-a";
    private static final String REALM_ID = "realm-immutable-id";
    private static final String CLIENT_ID = "client-immutable-id";
    private final ConfigurationReadConfig config = mock(ConfigurationReadConfig.class);
    private final PlatformAuthorizationConfig authorization = mock(PlatformAuthorizationConfig.class);
    private final SecurityIdentity identity = mock(SecurityIdentity.class);
    private final JsonWebToken jwt = mock(JsonWebToken.class);

    @BeforeEach
    void authenticatedFixture() {
        when(authorization.mode()).thenReturn("authenticated");
        when(identity.getPrincipal()).thenReturn(jwt);
        when(jwt.getIssuer()).thenReturn("https://issuer.invalid/realms/platform");
        when(jwt.getSubject()).thenReturn("synthetic-human-a");
        when(jwt.getClaim("azp")).thenReturn("operator-console");
        when(identity.hasRole(ROLE)).thenReturn(true);
        var scope = scope(ConfigurationKind.REALM);
        when(config.scopes()).thenReturn(Map.of("realm-policy", scope));
    }

    @Test
    void callerUsesOnlyVerifiedJwtPrincipalAndReturnsBoundedFingerprints() {
        Caller caller = policy().caller();
        assertThat(caller.actorFingerprint()).matches("[a-f0-9]{64}")
                .doesNotContain("synthetic-human", "issuer.invalid");
        assertThat(caller.clientFingerprint()).matches("[a-f0-9]{64}").doesNotContain("operator-console");
        assertThat(caller.clientId()).isEqualTo("operator-console");
        assertThat(policy().caller()).isEqualTo(caller);
    }

    @Test
    void actorFingerprintHasUnambiguousIssuerAndSubjectFraming() {
        when(jwt.getIssuer()).thenReturn("issuer#a");
        when(jwt.getSubject()).thenReturn("b");
        String first = policy().caller().actorFingerprint();
        when(jwt.getIssuer()).thenReturn("issuer");
        when(jwt.getSubject()).thenReturn("a#b");
        assertThat(policy().caller().actorFingerprint()).isNotEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local-lab", "", "unknown"})
    void localLabAndUnknownModesCannotBypassTheNewAuthenticationBoundary(String mode) {
        when(authorization.mode()).thenReturn(mode);
        assertAuthenticationDenied(() -> policy().caller());
    }

    @Test
    void anonymousOrNonJwtPrincipalDoesNotBecomeARestrictedHuman() {
        when(identity.isAnonymous()).thenReturn(true);
        assertAuthenticationDenied(() -> policy().caller());
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn((Principal) () -> "synthetic-unverified-user");
        assertAuthenticationDenied(() -> policy().caller());
        when(identity.getPrincipal()).thenReturn(null);
        assertAuthenticationDenied(() -> policy().caller());
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void issuerAndSubjectMustBeNonemptyBoundedClaims(String claim) {
        when(jwt.getIssuer()).thenReturn(claim);
        assertAuthenticationDenied(() -> policy().caller());
        when(jwt.getIssuer()).thenReturn("https://issuer.invalid");
        when(jwt.getSubject()).thenReturn(claim);
        assertAuthenticationDenied(() -> policy().caller());
    }

    @ParameterizedTest
    @MethodSource("invalidAuthorizedParties")
    void missingMalformedOrNonstringAuthorizedPartyCannotSelectClientScope(Object claim) {
        when(jwt.getClaim("azp")).thenReturn(claim);
        assertAuthenticationDenied(() -> policy().caller());
    }

    @Test
    void roleClientAndTransportAllParticipateInEachScopeDecision() {
        ConfigurationReadPolicy policy = policy();
        Caller caller = policy.caller();
        assertThat(policy.list(Channel.REST, caller)).extracting(scope -> scope.scopeId())
                .containsExactly("realm-policy");
        assertThat(policy.require("realm-policy", Channel.REST, caller).realmId()).isEqualTo(REALM_ID);
        assertThat(policy.list(Channel.MCP, caller)).isEmpty();
        assertScopeDenied(() -> policy.require("realm-policy", Channel.MCP, caller));
        when(jwt.getClaim("azp")).thenReturn("approved-agent");
        Caller agent = policy.caller();
        assertThat(policy.list(Channel.MCP, agent)).hasSize(1);
        assertThat(policy.list(Channel.REST, agent)).isEmpty();
        when(identity.hasRole(ROLE)).thenReturn(false);
        assertThat(policy.list(Channel.MCP, agent)).isEmpty();
        assertScopeDenied(() -> policy.require("realm-policy", Channel.MCP, agent));
    }

    @Test
    void legacyReadOrAdminRoleDoesNotImplicitlyGrantConfigurationScopes() {
        when(identity.hasRole(ROLE)).thenReturn(false);
        when(identity.hasRole("legacy-admin")).thenReturn(true);
        ConfigurationReadPolicy policy = policy();
        assertThat(policy.list(Channel.REST, policy.caller())).isEmpty();
        assertScopeDenied(() -> policy.require("realm-policy", Channel.REST, policy.caller()));
    }

    @Test
    void fabricatedOrStaleCallerCannotSelectOrListGrants() {
        ConfigurationReadPolicy policy = policy();
        Caller original = policy.caller();
        Caller fabricated = new Caller(original.actorFingerprint(), "approved-agent", "f".repeat(64));
        assertScopeDenied(() -> policy.list(Channel.MCP, fabricated));
        assertScopeDenied(() -> policy.require("realm-policy", Channel.MCP, fabricated));
        when(jwt.getSubject()).thenReturn("synthetic-human-b");
        assertScopeDenied(() -> policy.list(Channel.REST, original));
        assertScopeDenied(() -> policy.require("realm-policy", Channel.REST, original));
    }

    @Test
    void unapprovedClientAndAbsentTransportGrantsRemainClosed() {
        when(jwt.getClaim("azp")).thenReturn("another-client");
        ConfigurationReadPolicy policy = policy();
        assertThat(policy.list(Channel.REST, policy.caller())).isEmpty();
        assertScopeDenied(() -> policy.require("realm-policy", Channel.REST, policy.caller()));
        var scope = scope(ConfigurationKind.REALM);
        when(scope.restClientIds()).thenReturn(Optional.empty());
        when(scope.mcpClientIds()).thenReturn(Optional.empty());
        when(config.scopes()).thenReturn(Map.of("realm-policy", scope));
        when(jwt.getClaim("azp")).thenReturn("operator-console");
        ConfigurationReadPolicy noTransportPolicy = policy();
        assertThat(noTransportPolicy.list(Channel.REST, noTransportPolicy.caller())).isEmpty();
        assertThat(noTransportPolicy.list(Channel.MCP, noTransportPolicy.caller())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "../realm-policy", "REALM-POLICY", "realm-policy\n", ""})
    void unknownAndMalformedHandlesHaveTheSameSafeDenial(String handle) {
        ConfigurationReadPolicy policy = policy();
        assertScopeDenied(() -> policy.require(handle, Channel.REST, policy.caller()));
    }

    @Test
    void reauthorizationRejectsChangedPrincipalClientRoleAndOriginalCaller() {
        ConfigurationReadPolicy policy = policy();
        Caller original = policy.caller();
        var grant = policy.require("realm-policy", Channel.REST, original);
        policy.reauthorize(grant, Channel.REST, original);
        assertScopeDenied(() -> policy.reauthorize(grant, Channel.REST,
                new Caller("f".repeat(64), original.clientId(), original.clientFingerprint())));
        when(jwt.getSubject()).thenReturn("synthetic-human-b");
        assertScopeDenied(() -> policy.reauthorize(grant, Channel.REST, original));
        when(jwt.getSubject()).thenReturn("synthetic-human-a");
        when(jwt.getClaim("azp")).thenReturn("approved-agent");
        assertScopeDenied(() -> policy.reauthorize(grant, Channel.REST, original));
        when(jwt.getClaim("azp")).thenReturn("operator-console");
        when(identity.hasRole(ROLE)).thenReturn(false);
        assertScopeDenied(() -> policy.reauthorize(grant, Channel.REST, original));
    }

    @Test
    void scopeCatalogueIsSortedDetachedAndContainsOnlyApprovedDescriptors() {
        var scope = scope(ConfigurationKind.CLIENT);
        Set<String> fields = new HashSet<>(Set.of("enabled", "publicClient"));
        Set<String> clients = new HashSet<>(Set.of("operator-console"));
        when(scope.fields()).thenReturn(fields);
        when(scope.restClientIds()).thenReturn(Optional.of(clients));
        Map<String, ConfigurationReadConfig.Scope> scopes = new HashMap<>();
        scopes.put("z-client", scope); scopes.put("a-client", scope);
        when(config.scopes()).thenReturn(scopes);
        ConfigurationReadPolicy policy = policy();
        fields.clear(); clients.clear(); scopes.clear();
        var allowed = policy.list(Channel.REST, policy.caller());
        assertThat(allowed).extracting(descriptor -> descriptor.scopeId()).containsExactly("a-client", "z-client");
        assertThat(allowed.getFirst().fields()).containsExactly("enabled", "publicClient");
        assertThatThrownBy(() -> allowed.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> allowed.getFirst().fields().clear()).isInstanceOf(UnsupportedOperationException.class);
        var grant = policy.require("a-client", Channel.REST, policy.caller());
        assertThat(grant.clientId()).isEqualTo(CLIENT_ID);
        assertThatThrownBy(() -> grant.restClients().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyConfigurationProvidesNoScopes() {
        when(config.scopes()).thenReturn(Map.of());
        ConfigurationReadPolicy policy = policy();
        assertThat(policy.list(Channel.REST, policy.caller())).isEmpty();
        assertScopeDenied(() -> policy.require("realm-policy", Channel.REST, policy.caller()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad/handle", "Uppercase", "", "a________________________________________________________________"})
    void malformedConfiguredHandleFailsWithFixedDiagnostics(String handle) {
        var scope = scope(ConfigurationKind.REALM);
        when(config.scopes()).thenReturn(Map.of(handle, scope));
        assertInvalidConfig();
    }

    @Test
    void invalidFieldsIdentityPinsClientPresenceAndOversizedAllowlistsFailClosed() {
        List<Consumer<ConfigurationReadConfig.Scope>> invalid = List.of(
                scope -> when(scope.role()).thenReturn("raw-person@example.invalid\n"),
                scope -> when(scope.realm()).thenReturn("../other"),
                scope -> when(scope.realmId()).thenReturn(""),
                scope -> when(scope.targetId()).thenReturn(null),
                scope -> when(scope.targetId()).thenReturn("t".repeat(129)),
                scope -> when(scope.targetId()).thenReturn(" target-a "),
                scope -> when(scope.targetId()).thenReturn("https://example.invalid"),
                scope -> when(scope.kind()).thenReturn(null),
                scope -> when(scope.fields()).thenReturn(Set.of()),
                scope -> when(scope.fields()).thenReturn(Set.of("secret")),
                scope -> when(scope.fields()).thenReturn(Set.of("enabled", "publicClient")),
                scope -> when(scope.clientId()).thenReturn(Optional.of(CLIENT_ID)),
                scope -> when(scope.restClientIds()).thenReturn(Optional.of(Set.of("bad client"))),
                scope -> when(scope.mcpClientIds()).thenReturn(Optional.of(
                        java.util.stream.IntStream.range(0, 21).mapToObj(i -> "client-" + i).collect(java.util.stream.Collectors.toSet()))));
        for (Consumer<ConfigurationReadConfig.Scope> mutate : invalid) {
            var scope = scope(ConfigurationKind.REALM); mutate.accept(scope);
            when(config.scopes()).thenReturn(Map.of("realm-policy", scope));
            assertInvalidConfig();
        }
        var client = scope(ConfigurationKind.CLIENT);
        when(client.clientId()).thenReturn(Optional.empty());
        when(config.scopes()).thenReturn(Map.of("client-auth", client));
        assertInvalidConfig();
    }

    @Test
    void grantConstructorDefensivelyCopiesTransportAllowlists() {
        Set<String> rest = new HashSet<>(Set.of("operator-console"));
        Set<String> mcp = new HashSet<>(Set.of("approved-agent"));
        var descriptor = policy().list(Channel.REST, policy().caller()).getFirst();
        var grant = new ConfigurationReadPolicy.Grant(descriptor, REALM_ID, null, ROLE, rest, mcp);
        rest.clear(); mcp.clear();
        assertThat(grant.restClients()).containsExactly("operator-console");
        assertThat(grant.mcpClients()).containsExactly("approved-agent");
        assertThatThrownBy(() -> grant.mcpClients().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void moreThanOneHundredConfiguredScopesFailsBeforeMaterializingTheCatalogue() {
        Map<String, ConfigurationReadConfig.Scope> scopes = new HashMap<>();
        for (int i = 0; i < 101; i++) scopes.put("scope-" + i, scope(ConfigurationKind.REALM));
        when(config.scopes()).thenReturn(scopes);
        assertInvalidConfig();
    }

    private ConfigurationReadPolicy policy() { return new ConfigurationReadPolicy(config, authorization, identity); }

    private static ConfigurationReadConfig.Scope scope(ConfigurationKind kind) {
        var scope = mock(ConfigurationReadConfig.Scope.class);
        when(scope.role()).thenReturn(ROLE);
        when(scope.targetId()).thenReturn(TARGET);
        when(scope.realm()).thenReturn("realm-a");
        when(scope.realmId()).thenReturn(REALM_ID);
        when(scope.kind()).thenReturn(kind);
        when(scope.clientId()).thenReturn(kind == ConfigurationKind.CLIENT ? Optional.of(CLIENT_ID) : Optional.empty());
        when(scope.fields()).thenReturn(Set.of("enabled"));
        when(scope.restClientIds()).thenReturn(Optional.of(Set.of("operator-console")));
        when(scope.mcpClientIds()).thenReturn(Optional.of(Set.of("approved-agent")));
        return scope;
    }

    private void assertInvalidConfig() {
        assertThatThrownBy(this::policy).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid configuration read scope").hasNoCause();
    }
    private static void assertAuthenticationDenied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(McpException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.AUTHENTICATION_FAILED));
    }
    private static void assertScopeDenied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(McpException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.AUTHORIZATION_FAILED))
                .hasMessage("Configuration scope is not authorized").hasNoCause();
    }
    private static Stream<String> invalidClaims() { return Stream.of(null, "", " ", "x".repeat(2049)); }
    private static Stream<Object> invalidAuthorizedParties() { return Stream.of(null, "", "bad client", "x".repeat(129), 42, List.of("operator-console")); }
}
