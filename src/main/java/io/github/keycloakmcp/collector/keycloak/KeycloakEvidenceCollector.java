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
import io.github.keycloakmcp.domain.error.ErrorCode;
import io.github.keycloakmcp.domain.error.McpException;
import io.github.keycloakmcp.target.Target;
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
        List<Map<String, String>> issues = new ArrayList<>();

        ServerInfoRepresentation serverInfo = null;
        try {
            serverInfo = adminApi.getServerInfo(target);
            if (serverInfo == null) {
                issues.add(issue("INVALID_RESPONSE", "server-info"));
            }
        } catch (RuntimeException e) {
            // Server metadata may require privileges that realm/client reads do not.
            // Do not escalate or discard permitted resource evidence merely to observe a version.
            issues.add(issue(failureCode(e), "server-info"));
            LOG.warnf("Server metadata unavailable for target=%s; continuing permitted resource collection", targetId);
        }
        SystemInfoRepresentation systemInfo = serverInfo == null ? null : serverInfo.getSystemInfo();
        String rawVersion = systemInfo == null ? null : systemInfo.getVersion();
        String version = rawVersion == null || rawVersion.isBlank()
                ? null : versionDetector.parseVersion(rawVersion).orElse(null);
        if (serverInfo != null && version == null) {
            issues.add(issue("MISSING_FIELDS", "server-info.version"));
        }
        ServerInfo.Product product = versionDetector.detectProduct(serverInfo);

        evidence.add(ev(targetId, "server", "keycloak.version", version, now, null));
        if (rawVersion != null && !rawVersion.isBlank()) {
            evidence.add(ev(targetId, "server", "keycloak.version.raw", rawVersion, now, null));
        }
        evidence.add(ev(targetId, "server", "keycloak.product",
                product == ServerInfo.Product.UNKNOWN ? null : product.name(), now, null));
        evidence.add(ev(targetId, "server", "keycloak.product.configured", target.type().name(), now, null));

        List<RealmRepresentation> realms = adminApi.listRealms(target);
        if (realms == null) {
            issues.add(issue("INVALID_RESPONSE", "realms"));
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
            issues.add(issue("TRUNCATED", "realms"));
            LOG.warnf("Realm collection truncated to assessment.max-realms=%d for target=%s", maxRealms, targetId);
        }

        // Aggregate counters / lists over application realms (and optionally master)
        int bruteForceDisabledCount = 0;
        int sslNoneCount = 0;
        List<String> bruteForceDisabled = new ArrayList<>();
        int realmsCollected = 0;
        int clientsObserved = 0;

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
                issues.add(issue("INVALID_RESPONSE", "realms"));
                continue;
            }
            String realmName = brief.getRealm();
            EvidenceSubject realmSubject = EvidenceSubject.realm(realmName);
            boolean isMaster = MASTER.equalsIgnoreCase(realmName);
            boolean inAggregates = !isMaster || includeMasterInAggregates;
            evidence.add(ev(targetId, "realm", "realm.name", realmName, now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.assessmentIncluded", inAggregates, now, realmSubject));

            RealmRepresentation realm;
            try {
                realm = adminApi.getRealm(target, realmName);
            } catch (RuntimeException e) {
                issues.add(issue(failureCode(e), "realm:" + realmName));
                LOG.warnf("Failed to load realm details for %s on target=%s", realmName, targetId);
                continue;
            }
            if (realm == null) {
                issues.add(issue("INVALID_RESPONSE", "realm:" + realmName));
                continue;
            }
            realmsCollected++;

            String sslRequired = realm.getSslRequired() == null ? "" : realm.getSslRequired();
            if (realm.isBruteForceProtected() == null || realm.getSslRequired() == null
                    || realm.isRegistrationAllowed() == null) {
                issues.add(issue("MISSING_FIELDS", "realm:" + realmName));
            }

            // Per-realm evidence (including master) for admin packs
            evidence.add(ev(targetId, "realm", "realm.enabled", realm.isEnabled(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.sslRequired", realm.getSslRequired(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.registrationAllowed", realm.isRegistrationAllowed(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.bruteForceProtected", realm.isBruteForceProtected(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.verifyEmail", realm.isVerifyEmail(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.resetPasswordAllowed", realm.isResetPasswordAllowed(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.rememberMe", realm.isRememberMe(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.loginWithEmailAllowed", realm.isLoginWithEmailAllowed(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.duplicateEmailsAllowed", realm.isDuplicateEmailsAllowed(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.passwordPolicy", realm.getPasswordPolicy(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.otpPolicyType", realm.getOtpPolicyType(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.eventsEnabled", realm.isEventsEnabled(), now, realmSubject));
            evidence.add(ev(targetId, "realm", "realm.adminEventsEnabled", realm.isAdminEventsEnabled(), now, realmSubject));
            if (isMaster) {
                evidence.add(ev(targetId, "realm", "realm.master.registrationAllowed", realm.isRegistrationAllowed(), now, realmSubject));
                evidence.add(ev(targetId, "realm", "realm.master.sslRequired", realm.getSslRequired(), now, realmSubject));
            }

            if (inAggregates) {
                if (Boolean.FALSE.equals(realm.isBruteForceProtected())) {
                    bruteForceDisabledCount++;
                    bruteForceDisabled.add(realmName);
                }
                if ("none".equalsIgnoreCase(sslRequired.trim())) {
                    sslNoneCount++;
                }
            }

            // Clients
            List<ClientRepresentation> clients;
            try {
                clients = adminApi.listClients(target, realmName, false);
            } catch (RuntimeException e) {
                issues.add(issue(failureCode(e), "clients:" + realmName));
                LOG.warnf("Failed to list clients for realm=%s target=%s", realmName, targetId);
                continue;
            }
            if (clients == null) {
                issues.add(issue("INVALID_RESPONSE", "clients:" + realmName));
                clients = List.of();
            }
            clientsObserved += clients.size();
            if (clients.size() > maxClients) {
                issues.add(issue("TRUNCATED", "clients:" + realmName));
                LOG.warnf(
                        "Client collection truncated to assessment.max-clients-per-realm=%d for realm=%s target=%s",
                        maxClients,
                        realmName,
                        targetId);
                clients = clients.subList(0, maxClients);
            }

            for (ClientRepresentation client : clients) {
                if (client == null) {
                    issues.add(issue("INVALID_RESPONSE", "clients:" + realmName));
                    continue;
                }
                String clientId = client.getClientId() == null ? client.getId() : client.getClientId();
                // Client IDs are unique only inside a realm. Do not collapse affected resources.
                String clientRef = clientId == null ? null : realmName + "/" + clientId;
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
                        wildcardRedirectClients.add(clientRef);
                    }
                }
                if (hasLocalhostRedirect) {
                    localhostRedirectCount++;
                    if (clientId != null) {
                        localhostRedirectClients.add(clientRef);
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
                        wildcardWebOriginClients.add(clientRef);
                    }
                }

                if (Boolean.TRUE.equals(client.isImplicitFlowEnabled())) {
                    implicitFlowCount++;
                    if (clientId != null) {
                        implicitFlowClients.add(clientRef);
                    }
                }
                if (Boolean.TRUE.equals(client.isDirectAccessGrantsEnabled())) {
                    directAccessGrantsCount++;
                    if (clientId != null) {
                        directAccessClients.add(clientRef);
                    }
                }

                String pkce = null;
                Map<String, String> attrs = client.getAttributes();
                if (attrs != null) {
                    pkce = attrs.get(PKCE_ATTR);
                }
                boolean publicClient = Boolean.TRUE.equals(client.isPublicClient());
                boolean hasPkceS256 = pkce != null && "S256".equalsIgnoreCase(pkce.trim());
                if (client.isImplicitFlowEnabled() == null || client.isDirectAccessGrantsEnabled() == null
                        || client.isEnabled() == null || (Boolean.TRUE.equals(client.isEnabled())
                        && (client.getProtocol() == null || ("openid-connect".equals(client.getProtocol())
                        && (client.isStandardFlowEnabled() == null || (Boolean.TRUE.equals(client.isStandardFlowEnabled())
                        && client.isPublicClient() == null)))))) {
                    issues.add(issue("MISSING_FIELDS", "clients:" + realmName));
                }
                boolean pkceApplicable = Boolean.TRUE.equals(client.isEnabled())
                        && "openid-connect".equals(client.getProtocol())
                        && Boolean.TRUE.equals(client.isStandardFlowEnabled()) && publicClient;
                if (pkceApplicable && !hasPkceS256) {
                    publicWithoutPkceCount++;
                    if (clientId != null) {
                        publicWithoutPkceClients.add(clientRef);
                    }
                }
            }
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

        if (issues.stream().anyMatch(i -> !i.get("scope").startsWith("server-info"))) {
            // These counters require exhaustive collection. Zero after an incomplete read is
            // unknown, not PASS. Positive observed findings remain useful lower bounds.
            // Missing server metadata alone does not invalidate complete realm/client reads.
            evidence.removeIf(e -> e.subject() == null && e.value() instanceof Number n && n.intValue() == 0
                    && (e.key().startsWith("keycloak.clients.") || e.key().startsWith("keycloak.realms.")));
        }
        evidence.add(ev(targetId, "collection", "keycloak.collection.complete", issues.isEmpty(), now, null));
        evidence.add(ev(targetId, "collection", "keycloak.collection.issues", List.copyOf(issues), now, null));
        evidence.add(ev(targetId, "collection", "keycloak.collection.realmsDiscovered", realms.size(), now, null));
        evidence.add(ev(targetId, "collection", "keycloak.collection.realmsCollected", realmsCollected, now, null));
        evidence.add(ev(targetId, "collection", "keycloak.collection.clientsObserved", clientsObserved, now, null));
        evidence.add(ev(targetId, "collection", "keycloak.collection.clientsInspected", clientsTotal, now, null));

        return List.copyOf(evidence);
    }

    private static Map<String, String> issue(String code, String scope) {
        return Map.of("code", code, "scope", scope);
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof McpException m && (m.getCode() == ErrorCode.AUTHORIZATION_FAILED
                || m.getCode() == ErrorCode.AUTHENTICATION_FAILED)) {
            return "UNAUTHORIZED";
        }
        if (failure instanceof jakarta.ws.rs.WebApplicationException http
                && (http.getResponse().getStatus() == 401 || http.getResponse().getStatus() == 403)) {
            return "UNAUTHORIZED";
        }
        return "UNAVAILABLE";
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

}
