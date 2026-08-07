#!/usr/bin/env bash
# Bootstrap local Keycloak for keycloak-operations-mcp development.
# DEV ONLY: assigns realm-admin on mcp-demo for convenience.
# Production must use FGAP / least privilege — NOT realm-admin.
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
ADMIN_USER="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"
MCP_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-keycloak-mcp}"
MCP_CLIENT_SECRET="${MCP_CLIENT_SECRET:-${KEYCLOAK_CLIENT_SECRET:-change-me}}"
AUTH_REALM="${KEYCLOAK_AUTH_REALM:-master}"
TARGET_REALM="${MCP_TARGET_REALM:-mcp-demo}"
HEALTH_URL="${KEYCLOAK_HEALTH_URL:-http://localhost:9000/health/ready}"
MAX_WAIT="${KEYCLOAK_WAIT_SECONDS:-180}"

log() { printf '[setup-dev] %s\n' "$*"; }
die() { printf '[setup-dev] ERROR: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_cmd curl
require_cmd jq

log "Waiting for Keycloak health at ${HEALTH_URL} (timeout ${MAX_WAIT}s)..."
elapsed=0
until curl -sf "${HEALTH_URL}" >/dev/null 2>&1; do
  sleep 2
  elapsed=$((elapsed + 2))
  if [[ "${elapsed}" -ge "${MAX_WAIT}" ]]; then
    die "Keycloak did not become ready within ${MAX_WAIT}s"
  fi
done
log "Keycloak is ready."

get_admin_token() {
  curl -sf \
    -d "client_id=admin-cli" \
    -d "username=${ADMIN_USER}" \
    -d "password=${ADMIN_PASSWORD}" \
    -d "grant_type=password" \
    "${KEYCLOAK_URL}/realms/${AUTH_REALM}/protocol/openid-connect/token" \
    | jq -r '.access_token'
}

TOKEN="$(get_admin_token)"
[[ -n "${TOKEN}" && "${TOKEN}" != "null" ]] || die "failed to obtain admin token"

auth_hdr=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json")

# Ensure mcp-demo realm exists (imported via compose; recreate if missing)
if ! curl -sf "${auth_hdr[@]}" "${KEYCLOAK_URL}/admin/realms/${TARGET_REALM}" >/dev/null 2>&1; then
  log "Realm ${TARGET_REALM} not found — ensure compose import mounted mcp-demo-realm.json"
  die "missing realm ${TARGET_REALM}"
fi
log "Realm ${TARGET_REALM} is present."

EXISTING_CLIENT_ID="$(curl -sf "${auth_hdr[@]}" \
  "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients?clientId=${MCP_CLIENT_ID}" \
  | jq -r '.[0].id // empty')"

CLIENT_PAYLOAD="$(jq -n \
  --arg clientId "${MCP_CLIENT_ID}" \
  --arg secret "${MCP_CLIENT_SECRET}" \
  '{
    clientId: $clientId,
    name: "Keycloak Operations MCP",
    description: "Service account used by keycloak-operations-mcp (local demo)",
    enabled: true,
    protocol: "openid-connect",
    publicClient: false,
    secret: $secret,
    serviceAccountsEnabled: true,
    standardFlowEnabled: false,
    implicitFlowEnabled: false,
    directAccessGrantsEnabled: false,
    fullScopeAllowed: true
  }')"

if [[ -z "${EXISTING_CLIENT_ID}" ]]; then
  log "Creating client ${MCP_CLIENT_ID} in realm ${AUTH_REALM}..."
  curl -sf -X POST "${auth_hdr[@]}" \
    -d "${CLIENT_PAYLOAD}" \
    "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients" >/dev/null
  EXISTING_CLIENT_ID="$(curl -sf "${auth_hdr[@]}" \
    "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients?clientId=${MCP_CLIENT_ID}" \
    | jq -r '.[0].id')"
else
  log "Updating existing client ${MCP_CLIENT_ID} (${EXISTING_CLIENT_ID})..."
  curl -sf -X PUT "${auth_hdr[@]}" \
    -d "${CLIENT_PAYLOAD}" \
    "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients/${EXISTING_CLIENT_ID}" >/dev/null
fi

[[ -n "${EXISTING_CLIENT_ID}" && "${EXISTING_CLIENT_ID}" != "null" ]] \
  || die "unable to resolve internal id for ${MCP_CLIENT_ID}"

# Ensure the configured secret is applied (Keycloak may ignore create-time secret)
CLIENT_JSON="$(curl -sf "${auth_hdr[@]}" \
  "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients/${EXISTING_CLIENT_ID}")"
curl -sf -X PUT "${auth_hdr[@]}" \
  -d "$(jq --arg s "${MCP_CLIENT_SECRET}" \
    '.secret=$s | .serviceAccountsEnabled=true | .publicClient=false' <<<"${CLIENT_JSON}")" \
  "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients/${EXISTING_CLIENT_ID}" >/dev/null

SA_USER_ID="$(curl -sf "${auth_hdr[@]}" \
  "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients/${EXISTING_CLIENT_ID}/service-account-user" \
  | jq -r '.id')"
[[ -n "${SA_USER_ID}" && "${SA_USER_ID}" != "null" ]] || die "service account user not found"

assign_client_role() {
  local realm="$1"
  local client_uuid="$2"
  local role_name="$3"
  local role_json
  role_json="$(curl -sf "${auth_hdr[@]}" \
    "${KEYCLOAK_URL}/admin/realms/${realm}/clients/${client_uuid}/roles/${role_name}")" \
    || { log "WARN: role ${role_name} not found on client ${client_uuid}"; return 1; }
  curl -sf -X POST "${auth_hdr[@]}" \
    -d "[${role_json}]" \
    "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/users/${SA_USER_ID}/role-mappings/clients/${client_uuid}" \
    >/dev/null \
    || { log "WARN: failed to map role ${role_name}"; return 1; }
}

# In the master realm, admin clients are named "{realm}-realm" (not "realm-management").
# admin-cli is only used for password-grant bootstrap above.
MASTER_RM_ID="$(curl -sf "${auth_hdr[@]}" \
  "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients?clientId=master-realm" \
  | jq -r '.[0].id')"
[[ -n "${MASTER_RM_ID}" && "${MASTER_RM_ID}" != "null" ]] || die "master-realm client missing in master"

READ_ROLES=(view-realm view-clients view-users query-realms query-clients query-users query-groups)
# view-groups may be absent on some builds; assign best-effort
OPTIONAL_ROLES=(view-groups)

for role in "${READ_ROLES[@]}"; do
  log "Assigning master-realm/${role}..."
  assign_client_role "${AUTH_REALM}" "${MASTER_RM_ID}" "${role}"
done
for role in "${OPTIONAL_ROLES[@]}"; do
  log "Assigning master-realm/${role} (optional)..."
  assign_client_role "${AUTH_REALM}" "${MASTER_RM_ID}" "${role}" \
    || log "WARN: optional role master-realm/${role} not assigned"
done

# Permissions to administer/view the demo realm from a master service account
TARGET_RM_CLIENT_ID="${TARGET_REALM}-realm"
TARGET_RM_ID="$(curl -sf "${auth_hdr[@]}" \
  "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/clients?clientId=${TARGET_RM_CLIENT_ID}" \
  | jq -r '.[0].id // empty')"
[[ -n "${TARGET_RM_ID}" && "${TARGET_RM_ID}" != "null" ]] \
  || die "${TARGET_RM_CLIENT_ID} client missing in master (is realm ${TARGET_REALM} imported?)"

for role in "${READ_ROLES[@]}"; do
  log "Assigning ${TARGET_RM_CLIENT_ID}/${role}..."
  assign_client_role "${AUTH_REALM}" "${TARGET_RM_ID}" "${role}"
done
for role in "${OPTIONAL_ROLES[@]}"; do
  log "Assigning ${TARGET_RM_CLIENT_ID}/${role} (optional)..."
  assign_client_role "${AUTH_REALM}" "${TARGET_RM_ID}" "${role}" \
    || log "WARN: optional role ${TARGET_RM_CLIENT_ID}/${role} not assigned"
done

# DEV convenience: manage-realm on demo so local exploration is not blocked by missing views
log "Assigning DEV-ONLY ${TARGET_RM_CLIENT_ID}/manage-realm (prefer FGAP / least privilege in production)..."
assign_client_role "${AUTH_REALM}" "${TARGET_RM_ID}" "manage-realm" \
  || log "WARN: manage-realm not assigned; read roles may still be enough for 0.1.0 tools"

# Verify client_credentials
log "Verifying client_credentials for ${MCP_CLIENT_ID}..."
VERIFY="$(curl -sf \
  -d "client_id=${MCP_CLIENT_ID}" \
  -d "client_secret=${MCP_CLIENT_SECRET}" \
  -d "grant_type=client_credentials" \
  "${KEYCLOAK_URL}/realms/${AUTH_REALM}/protocol/openid-connect/token" \
  | jq -r '.access_token // empty')"
[[ -n "${VERIFY}" ]] || die "client_credentials grant failed for ${MCP_CLIENT_ID}"

# DEV ONLY: master realm-role "admin" unlocks /admin/serverinfo systemInfo (version).
# Without it, capabilities still work from feature flags, but version may be null.
# Production must NOT use the master admin role — use FGAP / least privilege.
if [[ "${MCP_GRANT_MASTER_ADMIN:-true}" == "true" ]]; then
  log "Assigning DEV-ONLY master realm role 'admin' (for serverInfo.version; set MCP_GRANT_MASTER_ADMIN=false to skip)..."
  ADMIN_REALM_ROLE="$(curl -sf "${auth_hdr[@]}" \
    "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/roles/admin" || true)"
  if [[ -n "${ADMIN_REALM_ROLE}" && "${ADMIN_REALM_ROLE}" != "null" ]]; then
    curl -sf -X POST "${auth_hdr[@]}" \
      -d "[${ADMIN_REALM_ROLE}]" \
      "${KEYCLOAK_URL}/admin/realms/${AUTH_REALM}/users/${SA_USER_ID}/role-mappings/realm" \
      >/dev/null \
      || log "WARN: could not assign master realm admin role"
  else
    log "WARN: master realm role 'admin' not found"
  fi
fi

cat <<EOF

[setup-dev] Complete.

Export these variables before starting the MCP server:

  export KEYCLOAK_URL=${KEYCLOAK_URL}
  export KEYCLOAK_AUTH_REALM=${AUTH_REALM}
  export KEYCLOAK_CLIENT_ID=${MCP_CLIENT_ID}
  export KEYCLOAK_CLIENT_SECRET=${MCP_CLIENT_SECRET}

Demo realm: ${TARGET_REALM}
Demo users: alice/alice (users), bob/bob (administrators)
Demo clients: portal-web (public), backend-api / backend-api-secret (confidential)

WARNING: This script grants broad admin privileges for local development only.
Production deployments MUST use Fine-Grained Admin Permissions (FGAP) or
least-privilege view/query roles — never the master realm admin role for MCP
service accounts. Note: /admin/serverinfo systemInfo (product version) is only
visible to master admins; without it, capability detection still works from
feature flags.

EOF
