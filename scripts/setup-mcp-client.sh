#!/usr/bin/env bash
# Create / update the keycloak-mcp confidential client in the master realm
# and assign service-account view/query roles. Prefer scripts/setup-dev.sh for
# the full local demo bootstrap (includes mcp-demo realm-admin convenience).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Reuse the main bootstrap; this script exists as the focused client helper.
exec "${ROOT_DIR}/scripts/setup-dev.sh" "$@"
