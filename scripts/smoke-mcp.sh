#!/usr/bin/env bash
# HTTP smoke test against Quarkiverse MCP Streamable HTTP endpoint (/mcp).
set -euo pipefail

MCP_URL="${MCP_URL:-http://localhost:8081/mcp}"
PROTOCOL_VERSION="${MCP_PROTOCOL_VERSION:-2025-11-25}"
TARGET_ID="${TARGET_ID:-lab-keycloak-a}"
TARGET_ID_B="${TARGET_ID_B:-lab-keycloak-b}"
ACCEPT_HDR="application/json, text/event-stream"

log() { printf '[smoke-mcp] %s\n' "$*"; }
die() { printf '[smoke-mcp] ERROR: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_cmd curl
require_cmd jq

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

SESSION_ID=""
REQUEST_ID=0
LAST_MCP_PAYLOAD=""

# Parse JSON body from either raw JSON or SSE "data:" lines.
extract_json_payload() {
  local file="$1"
  if jq -e . "${file}" >/dev/null 2>&1; then
    cat "${file}"
    return 0
  fi
  local data
  data="$(awk '
    BEGIN { body="" }
    /^data:/{
      sub(/^data:[[:space:]]*/, "")
      if (body != "") body = body "\n"
      body = body $0
    }
    END { print body }
  ' "${file}")"
  if [[ -n "${data}" ]] && jq -e . >/dev/null 2>&1 <<<"${data}"; then
    printf '%s' "${data}"
    return 0
  fi
  if grep -o '{.*}' "${file}" | head -1 | jq -e . >/dev/null 2>&1; then
    grep -o '{.*}' "${file}" | head -1
    return 0
  fi
  die "unable to parse MCP response as JSON/SSE from ${file}"
}

# Important: do not invoke this inside $() — SESSION_ID must persist in the parent shell.
mcp_call() {
  local method="$1"
  local params_json
  if [[ $# -ge 2 ]]; then
    params_json="$2"
  else
    params_json='{}'
  fi
  # Increment in-place (avoid $() subshell which would lose REQUEST_ID updates)
  REQUEST_ID=$((REQUEST_ID + 1))
  local id="${REQUEST_ID}"
  local body
  body="$(jq -n \
    --arg id "${id}" \
    --arg method "${method}" \
    --argjson params "${params_json}" \
    '{jsonrpc:"2.0", id:($id|tonumber), method:$method, params:$params}')"

  local hdr_file="${TMP_DIR}/headers-${id}.txt"
  local body_file="${TMP_DIR}/body-${id}.txt"
  local curl_args=(
    -sS
    -D "${hdr_file}"
    -o "${body_file}"
    -X POST "${MCP_URL}"
    -H "Content-Type: application/json"
    -H "Accept: ${ACCEPT_HDR}"
  )
  if [[ -n "${SESSION_ID}" ]]; then
    curl_args+=(-H "Mcp-Session-Id: ${SESSION_ID}")
  fi

  curl "${curl_args[@]}" -d "${body}" || die "curl failed for method ${method}"

  local new_session=""
  new_session="$(grep -i '^mcp-session-id:' "${hdr_file}" 2>/dev/null | tail -1 | cut -d: -f2- | tr -d ' \r' || true)"
  if [[ -n "${new_session}" ]]; then
    SESSION_ID="${new_session}"
    log "Session id: ${SESSION_ID}"
  fi

  LAST_MCP_PAYLOAD="$(extract_json_payload "${body_file}")" || die "failed to parse payload for ${method}"
  if [[ -z "${LAST_MCP_PAYLOAD}" ]]; then
    die "empty MCP payload for ${method}"
  fi
  if jq -e 'has("error") and .error != null' >/dev/null 2>&1 <<<"${LAST_MCP_PAYLOAD}"; then
    printf '%s\n' "${LAST_MCP_PAYLOAD}" | jq .
    die "MCP error on ${method}"
  fi
}

log "Target: ${MCP_URL}"

INIT_PARAMS="$(jq -n \
  --arg pv "${PROTOCOL_VERSION}" \
  '{
    protocolVersion: $pv,
    capabilities: {},
    clientInfo: {name: "smoke-mcp", version: "0.1.0"}
  }')"

log "initialize..."
mcp_call "initialize" "${INIT_PARAMS}"
INIT_RESP="${LAST_MCP_PAYLOAD}"
printf '%s\n' "${INIT_RESP}" | jq '{id, result: {protocolVersion: .result.protocolVersion, serverInfo: .result.serverInfo}}'

# notifications/initialized (JSON-RPC notification — no id)
log "notifications/initialized..."
NOTIFY_BODY='{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}'
NOTIFY_ARGS=(-sS -o /dev/null -w "%{http_code}" -X POST "${MCP_URL}"
  -H "Content-Type: application/json"
  -H "Accept: ${ACCEPT_HDR}"
  -d "${NOTIFY_BODY}")
if [[ -n "${SESSION_ID}" ]]; then
  NOTIFY_ARGS+=(-H "Mcp-Session-Id: ${SESSION_ID}")
fi
NOTIFY_CODE="$(curl "${NOTIFY_ARGS[@]}")"
[[ "${NOTIFY_CODE}" =~ ^(200|202|204)$ ]] || log "WARN: notifications/initialized HTTP ${NOTIFY_CODE}"

log "tools/list..."
mcp_call "tools/list" "{}"
TOOLS_RESP="${LAST_MCP_PAYLOAD}"
TOOL_COUNT="$(jq '.result.tools | length' <<<"${TOOLS_RESP}")"
log "tools listed: ${TOOL_COUNT}"
printf '%s\n' "${TOOLS_RESP}" | jq -r '.result.tools[].name' | sort

call_tool() {
  local name="$1"
  local arguments_json
  if [[ $# -ge 2 ]]; then
    arguments_json="$2"
  else
    arguments_json='{}'
  fi
  local params
  params="$(jq -n --arg name "${name}" --argjson args "${arguments_json}" \
    '{name:$name, arguments:$args}')"
  log "tools/call ${name}..."
  mcp_call "tools/call" "${params}"
  local resp="${LAST_MCP_PAYLOAD}"
  if jq -e '.result.structuredContent != null' >/dev/null 2>&1 <<<"${resp}"; then
    printf '%s\n' "${resp}" | jq '.result.structuredContent'
  elif jq -e '.result.content != null' >/dev/null 2>&1 <<<"${resp}"; then
    printf '%s\n' "${resp}" | jq '.result.content'
  else
    printf '%s\n' "${resp}" | jq .
  fi
  if jq -e '.result.isError == true' >/dev/null 2>&1 <<<"${resp}"; then
    die "tool ${name} returned isError=true"
  fi
}

call_tool "keycloak_list_targets" "{}"
call_tool "keycloak_get_target" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t}')"
call_tool "keycloak_server_info" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t}')"
call_tool "keycloak_list_realms" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t}')"
call_tool "keycloak_get_realm" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo"}')"
call_tool "keycloak_list_clients" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo"}')"
call_tool "keycloak_get_client" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo", clientId:"portal-web"}')"
call_tool "keycloak_search_users" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo", search:"alice"}')"
call_tool "keycloak_get_user" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo", userId:"mcp-demo-user-alice"}')"
call_tool "keycloak_list_groups" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo"}')"
call_tool "keycloak_get_group" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo", groupId:"mcp-demo-group-users"}')"
call_tool "keycloak_list_roles" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo"}')"
call_tool "keycloak_get_role" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t, realm:"mcp-demo", roleName:"user"}')"
call_tool "keycloak_discover_environment" "$(jq -n --arg t "${TARGET_ID}" '{targetId:$t}')"

log "tools/call keycloak_get_realm (unknown target)..."
UNKNOWN_PARAMS="$(jq -n '{name:"keycloak_get_realm", arguments:{targetId:"does-not-exist", realm:"master"}}')"
mcp_call "tools/call" "${UNKNOWN_PARAMS}"
UNKNOWN_RESP="${LAST_MCP_PAYLOAD}"
if jq -e '.result.isError == true or (.result.content[0].text|tostring|test("TARGET_NOT_FOUND"))' >/dev/null 2>&1 <<<"${UNKNOWN_RESP}"; then
  log "TARGET_NOT_FOUND validated"
else
  printf '%s\n' "${UNKNOWN_RESP}" | jq . || true
  die "expected TARGET_NOT_FOUND for unknown target"
fi

# Optional second target (multi-target compose profile)
if [[ "${SMOKE_MULTI_TARGET:-false}" == "true" ]]; then
  call_tool "keycloak_list_realms" "$(jq -n --arg t "${TARGET_ID_B}" '{targetId:$t}')"
fi

log "Smoke test completed successfully."
