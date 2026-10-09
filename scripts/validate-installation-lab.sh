#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo_dir"
browser_mode=false
if [[ "${1:-}" == --browser && $# == 1 ]]; then browser_mode=true
elif [[ $# != 0 ]]; then echo 'Usage: validate-installation-lab.sh [--browser]' >&2; exit 2; fi
source "$repo_dir/scripts/identity-lab-runner-common.sh"
log_dir=''
app_pid='' readonly_pid='' fixture_pid='' ui_pid=''
compose_started=false
cleanup() {
  identity_lab_cleanup "$?" "$ui_pid" "$app_pid" "$readonly_pid" "$fixture_pid"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
identity_lab_acquire_lock
log_dir="$(mktemp -d /private/tmp/kcops-installation.XXXXXX)"
identity_lab_run node scripts/identity-lab-runtime.mjs init "$log_dir/runtime.json"
identity_lab_run node scripts/identity-lab-runtime.mjs preflight "$log_dir/runtime.json"
test -f target/quarkus-app/quarkus-run.jar || { echo 'Build with Java 21 through jenv first.' >&2; exit 1; }
node --input-type=module -e '
  import net from "node:net";
  const servers=[];
  try {
    for (const port of [15432,18080,18180,18280,18081,18082,19001,19002,18590,...(process.argv[1]==="true"?[18300]:[])]) {
      const server=net.createServer(); servers.push(server);
      await new Promise((resolve,reject)=>server.once("error",reject).listen(port,"127.0.0.1",resolve));
    }
  } finally { for(const server of servers) server.close(); }
' "$browser_mode"
node scripts/lab-process.mjs 3600000 node scripts/installation-cluster-fixture.mjs >"$log_dir/cluster.log" 2>&1 &
fixture_pid=$!
compose_started=true
identity_lab_run node scripts/identity-lab-runtime.mjs up "$log_dir/runtime.json"
wait_for_url() {
  identity_lab_run node scripts/lab-process.mjs wait-url 180000 "$@"
}
for address in localhost:18280/realms/operations localhost:18080/realms/target-a localhost:18180/realms/target-b; do
  wait_for_url "http://$address/.well-known/openid-configuration"
done
kill -0 "$fixture_pid"
node scripts/lab-process.mjs 3600000 env INSTALLATION_LAB_READ_ONLY=false INSTALLATION_LAB_HTTP_PORT=18081 INSTALLATION_LAB_MANAGEMENT_PORT=19001 \
  JENV_VERSION=21 jenv exec java -Dquarkus.profile=oidc "-Dquarkus.config.locations=$repo_dir/dev/identity-lab/installation.properties" \
  -jar target/quarkus-app/quarkus-run.jar >"$log_dir/setup-platform.log" 2>&1 &
app_pid=$!
wait_for_url http://localhost:19001/q/health/ready "$app_pid"
node scripts/lab-process.mjs 3600000 env INSTALLATION_LAB_READ_ONLY=true INSTALLATION_LAB_HTTP_PORT=18082 INSTALLATION_LAB_MANAGEMENT_PORT=19002 \
  JENV_VERSION=21 jenv exec java -Dquarkus.profile=oidc "-Dquarkus.config.locations=$repo_dir/dev/identity-lab/installation.properties" \
  -jar target/quarkus-app/quarkus-run.jar >"$log_dir/readonly-platform.log" 2>&1 &
readonly_pid=$!
wait_for_url http://localhost:19002/q/health/ready "$readonly_pid"
identity_lab_run node scripts/lab-process.mjs 600000 env INSTALLATION_LAB_OWNED_DB=1 INSTALLATION_LAB_RUNTIME_CONTEXT="$log_dir/runtime.json" node scripts/validate-installation-lab.mjs
if "$browser_mode"; then
  test -f ui/node_modules/vite/bin/vite.js || { echo 'Install UI dependencies first.' >&2; exit 1; }
  node scripts/lab-process.mjs 3600000 env VITE_AUTH_MODE=OIDC VITE_API_BASE_URL=http://localhost:18081 \
    VITE_OIDC_AUTHORITY=http://localhost:18280/realms/operations VITE_OIDC_CLIENT_ID=keycloak-ops-ui \
    node ui/node_modules/vite/bin/vite.js ui --host 127.0.0.1 --port 18300 --strictPort >"$log_dir/ui.log" 2>&1 &
  ui_pid=$!
  wait_for_url http://127.0.0.1:18300/ "$ui_pid"
  echo 'Installation browser lab ready: http://127.0.0.1:18300/ (maximum 15 minutes).'
  echo 'Public setup fixture sam-setup-a; ordinary reader alice-a. Press Enter to remove this lab.'
  read -r -t 900 || true
fi
log_files=("$log_dir/setup-platform.log" "$log_dir/readonly-platform.log" "$log_dir/cluster.log")
if "$browser_mode"; then log_files+=("$log_dir/ui.log"); fi
identity_lab_assert_no_jwt "${log_files[@]}"
