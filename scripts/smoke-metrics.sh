#!/usr/bin/env bash
# Smoke checks for Prometheus + semantic metrics endpoints.
# This is NOT a load test — only connectivity / isolation probes.
set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
API_BASE="${API_BASE:-http://localhost:8081/api/v1}"
TARGET_A="${TARGET_A:-lab-keycloak-a}"
TARGET_B="${TARGET_B:-lab-keycloak-b}"

log() { printf '[smoke-metrics] %s\n' "$*"; }
die() { printf '[smoke-metrics] ERROR: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_cmd curl
require_cmd jq

log "Checking Prometheus health at ${PROMETHEUS_URL}/-/healthy"
curl -fsS "${PROMETHEUS_URL}/-/healthy" | grep -qi healthy \
  || die "Prometheus not healthy"

log "Checking Prometheus ready"
curl -fsS "${PROMETHEUS_URL}/-/ready" | grep -qi ready \
  || die "Prometheus not ready"

if [[ "${SKIP_API:-false}" == "true" ]]; then
  log "SKIP_API=true — skipping platform metrics endpoints"
  log "OK (prometheus only)"
  exit 0
fi

probe_target() {
  local tid="$1"
  log "GET ${API_BASE}/targets/${tid}/metrics/status"
  curl -fsS "${API_BASE}/targets/${tid}/metrics/status" >/dev/null \
    || die "metrics status failed for ${tid}"

  log "GET ${API_BASE}/targets/${tid}/metrics/summary?window=5m"
  curl -fsS "${API_BASE}/targets/${tid}/metrics/summary?window=5m" >/dev/null \
    || die "metrics summary failed for ${tid}"

  log "GET ${API_BASE}/targets/${tid}/metrics/http?window=5m"
  curl -fsS "${API_BASE}/targets/${tid}/metrics/http?window=5m" >/dev/null \
    || die "metrics http failed for ${tid}"

  log "GET ${API_BASE}/targets/${tid}/metrics/database?window=5m"
  curl -fsS "${API_BASE}/targets/${tid}/metrics/database?window=5m" >/dev/null \
    || die "metrics database failed for ${tid}"

  log "GET ${API_BASE}/targets/${tid}/metrics/jvm?window=5m"
  curl -fsS "${API_BASE}/targets/${tid}/metrics/jvm?window=5m" >/dev/null \
    || die "metrics jvm failed for ${tid}"
}

probe_target "${TARGET_A}"
probe_target "${TARGET_B}"

# Isolation: summaries must be target-scoped (different targetId field)
SUM_A=$(curl -fsS "${API_BASE}/targets/${TARGET_A}/metrics/summary?window=5m")
SUM_B=$(curl -fsS "${API_BASE}/targets/${TARGET_B}/metrics/summary?window=5m")
ID_A=$(printf '%s' "${SUM_A}" | jq -r '.targetId // empty')
ID_B=$(printf '%s' "${SUM_B}" | jq -r '.targetId // empty')
[[ "${ID_A}" == "${TARGET_A}" ]] || die "summary A targetId mismatch: ${ID_A}"
[[ "${ID_B}" == "${TARGET_B}" ]] || die "summary B targetId mismatch: ${ID_B}"
[[ "${SUM_A}" != "${SUM_B}" ]] || die "target A and B summaries unexpectedly identical"

log "OK — smoke-metrics passed (A/B isolation checked; not a load test)"
