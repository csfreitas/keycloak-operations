package io.github.keycloakmcp.service.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.keycloakmcp.config.PlatformAuthorizationConfig;
import io.github.keycloakmcp.config.RegistryAdministrationConfig;
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.config.SmallRyeConfigBuilder;

class RegistryPreflightPolicyTest {
    private static final String ISSUER = "https://idp.example.invalid/realms/platform";
    private static final String SUBJECT = "synthetic-admin-a";
    private static final String ROLE = "registry-preflight";
    private static final String CLIENT = "operator-console";
    private static final String CANARY = "synthetic-private-identity-canary";

    private final RegistryAdministrationConfig config = mock(RegistryAdministrationConfig.class);
    private final PlatformAuthorizationConfig authorization = mock(PlatformAuthorizationConfig.class);
    private final SecurityIdentity identity = mock(SecurityIdentity.class);
    private final JsonWebToken jwt = mock(JsonWebToken.class);

    @BeforeEach
    void fixture() {
        when(config.enabled()).thenReturn(true);
        doReturn(Map.of("operator-a", administrator(ISSUER, SUBJECT, ROLE, Set.of(CLIENT))))
                .when(config).administrators();
        when(authorization.mode()).thenReturn("authenticated");
        when(identity.getPrincipal()).thenReturn(jwt);
        when(identity.hasRole(ROLE)).thenReturn(true);
        when(jwt.getIssuer()).thenReturn(ISSUER);
        when(jwt.getSubject()).thenReturn(SUBJECT);
        when(jwt.getClaim("azp")).thenReturn(CLIENT);
    }

    @Test
    void absentConfigurationDefaultsToDisabledWithNoAdministrators() {
        var defaults = new SmallRyeConfigBuilder().withMapping(RegistryAdministrationConfig.class)
                .build().getConfigMapping(RegistryAdministrationConfig.class);
        assertThat(defaults.enabled()).isFalse();
        assertThat(defaults.administrators()).isEmpty();
        assertDenied(() -> new RegistryPreflightPolicy(defaults, authorization, identity).assertAllowed());
        verifyNoInteractions(authorization, identity, jwt);
    }

    @Test
    void disabledConfigurationDoesNotConsultTheIdentityOrLegacyGrants() {
        when(config.enabled()).thenReturn(false);
        assertDenied(() -> policy().assertAllowed());
        verifyNoInteractions(authorization, identity, jwt);
    }

    @Test
    void explicitlyNamedPrincipalWithRoleAndApprovedRestClientIsAllowed() {
        assertThatCode(() -> policy().assertAllowed()).doesNotThrowAnyException();
        verify(identity).hasRole(ROLE);
        verify(authorization, org.mockito.Mockito.never()).grants();
    }

    @Test
    void emptyAdministratorListGrantsNothingEvenWhenEnabled() {
        when(config.administrators()).thenReturn(Map.of());
        assertDenied(() -> policy().assertAllowed());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"local-lab", "dev", "AUTHENTICATED", " authenticated", "unknown"})
    void onlyExactAuthenticatedModeIsAccepted(String mode) {
        when(authorization.mode()).thenReturn(mode);
        assertAuthenticationDenied(() -> policy().assertAllowed());
    }

    @Test
    void anonymousNullAndNonJwtPrincipalsCannotBootstrapAdministration() {
        when(identity.isAnonymous()).thenReturn(true);
        assertAuthenticationDenied(() -> policy().assertAllowed());
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(null);
        assertAuthenticationDenied(() -> policy().assertAllowed());
        when(identity.getPrincipal()).thenReturn((Principal) () -> SUBJECT);
        assertAuthenticationDenied(() -> policy().assertAllowed());
        assertAuthenticationDenied(() -> new RegistryPreflightPolicy(config, authorization, null).assertAllowed());
        assertAuthenticationDenied(() -> new RegistryPreflightPolicy(config, null, identity).assertAllowed());
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void malformedIssuerAndSubjectFailClosed(String invalid) {
        when(jwt.getIssuer()).thenReturn(invalid);
        assertAuthenticationDenied(() -> policy().assertAllowed());
        when(jwt.getIssuer()).thenReturn(ISSUER);
        when(jwt.getSubject()).thenReturn(invalid);
        assertAuthenticationDenied(() -> policy().assertAllowed());
    }

    @ParameterizedTest
    @MethodSource("invalidClients")
    void missingMalformedOrNonStringAzpCannotSelectTheRestClient(Object invalid) {
        when(jwt.getClaim("azp")).thenReturn(invalid);
        assertAuthenticationDenied(() -> policy().assertAllowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://another.example.invalid/realms/platform", "https://idp.example.invalid/realms/platform/", "https://IDP.example.invalid/realms/platform"})
    void issuerMatchingIsExactWithoutUrlNormalization(String otherIssuer) {
        when(jwt.getIssuer()).thenReturn(otherIssuer);
        assertDenied(() -> policy().assertAllowed());
    }

    @Test
    void noFirstLoginRoleOnlyClientOnlyOrTargetAdminFallbackExists() {
        when(jwt.getSubject()).thenReturn("synthetic-unlisted-first-login");
        when(identity.hasRole("admin")).thenReturn(true);
        when(identity.hasRole("realm-admin")).thenReturn(true);
        when(identity.hasRole("ops-administrator")).thenReturn(true);
        assertDenied(() -> policy().assertAllowed());
        when(jwt.getSubject()).thenReturn(SUBJECT);
        when(identity.hasRole(ROLE)).thenReturn(false);
        assertDenied(() -> policy().assertAllowed());
        when(identity.hasRole(ROLE)).thenReturn(true);
        when(jwt.getClaim("azp")).thenReturn("approved-mcp-but-not-console");
        assertDenied(() -> policy().assertAllowed());
        verify(authorization, org.mockito.Mockito.never()).grants();
    }

    @Test
    void differentEntriesCannotContributePartsOfOneAuthorization() {
        doReturn(Map.of(
                "subject-match", administrator(ISSUER, SUBJECT, "other-required-role", Set.of(CLIENT)),
                "role-match", administrator(ISSUER, "another-subject", ROLE, Set.of(CLIENT)),
                "client-mismatch", administrator(ISSUER, SUBJECT, ROLE, Set.of("other-console")),
                "issuer-mismatch", administrator("https://other.example.invalid", SUBJECT, ROLE, Set.of(CLIENT))))
                .when(config).administrators();
        assertDenied(() -> policy().assertAllowed());
    }

    @Test
    void anyOneCompleteEntryCanAuthorizeWithoutOrderDependence() {
        doReturn(Map.of(
                "unmatched", administrator(ISSUER, "other-subject", ROLE, Set.of(CLIENT)),
                "approved", administrator(ISSUER, SUBJECT, ROLE, Set.of("another-console", CLIENT))))
                .when(config).administrators();
        assertThatCode(() -> policy().assertAllowed()).doesNotThrowAnyException();
    }

    @Test
    void everyCallRechecksPrincipalClientRoleAndMode() {
        RegistryPreflightPolicy policy = policy();
        policy.assertAllowed();
        when(jwt.getSubject()).thenReturn("replacement-user");
        assertDenied(policy::assertAllowed);
        when(jwt.getSubject()).thenReturn(SUBJECT);
        when(jwt.getClaim("azp")).thenReturn("replacement-client");
        assertDenied(policy::assertAllowed);
        when(jwt.getClaim("azp")).thenReturn(CLIENT);
        when(identity.hasRole(ROLE)).thenReturn(false);
        assertDenied(policy::assertAllowed);
        when(identity.hasRole(ROLE)).thenReturn(true);
        when(authorization.mode()).thenReturn("local-lab");
        assertAuthenticationDenied(policy::assertAllowed);
    }

    @Test
    void configurationIsDefensivelyCopiedAndRequiresRestartForChanges() {
        Set<String> clients = new HashSet<>(Set.of(CLIENT));
        var administrator = administrator(ISSUER, SUBJECT, ROLE, clients);
        Map<String, RegistryAdministrationConfig.Administrator> entries = new HashMap<>();
        entries.put("operator-a", administrator);
        when(config.administrators()).thenReturn(entries);
        RegistryPreflightPolicy policy = policy();
        entries.clear();
        clients.clear();
        when(administrator.subject()).thenReturn("changed-config-subject");
        when(config.enabled()).thenReturn(false);
        assertThatCode(policy::assertAllowed).doesNotThrowAnyException();
        when(jwt.getSubject()).thenReturn("changed-config-subject");
        assertDenied(policy::assertAllowed);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Uppercase", "bad/handle", "*", "a________________________________________________________________"})
    void invalidEntryKeysAreRejectedWithFixedDiagnostics(String key) {
        Map<String, RegistryAdministrationConfig.Administrator> entries = new HashMap<>();
        entries.put(key, administrator(ISSUER, SUBJECT, ROLE, Set.of(CLIENT)));
        when(config.administrators()).thenReturn(entries);
        assertInvalidConfiguration();
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void invalidConfiguredIdentityFieldsNeverAppearInStartupDiagnostics(String invalid) {
        doReturn(Map.of("operator-a", administrator(invalid, SUBJECT, ROLE, Set.of(CLIENT))))
                .when(config).administrators();
        assertInvalidConfiguration();
        doReturn(Map.of("operator-a", administrator(ISSUER, invalid, ROLE, Set.of(CLIENT))))
                .when(config).administrators();
        assertInvalidConfiguration();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"*", "role with spaces", "role\n", "role/with/path"})
    void invalidConfiguredRolesAreRejected(String invalid) {
        doReturn(Map.of("operator-a", administrator(ISSUER, SUBJECT, invalid, Set.of(CLIENT))))
                .when(config).administrators();
        assertInvalidConfiguration();
    }

    @Test
    void nullEntryMapEntryAndClientSetFailClosedEvenWhenDisabled() {
        when(config.enabled()).thenReturn(false);
        when(config.administrators()).thenReturn(null);
        assertInvalidConfiguration();
        Map<String, RegistryAdministrationConfig.Administrator> entries = new HashMap<>();
        entries.put("operator-a", null);
        when(config.administrators()).thenReturn(entries);
        assertInvalidConfiguration();
        doReturn(Map.of("operator-a", administrator(ISSUER, SUBJECT, ROLE, null)))
                .when(config).administrators();
        assertInvalidConfiguration();
        assertThatThrownBy(() -> new RegistryPreflightPolicy(null, authorization, identity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid registry administration configuration").hasNoCause();
    }

    @ParameterizedTest
    @MethodSource("invalidConfiguredClients")
    void invalidConfiguredClientSetsAreRejected(Set<String> invalid) {
        doReturn(Map.of("operator-a", administrator(ISSUER, SUBJECT, ROLE, invalid)))
                .when(config).administrators();
        assertInvalidConfiguration();
    }

    @Test
    void limitsAcceptTwentyAdministratorsAndClientsButRejectTwentyOne() {
        Set<String> twentyClients = IntStream.range(0, 19).mapToObj(i -> "client-" + i).collect(Collectors.toSet());
        twentyClients.add(CLIENT);
        Map<String, RegistryAdministrationConfig.Administrator> entries = new HashMap<>();
        IntStream.range(0, 20).forEach(i -> entries.put("admin-" + i, administrator(ISSUER, SUBJECT, ROLE, twentyClients)));
        when(config.administrators()).thenReturn(entries);
        assertThatCode(() -> policy().assertAllowed()).doesNotThrowAnyException();
        entries.put("admin-extra", administrator(ISSUER, SUBJECT, ROLE, Set.of(CLIENT)));
        assertInvalidConfiguration();
        entries.remove("admin-extra");
        twentyClients.add("one-client-too-many");
        assertInvalidConfiguration();
    }

    @Test
    void acceptedLengthBoundariesMatchExactlyWithoutTrimming() {
        String issuer = "i".repeat(2048), subject = "s".repeat(2048), role = "r".repeat(128), client = "c".repeat(128);
        doReturn(Map.of("a".repeat(64), administrator(issuer, subject, role, Set.of(client))))
                .when(config).administrators();
        when(jwt.getIssuer()).thenReturn(issuer);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaim("azp")).thenReturn(client);
        when(identity.hasRole(role)).thenReturn(true);
        assertThatCode(() -> policy().assertAllowed()).doesNotThrowAnyException();
        doReturn(Map.of("a".repeat(65), administrator(issuer, subject, role, Set.of(client))))
                .when(config).administrators();
        assertInvalidConfiguration();
        doReturn(Map.of("operator-a", administrator(issuer, subject, "r".repeat(129), Set.of(client))))
                .when(config).administrators();
        assertInvalidConfiguration();
    }

    @Test
    void configurationAndIdentityExceptionsNeverRetainPrivateDiagnostics() {
        when(config.administrators()).thenThrow(new IllegalStateException(CANARY));
        assertInvalidConfiguration();
        doReturn(Map.of("operator-a", administrator(ISSUER, SUBJECT, ROLE, Set.of(CLIENT))))
                .when(config).administrators();
        when(jwt.getIssuer()).thenThrow(new IllegalStateException(CANARY));
        assertAuthenticationDenied(() -> policy().assertAllowed());
    }

    @Test
    void roleResolutionFailureReturnsSafeDenialWithoutCause() {
        when(identity.hasRole(ROLE)).thenThrow(new IllegalStateException(CANARY));
        assertDenied(() -> policy().assertAllowed());
    }

    @Test
    void malformedConfigurationGettersHaveSanitizedErrors() {
        var administrator = administrator(ISSUER, SUBJECT, ROLE, Set.of(CLIENT));
        when(administrator.subject()).thenThrow(new IllegalArgumentException(CANARY));
        when(config.administrators()).thenReturn(Map.of("operator-a", administrator));
        assertInvalidConfiguration();
    }

    private RegistryPreflightPolicy policy() {
        return new RegistryPreflightPolicy(config, authorization, identity);
    }

    private static RegistryAdministrationConfig.Administrator administrator(String issuer, String subject, String role, Set<String> clients) {
        var entry = mock(RegistryAdministrationConfig.Administrator.class);
        when(entry.issuer()).thenReturn(issuer);
        when(entry.subject()).thenReturn(subject);
        when(entry.role()).thenReturn(role);
        when(entry.restClientIds()).thenReturn(clients);
        return entry;
    }

    private void assertInvalidConfiguration() {
        assertThatThrownBy(this::policy).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid registry administration configuration").hasNoCause();
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertSafeError(action, ErrorCode.AUTHORIZATION_FAILED, "Registry preflight is not authorized");
    }

    private static void assertAuthenticationDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertSafeError(action, ErrorCode.AUTHENTICATION_FAILED,
                "Registry preflight requires an authenticated OIDC identity and client");
    }

    private static void assertSafeError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code, String message) {
        assertThatThrownBy(action).isInstanceOfSatisfying(McpException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(code);
            assertThat(exception).hasMessage(message).hasNoCause();
            assertThat(exception.toString()).doesNotContain(CANARY, SUBJECT, ISSUER);
        });
    }

    static Stream<String> invalidClaims() {
        return Stream.of(null, "", " ", " leading", "trailing ", "line\nbreak", "nul\u0000value", "*", "prefix*", "x".repeat(2049));
    }

    static Stream<Object> invalidClients() {
        return Stream.of(null, "", " ", "other client", "client\n", "*", "x".repeat(129), 17, Set.of(CLIENT), Map.of("client", CLIENT));
    }

    static Stream<Set<String>> invalidConfiguredClients() {
        Set<String> withNull = new HashSet<>();
        withNull.add(null);
        return Stream.of(Set.of(), Set.of("*"), Set.of("bad client"), Set.of("x".repeat(129)), withNull);
    }
}
