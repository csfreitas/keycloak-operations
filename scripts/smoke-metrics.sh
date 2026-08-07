#!/usr/bin/env bash
# Smoke checks for Prometheus + semantic metrics endpoints.
# This is NOT a load test — only connectivity / health probes.
set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
API_BASE="${API_BASE:-http://localhost:8081/api/v1}"
TARGET_ID="${TARGET_ID:-lab-keycloak-a}"

log() { printf '[smoke-metrics] %s\n' "$*"; }
die() { printf '[smoke-metrics] ERROR: %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_cmd curl

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

log "GET ${API_BASE}/targets/${TARGET_ID}/metrics/status"
curl -fsS "${API_BASE}/targets/${TARGET_ID}/metrics/status" >/dev/null \
  || die "metrics status endpoint failed"

log "GET ${API_BASE}/targets/${TARGET_ID}/metrics/summary?window=5m"
curl -fsS "${API_BASE}/targets/${TARGET_ID}/metrics/summary?window=5m" >/dev/null \
  || die "metrics summary endpoint failed"

log "GET ${API_BASE}/targets/${TARGET_ID}/metrics/http?window=5m"
curl -fsS "${API_BASE}/targets/${TARGET_ID}/metrics/http?window=5m" >/dev/null \
  || die "metrics http endpoint failed"

log "OK — smoke-metrics passed (not a load test)"
