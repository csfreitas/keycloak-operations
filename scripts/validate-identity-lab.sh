#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"
browser_mode=false
browser_negatives=false
metrics_mode=false
reference_agent=false
for option in "$@"; do
  case "$option" in
    --browser) browser_mode=true ;;
    --browser-negatives) browser_mode=true; browser_negatives=true ;;
    --metrics) metrics_mode=true ;;
    --reference-agent) reference_agent=true ;;
    *) echo 'Usage: validate-identity-lab.sh [--browser | --browser-negatives] [--metrics] [--reference-agent]' >&2; exit 2 ;;
  esac
done
metrics_type=NONE
if "$metrics_mode"; then
  metrics_type=PROMETHEUS
fi
source "$repo_dir/scripts/identity-lab-runner-common.sh"
log_dir=''
app_pid=''
ui_pid=''
compose_started=false
cleanup() {
  identity_lab_cleanup "$?" "$ui_pid" "$app_pid"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
identity_lab_acquire_lock
log_dir="$(mktemp -d /private/tmp/kcops-identity.XXXXXX)"
identity_lab_run node scripts/identity-lab-runtime.mjs init "$log_dir/runtime.json"
identity_lab_run node scripts/identity-lab-runtime.mjs preflight "$log_dir/runtime.json"
test -f target/quarkus-app/quarkus-run.jar || { echo 'Build with JENV_VERSION=21 jenv exec mvn clean verify first.' >&2; exit 1; }
node --input-type=module -e '
  import net from "node:net";
  const servers = [];
  try {
    const ports = [15432,18080,18180,18280,18081,19001];
    if (process.argv[1] === "true") ports.push(18300);
    if (process.argv[2] === "true") ports.push(18490);
    for (const port of ports) {
      const server = net.createServer();
      await new Promise((resolve, reject) => server.once("error", reject).listen(port, "127.0.0.1", resolve));
      servers.push(server);
    }
  } finally { for (const server of servers) server.close(); }
' "$browser_mode" "$metrics_mode"
compose_started=true
runtime_mode=default
if "$metrics_mode"; then runtime_mode=metrics; fi
identity_lab_run node scripts/identity-lab-runtime.mjs up "$log_dir/runtime.json" "$runtime_mode"
for address in localhost:18280/realms/operations localhost:18080/realms/target-a localhost:18180/realms/target-b; do
  identity_lab_run node scripts/lab-process.mjs wait-url 180000 "http://$address/.well-known/openid-configuration"
done
node scripts/lab-process.mjs 3600000 env IDENTITY_LAB_METRICS_TYPE="$metrics_type" JENV_VERSION=21 jenv exec java -Dquarkus.profile=oidc "-Dquarkus.config.locations=$repo_dir/dev/identity-lab/platform.properties" -jar target/quarkus-app/quarkus-run.jar >"$log_dir/platform.log" 2>&1 &
app_pid=$!
identity_lab_run node scripts/lab-process.mjs wait-url 120000 http://localhost:19001/q/health/ready "$app_pid"
identity_lab_run node scripts/lab-process.mjs 600000 env IDENTITY_LAB_METRICS="$metrics_mode" IDENTITY_LAB_REFERENCE_AGENT="$reference_agent" node scripts/validate-identity-lab.mjs
log_files=("$log_dir/platform.log")
if "$browser_mode"; then
  test -f ui/node_modules/vite/bin/vite.js || { echo 'Install UI dependencies with npm ci first.' >&2; exit 1; }
  if "$browser_negatives"; then
    # One shared walkthrough deadline; each UI process is stopped before the next.
    browser_deadline=$((SECONDS + 900))
    for scenario in wrong-issuer wrong-audience expired valid; do
      remaining=$((browser_deadline - SECONDS))
      if ((remaining <= 0)); then
        echo 'Browser negative walkthrough deadline reached before the next scenario.' >&2
        exit 124
      fi
      readiness_budget=$((remaining < 30 ? remaining * 1000 : 30000))
      node scripts/lab-process.mjs 3600000 env KCOPS_BROWSER_SCENARIO="$scenario" \
        node ui/node_modules/vite/bin/vite.js --config dev/ui-browser-negatives/vite.config.mjs >"$log_dir/ui-$scenario.log" 2>&1 &
      ui_pid=$!
      log_files+=("$log_dir/ui-$scenario.log")
      identity_lab_run node scripts/lab-process.mjs wait-url "$readiness_budget" http://127.0.0.1:18300/ "$ui_pid"
      echo "Browser negative lab ready: $scenario at http://127.0.0.1:18300/"
      echo 'Observe the real login/API result, close its lab tab, then press Enter for the next scenario.'
      remaining=$((browser_deadline - SECONDS))
      if ((remaining <= 0)) || ! read -r -t "$remaining"; then
        echo 'Browser negative walkthrough was not acknowledged within its shared deadline.' >&2
        exit 124
      fi
      if ! identity_lab_stop_process "$ui_pid"; then
        identity_lab_process_uncertain=true
        exit 1
      fi
      ui_pid=''
    done
    echo 'Browser negative walkthrough acknowledged; results require the separate browser evidence ledger.'
  else
    node scripts/lab-process.mjs 3600000 env VITE_AUTH_MODE=OIDC VITE_API_BASE_URL=http://localhost:18081 \
      VITE_OIDC_AUTHORITY=http://localhost:18280/realms/operations VITE_OIDC_CLIENT_ID=keycloak-ops-ui \
      node ui/node_modules/vite/bin/vite.js ui --host 127.0.0.1 --port 18300 --strictPort >"$log_dir/ui.log" 2>&1 &
    ui_pid=$!
    identity_lab_run node scripts/lab-process.mjs wait-url 30000 http://127.0.0.1:18300/ "$ui_pid"
    echo 'Browser lab ready: http://127.0.0.1:18300/ (maximum 15 minutes).'
    echo 'Press Enter when finished; this removes only the disposable lab and stops its UI/backend.'
    read -r -t 900 || true
    log_files+=("$log_dir/ui.log")
  fi
fi
identity_lab_assert_no_jwt "${log_files[@]}"
