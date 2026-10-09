#!/usr/bin/env bash
set -euo pipefail
umask 077
# This affects only the runner's children, never the caller's shell/global setup.
unset NODE_OPTIONS NODE_USE_ENV_PROXY HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy COMPOSE_PROFILES
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"
browser_mode=false
for option in "$@"; do
  case "$option" in
    --browser) browser_mode=true ;;
    *) echo 'Usage: bash scripts/validate-configuration-lab.sh [--browser]' >&2; exit 2 ;;
  esac
done
# Refuse local implicit configuration instead of loading unknown credentials or
# grants. The backend/UI receive an allowlisted process-local environment below.
for implicit_config in .env config/application.properties config/application.yaml config/application.yml config/application-oidc.properties config/application-oidc.yaml config/application-oidc.yml ui/.env ui/.env.local ui/.env.development ui/.env.development.local; do
  if [[ -e "$implicit_config" || -L "$implicit_config" ]]; then
    echo 'Refusing implicit local configuration for the restricted lab; use an isolated checkout.' >&2
    exit 1
  fi
done
source "$repo_dir/scripts/identity-lab-runner-common.sh"
log_dir=''
app_pid=''
ui_pid=''
compose_started=false
log_files=()
cleanup() {
  local result="$?" owned_pid
  trap - EXIT
  trap '' INT TERM
  # Stop writers before scanning, including on failure/interruption. Preserve the
  # original result and retain the lock if termination cannot be established.
  for owned_pid in "$identity_lab_foreground_pid" "$ui_pid" "$app_pid"; do
    identity_lab_stop_process "$owned_pid" || identity_lab_process_uncertain=true
  done
  identity_lab_foreground_pid=''
  if (( ${#log_files[@]} > 0 )); then
    identity_lab_assert_no_jwt "${log_files[@]}" || { [[ "$result" != 0 ]] || result=1; }
  fi
  identity_lab_cleanup "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
identity_lab_acquire_lock
log_dir="$(mktemp -d /private/tmp/kcops-configuration.XXXXXX)"
identity_lab_run node scripts/identity-lab-runtime.mjs init "$log_dir/runtime.json"
identity_lab_run node scripts/identity-lab-runtime.mjs preflight "$log_dir/runtime.json"
test -f target/quarkus-app/quarkus-run.jar || { echo 'Build with JENV_VERSION=21 jenv exec mvn clean verify first.' >&2; exit 1; }
node --input-type=module -e '
  import net from "node:net";
  const servers = [];
  try {
    const ports = [15432,18080,18180,18280,18081,19001];
    if (process.argv[1] === "true") ports.push(18300);
    for (const port of ports) {
      const server = net.createServer();
      await new Promise((resolve, reject) => server.once("error", reject).listen(port, "127.0.0.1", resolve));
      servers.push(server);
    }
  } finally { for (const server of servers) server.close(); }
' "$browser_mode"
compose_started=true
identity_lab_run node scripts/identity-lab-runtime.mjs up "$log_dir/runtime.json" default
for address in localhost:18280/realms/operations localhost:18080/realms/target-a localhost:18180/realms/target-b; do
  identity_lab_run node scripts/lab-process.mjs wait-url 180000 "http://$address/.well-known/openid-configuration"
done
node scripts/lab-process.mjs 3600000 env -i PATH="$PATH" HOME="$HOME" JENV_ROOT="${JENV_ROOT:-$HOME/.jenv}" JENV_VERSION=21 jenv exec java -Dquarkus.profile=oidc "-Dquarkus.config.locations=$repo_dir/dev/identity-lab/configuration.properties" -jar target/quarkus-app/quarkus-run.jar >"$log_dir/platform.log" 2>&1 &
app_pid=$!
log_files+=("$log_dir/platform.log")
identity_lab_run node scripts/lab-process.mjs wait-url 120000 http://localhost:19001/q/health/ready "$app_pid"
log_files+=("$log_dir/checks.log")
checks_status=0
if ! identity_lab_run node scripts/lab-process.mjs 600000 node scripts/configuration-lab-checks.mjs --output "$log_dir/configuration-checks.json" >"$log_dir/checks.log" 2>&1; then
  echo 'Configuration checks failed; see the sanitized checks.log in the run directory.' >&2
  checks_status=1
  if ! "$browser_mode" || "$identity_lab_process_uncertain"; then exit "$checks_status"; fi
  echo 'Browser retained for bounded diagnosis only; this run will still exit nonzero.' >&2
fi
if [[ -f "$log_dir/configuration-checks.json" ]]; then log_files+=("$log_dir/configuration-checks.json"); fi
if [[ "$checks_status" == 0 ]]; then
  echo 'Authenticated configuration checks passed; safe aggregate retained in the run directory.'
fi
if "$browser_mode"; then
  test -f ui/node_modules/vite/bin/vite.js || { echo 'Install UI dependencies with npm ci first.' >&2; exit 1; }
  node scripts/lab-process.mjs 3600000 env -i PATH="$PATH" HOME="$HOME" VITE_AUTH_MODE=OIDC VITE_API_BASE_URL=http://localhost:18081 \
    VITE_OIDC_AUTHORITY=http://localhost:18280/realms/operations VITE_OIDC_CLIENT_ID=keycloak-ops-ui \
    node ui/node_modules/vite/bin/vite.js ui --host 127.0.0.1 --port 18300 --strictPort >"$log_dir/ui.log" 2>&1 &
  ui_pid=$!
  log_files+=("$log_dir/ui.log")
  identity_lab_run node scripts/lab-process.mjs wait-url 30000 http://127.0.0.1:18300/ "$ui_pid"
  echo 'Configuration browser lab ready: http://127.0.0.1:18300/configuration (15 minutes).'
  echo 'Inspect both restricted operators, sign out, close the lab tab, then press Enter.'
  if ! read -r -t 900; then
    echo 'Browser walkthrough was not acknowledged within its deadline.' >&2
    exit 124
  fi
  echo 'Browser walkthrough acknowledged; its results require a separate evidence ledger.'
fi
exit "$checks_status"
