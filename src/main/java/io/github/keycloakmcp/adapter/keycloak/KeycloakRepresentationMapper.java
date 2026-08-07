package io.github.keycloakmcp.adapter.keycloak;

import java.util.Collections;
import java.util.List;

import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import io.github.keycloakmcp.domain.client.ClientDetails;
import io.github.keycloakmcp.domain.client.ClientSummary;
import io.github.keycloakmcp.domain.group.GroupDetails;
import io.github.keycloakmcp.domain.group.GroupSummary;
import io.github.keycloakmcp.domain.realm.RealmDetails;
import io.github.keycloakmcp.domain.realm.RealmSummary;
import io.github.keycloakmcp.domain.role.RoleDetails;
import io.github.keycloakmcp.domain.role.RoleSummary;
import io.github.keycloakmcp.domain.user.UserDetails;
import io.github.keycloakmcp.domain.user.UserSummary;

public final class KeycloakRepresentationMapper {

    private KeycloakRepresentationMapper() {
    }

    public static RealmSummary toRealmSummary(RealmRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new RealmSummary(
                representation.getRealm(),
                representation.getDisplayName(),
                Boolean.TRUE.equals(representation.isEnabled()));
    }

    public static RealmDetails toRealmDetails(RealmRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new RealmDetails(
                representation.getRealm(),
                representation.getDisplayName(),
                Boolean.TRUE.equals(representation.isEnabled()),
                Boolean.TRUE.equals(representation.isRegistrationAllowed()),
                Boolean.TRUE.equals(representation.isResetPasswordAllowed()),
                Boolean.TRUE.equals(representation.isEditUsernameAllowed()),
                Boolean.TRUE.equals(representation.isBruteForceProtected()),
                representation.getSslRequired(),
                Boolean.TRUE.equals(representation.isLoginWithEmailAllowed()),
                Boolean.TRUE.equals(representation.isDuplicateEmailsAllowed()),
                Boolean.TRUE.equals(representation.isInternationalizationEnabled()));
    }

    public static ClientSummary toClientSummary(ClientRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new ClientSummary(
                representation.getId(),
                representation.getClientId(),
                representation.getName(),
                Boolean.TRUE.equals(representation.isEnabled()),
                Boolean.TRUE.equals(representation.isPublicClient()),
                Boolean.TRUE.equals(representation.isServiceAccountsEnabled()),
                Boolean.TRUE.equals(representation.isStandardFlowEnabled()),
                Boolean.TRUE.equals(representation.isDirectAccessGrantsEnabled()));
    }

    /**
     * Maps client representation to details. Never maps secrets or credentials.
     */
    public static ClientDetails toClientDetails(ClientRepresentation representation) {
        if (representation == null) {
            return null;
        }
        // confidentialPort was removed from Keycloak 26 ClientRepresentation; keep DTO field as 0.
        return new ClientDetails(
                representation.getId(),
                representation.getClientId(),
                representation.getName(),
                Boolean.TRUE.equals(representation.isEnabled()),
                Boolean.TRUE.equals(representation.isPublicClient()),
                Boolean.TRUE.equals(representation.isServiceAccountsEnabled()),
                Boolean.TRUE.equals(representation.isStandardFlowEnabled()),
                Boolean.TRUE.equals(representation.isDirectAccessGrantsEnabled()),
                representation.getProtocol(),
                representation.getRootUrl(),
                representation.getBaseUrl(),
                copyList(representation.getRedirectUris()),
                copyList(representation.getWebOrigins()),
                Boolean.TRUE.equals(representation.isBearerOnly()),
                0,
                Boolean.TRUE.equals(representation.isFullScopeAllowed()),
                representation.getDescription());
    }

    public static UserSummary toUserSummary(UserRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new UserSummary(
                representation.getId(),
                representation.getUsername(),
                representation.getFirstName(),
                representation.getLastName(),
                representation.getEmail(),
                Boolean.TRUE.equals(representation.isEnabled()));
    }

    public static UserDetails toUserDetails(UserRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new UserDetails(
                representation.getId(),
                representation.getUsername(),
                representation.getFirstName(),
                representation.getLastName(),
                representation.getEmail(),
                Boolean.TRUE.equals(representation.isEnabled()),
                Boolean.TRUE.equals(representation.isEmailVerified()),
                representation.getFederationLink(),
                copyList(representation.getRequiredActions()),
                representation.getCreatedTimestamp());
    }

    public static GroupSummary toGroupSummary(GroupRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new GroupSummary(
                representation.getId(),
                representation.getName(),
                representation.getPath());
    }

    public static GroupDetails toGroupDetails(GroupRepresentation representation) {
        if (representation == null) {
            return null;
        }
        Long subGroupCount = representation.getSubGroupCount();
        if (subGroupCount == null && representation.getSubGroups() != null) {
            subGroupCount = (long) representation.getSubGroups().size();
        }
        return new GroupDetails(
                representation.getId(),
                representation.getName(),
                representation.getPath(),
                subGroupCount);
    }

    public static RoleSummary toRoleSummary(RoleRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new RoleSummary(
                representation.getId(),
                representation.getName(),
                representation.getDescription(),
                Boolean.TRUE.equals(representation.isComposite()),
                Boolean.TRUE.equals(representation.getClientRole()),
                representation.getContainerId());
    }

    public static RoleDetails toRoleDetails(RoleRepresentation representation) {
        if (representation == null) {
            return null;
        }
        return new RoleDetails(
                representation.getId(),
                representation.getName(),
                representation.getDescription(),
                Boolean.TRUE.equals(representation.isComposite()),
                Boolean.TRUE.equals(representation.getClientRole()),
                representation.getContainerId());
    }

    private static List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(List.copyOf(values));
    }
}
