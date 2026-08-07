package io.github.keycloakmcp.collector.keycloak;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.info.ServerInfoRepresentation;
import org.keycloak.representations.info.SystemInfoRepresentation;

import io.github.keycloakmcp.adapter.keycloak.KeycloakVersionDetector;
import io.github.keycloakmcp.adapter.keycloak.StableAdminApiAdapter;
import io.github.keycloakmcp.assessment.engine.Evidence;
import io.github.keycloakmcp.assessment.engine.EvidenceSubject;
import io.github.keycloakmcp.collector.EvidenceCollector;
import io.github.keycloakmcp.config.AssessmentConfig;
import io.github.keycloakmcp.domain.common.ServerInfo;
import io.github.keycloakmcp.target.Target;
import io.github.keycloakmcp.target.TargetType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Collects Keycloak Admin API evidence for assessments.
 * Uses {@link StableAdminApiAdapter} with the already-authorized {@link Target}.
 */
@ApplicationScoped
public class KeycloakEvidenceCollector implements EvidenceCollector {

    private static final Logger LOG = Logger.getLogger(KeycloakEvidenceCollector.class);
    private static final String MASTER = "master";
    private static final String PKCE_ATTR = "pkce.code.challenge.method";

    private final StableAdminApiAdapter adminApi;
    private final KeycloakVersionDetector versionDetector;
    private final AssessmentConfig assessmentConfig;

    @Inject
    public KeycloakEvidenceCollector(
            StableAdminApiAdapter adminApi,
            KeycloakVersionDetector versionDetector,
            AssessmentConfig assessmentConfig) {
        this.adminApi = adminApi;
        this.versionDetector = versionDetector;
        this.assessmentConfig = assessmentConfig;
    }

    @Override
    public String source() {
        return "keycloak";
    }

    @Override
    public List<Evidence> collect(Target target) {
        String targetId = target.id().value();
        Instant now = Instant.now();
        List<Evidence> evidence = new ArrayList<>();

        ServerInfoRepresentation serverInfo = adminApi.getServerInfo(target);
        SystemInfoRepresentation systemInfo = serverInfo.getSystemInfo();
        String rawVersion = systemInfo == null ? null : systemInfo.getVersion();
        String version = versionDetector.parseVersion(rawVersion).orElse(rawVersion);
        ServerInfo.Product product = productFromTarget(target.type());
        ServerInfo.Product detected = versionDetector.detectProduct(serverInfo);
        if (detected != ServerInfo.Product.UNKNOWN) {
            product = detected;
        }

        evidence.add(ev(targetId, "server", "keycloak.version", version, now, null));
        evidence.add(ev(targetId, "server", "keycloak.product", product.name(), now, null));

        List<RealmRepresentation> realms = adminApi.listRealms(target);
        if (realms == null) {
            realms = List.of();
        }
        evidence.add(ev(targetId, "realm", "keycloak.realm.count", realms.size(), now, null));

        int maxRealms = Math.max(0, assessmentConfig.maxRealms());
        int maxClients = Math.max(0, assessmentConfig.maxClientsPerRealm());
        boolean includeMasterInAggregates = assessmentConfig.includeMasterRealm();

        List<RealmRepresentation> bounded = realms.size() <= maxRealms
                ? realms
                : realms.subList(0, maxRealms);
        if (realms.size() > maxRealms) {
            LOG.warnf("Realm collection truncated to assessment.max-realms=%d for target=%s", maxRealms, targetId);
        }

        // Aggregate counters / lists over application realms (and optionally master)
        int bruteForceDisabledCount = 0;
        int sslNoneCount = 0;
        List<String> bruteForceDisabled = new ArrayList<>();
        boolean allBruteForceProtected = true;
        boolean anySslNone = false;
        boolean anyRegistrationAllowed = false;
        int applicationRealmCount = 0;

        int clientsTotal = 0;
        int clientsEnabled = 0;
        int wildcardRedirectCount = 0;
        int wildcardWebOriginsCount = 0;
        int implicitFlowCount = 0;
        int directAccessGrantsCount = 0;
        int publicWithoutPkceCount = 0;
        int localhostRedirectCount = 0;
        Set<String> wildcardRedirectClients = new LinkedHashSet<>();
        Set<String> wildcardWebOriginClients = new LinkedHashSet<>();
        Set<String> implicitFlowClients = new LinkedHashSet<>();
        Set<String> directAccessClients = new LinkedHashSet<>();
        Set<String> publicWithoutPkceClients = new LinkedHashSet<>();
        Set<String> localhostRedirectClients = new LinkedHashSet<>();

        for (RealmRepresentation brief : bounded) {
            if (brief == null || brief.getRealm() == null || brief.getRealm().isBlank()) {
                continue;
            }
            String realmName = brief.getRealm();
            EvidenceSubject realmSubject = EvidenceSubject.realm(realmName);

            RealmRepresentation realm;
            try {
                realm = adminApi.getRealm(target, realmName);
            } catch (RuntimeException e) {
                LOG.warnf(e, "Failed to load realm details for %s on target=%s", realmName, targetId);
                continue;
            }

            boolean isMaster = MASTER.equalsIgnoreCase(realmName);
            boolean inAggregates = !isMaster || includeMasterInAggregates;

            boolean enabled = Boolean.TRUE.equals(realm.isEnabled());
            boolean bruteForce = Boolean.TRUE.equals(realm.isBruteForceProtected());
            String sslRequired = realm.getSslRequired() == null ? "" : realm.getSslRequired();
            boolean registrationAllowed = Boolean.TRUE.equals(realm.isRegistrationAllowed());
            boolean verifyEmail = Boolean.TRUE.equals(realm.isVerifyEmail());
            boolean resetPassword = Boolean.TRUE.equals(realm.isResetPasswordAllowed());
            boolean rememberMe = Boolean.TRUE.equals(realm.isRememberMe());
            boolean loginWithEmail = Boolean.TRUE.equals(realm.isLoginWithEmailAllowed());
            boolean duplicateEmails = Boolean.TRUE.equals(realm.isDuplicateEmailsAllowed());
            boolean eventsEnabled = Boolean.TRUE.equals(realm.isEventsEnabled());
            boolean adminEventsEnabled = Boolean.TRUE.equals(realm.isAdminEventsEnabled());

            // Per-realm evidence (including master) for admin packs
            evidence.add(ev(targetId, "realm", "realm.enabled", enabled, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.sslRequired", sslRequired, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.registrationAllowed", registrationAllowed, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.bruteForceProtected", bruteForce, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.verifyEmail", verifyEmail, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.resetPasswordAllowed", resetPassword, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.rememberMe", rememberMe, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.loginWithEmailAllowed", loginWithEmail, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.duplicateEmailsAllowed", duplicateEmails, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.passwordPolicy", realm.getPasswordPolicy(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.otpPolicyType", realm.getOtpPolicyType(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.eventsEnabled", eventsEnabled, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.adminEventsEnabled", adminEventsEnabled, now, realmSubject));

            if (inAggregates) {
                applicationRealmCount++;
                if (!bruteForce) {
                    allBruteForceProtected = false;
                    bruteForceDisabledCount++;
                    bruteForceDisabled.add(realmName);
                }
                if ("none".equalsIgnoreCase(sslRequired.trim())) {
                    anySslNone = true;
                    sslNoneCount++;
                }
                if (registrationAllowed) {
                    anyRegistrationAllowed = true;
                }
            }

            // Clients
            List<ClientRepresentation> clients;
            try {
                clients = adminApi.listClients(target, realmName, false);
            } catch (RuntimeException e) {
                LOG.warnf(e, "Failed to list clients for realm=%s target=%s", realmName, targetId);
                continue;
            }
            if (clients == null) {
                clients = List.of();
            }
            if (clients.size() > maxClients) {
                LOG.warnf(
                        "Client collection truncated to assessment.max-clients-per-realm=%d for realm=%s target=%s",
                        maxClients,
                        realmName,
                        targetId);
                clients = clients.subList(0, maxClients);
            }

            for (ClientRepresentation client : clients) {
                if (client == null) {
                    continue;
                }
                String clientId = client.getClientId() == null ? client.getId() : client.getClientId();
                clientsTotal++;
                if (Boolean.TRUE.equals(client.isEnabled())) {
                    clientsEnabled++;
                }

                List<String> redirectUris = client.getRedirectUris() == null ? List.of() : client.getRedirectUris();
                List<String> webOrigins = client.getWebOrigins() == null ? List.of() : client.getWebOrigins();

                boolean hasWildcardRedirect = false;
                boolean hasLocalhostRedirect = false;
                for (String uri : redirectUris) {
                    if (isWildcardUri(uri)) {
                        hasWildcardRedirect = true;
                    }
                    if (isLocalhostUri(uri)) {
                        hasLocalhostRedirect = true;
                    }
                }
                if (hasWildcardRedirect) {
                    wildcardRedirectCount++;
                    if (clientId != null) {
                        wildcardRedirectClients.add(clientId);
                    }
                }
                if (hasLocalhostRedirect) {
                    localhostRedirectCount++;
                    if (clientId != null) {
                        localhostRedirectClients.add(clientId);
                    }
                }

                boolean hasWildcardOrigin = false;
                for (String origin : webOrigins) {
                    if (isWildcardUri(origin) || "*".equals(origin)) {
                        hasWildcardOrigin = true;
                        break;
                    }
                }
                if (hasWildcardOrigin) {
                    wildcardWebOriginsCount++;
                    if (clientId != null) {
                        wildcardWebOriginClients.add(clientId);
                    }
                }

                if (Boolean.TRUE.equals(client.isImplicitFlowEnabled())) {
                    implicitFlowCount++;
                    if (clientId != null) {
                        implicitFlowClients.add(clientId);
                    }
                }
                if (Boolean.TRUE.equals(client.isDirectAccessGrantsEnabled())) {
                    directAccessGrantsCount++;
                    if (clientId != null) {
                        directAccessClients.add(clientId);
                    }
                }

                String pkce = null;
                Map<String, String> attrs = client.getAttributes();
                if (attrs != null) {
                    pkce = attrs.get(PKCE_ATTR);
                }
                boolean publicClient = Boolean.TRUE.equals(client.isPublicClient());
                boolean hasPkceS256 = pkce != null && "S256".equalsIgnoreCase(pkce.trim());
                if (publicClient && !hasPkceS256) {
                    publicWithoutPkceCount++;
                    if (clientId != null) {
                        publicWithoutPkceClients.add(clientId);
                    }
                }
            }
        }

        // Target-level aggregate booleans for YAML rules (EvidenceContext.find returns first match)
        if (applicationRealmCount == 0) {
            // No application realms: treat aggregates as satisfied / non-triggering defaults
            evidence.add(ev(targetId, "realm", "realm.bruteForceProtected", true, now, null));
            evidence.add(ev(targetId, "realm", "realm.sslRequired", "external", now, null));
            evidence.add(ev(targetId, "realm", "realm.registrationAllowed", false, now, null));
        } else {
            evidence.add(ev(targetId, "realm", "realm.bruteForceProtected", allBruteForceProtected, now, null));
            evidence.add(ev(
                    targetId,
                    "realm",
                    "realm.sslRequired",
                    anySslNone ? "none" : "external",
                    now,
                    null));
            evidence.add(ev(targetId, "realm", "realm.registrationAllowed", anyRegistrationAllowed, now, null));
        }

        evidence.add(ev(
                targetId, "realm", "keycloak.realms.bruteForceProtected.disabledCount", bruteForceDisabledCount, now, null));
        evidence.add(ev(targetId, "realm", "keycloak.realms.sslRequired.noneCount", sslNoneCount, now, null));
        evidence.add(ev(targetId, "realm", "keycloak.realms.bruteForceDisabled", List.copyOf(bruteForceDisabled), now, null));

        evidence.add(ev(targetId, "client", "keycloak.clients.total", clientsTotal, now, null));
        evidence.add(ev(targetId, "client", "keycloak.clients.enabled", clientsEnabled, now, null));
        evidence.add(ev(targetId, "client", "keycloak.clients.wildcardRedirectUri", wildcardRedirectCount, now, null));
        evidence.add(ev(
                targetId,
                "client",
                "keycloak.clients.wildcardRedirectUri.clientIds",
                List.copyOf(wildcardRedirectClients),
                now,
                null));
        evidence.add(ev(targetId, "client", "keycloak.clients.wildcardWebOrigins", wildcardWebOriginsCount, now, null));
        evidence.add(ev(
                targetId,
                "client",
                "keycloak.clients.wildcardWebOrigins.clientIds",
                List.copyOf(wildcardWebOriginClients),
                now,
                null));
        evidence.add(ev(targetId, "client", "keycloak.clients.implicitFlowEnabled", implicitFlowCount, now, null));
        evidence.add(ev(
                targetId,
                "client",
                "keycloak.clients.implicitFlowEnabled.clientIds",
                List.copyOf(implicitFlowClients),
                now,
                null));
        evidence.add(ev(
                targetId, "client", "keycloak.clients.directAccessGrantsEnabled", directAccessGrantsCount, now, null));
        evidence.add(ev(
                targetId,
                "client",
                "keycloak.clients.directAccessGrantsEnabled.clientIds",
                List.copyOf(directAccessClients),
                now,
                null));
        evidence.add(ev(
                targetId, "client", "keycloak.clients.publicWithoutPkceS256", publicWithoutPkceCount, now, null));
        evidence.add(ev(
                targetId,
                "client",
                "keycloak.clients.publicWithoutPkceS256.clientIds",
                List.copyOf(publicWithoutPkceClients),
                now,
                null));
        evidence.add(ev(targetId, "client", "keycloak.clients.localhostRedirectUri", localhostRedirectCount, now, null));
        evidence.add(ev(
                targetId,
                "client",
                "keycloak.clients.localhostRedirectUri.clientIds",
                List.copyOf(localhostRedirectClients),
                now,
                null));

        return List.copyOf(evidence);
    }

    private Evidence ev(
            String targetId,
            String category,
            String key,
            Object value,
            Instant now,
            EvidenceSubject subject) {
        return new Evidence(targetId, source(), category, key, value, now, subject);
    }

    /**
     * Wildcard if URI is exactly "*" or the host component contains "*".
     * Path wildcards (e.g. http://localhost:8080/*) are not host wildcards.
     */
    static boolean isWildcardUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String trimmed = uri.trim();
        if ("*".equals(trimmed)) {
            return true;
        }
        String host = extractHost(trimmed);
        return host != null && host.contains("*");
    }

    static boolean isLocalhostUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        String host = extractHost(uri.trim());
        if (host == null || host.isBlank()) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(lower)
                || "127.0.0.1".equals(lower)
                || "[::1]".equals(lower)
                || "::1".equals(lower);
    }

    private static String extractHost(String uri) {
        int schemeEnd = uri.indexOf("://");
        String remainder = schemeEnd >= 0 ? uri.substring(schemeEnd + 3) : uri;
        int pathStart = remainder.indexOf('/');
        String hostPort = pathStart < 0 ? remainder : remainder.substring(0, pathStart);
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            return close > 0 ? hostPort.substring(0, close + 1) : hostPort;
        }
        int colon = hostPort.indexOf(':');
        return colon < 0 ? hostPort : hostPort.substring(0, colon);
    }

    private static ServerInfo.Product productFromTarget(TargetType type) {
        if (type == null) {
            return ServerInfo.Product.UNKNOWN;
        }
        return switch (type) {
            case RHBK -> ServerInfo.Product.RHBK;
            case KEYCLOAK -> ServerInfo.Product.KEYCLOAK;
        };
    }
}
