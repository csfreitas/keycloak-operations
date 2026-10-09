#!/usr/bin/env bash
# Shared by both disposable identity-lab runners; sourcing has no side effects.
identity_lab_lock_held=false
identity_lab_lock_dir=''
identity_lab_lock_token=''
identity_lab_foreground_pid=''
identity_lab_process_uncertain=false

identity_lab_uncertain_exit() {
  [[ "$1" == 125 || "$1" == 127 || ( "$1" -ge 128 && "$1" != 130 && "$1" != 143 ) ]]
}

# Run supervisors asynchronously so Bash can service INT/TERM while waiting.
# Commands supplied here must themselves have bounded termination (lab-process or
# identity-lab-runtime); never use this as an arbitrary background job launcher.
identity_lab_run() {
  local status=0
  "$@" &
  identity_lab_foreground_pid=$!
  wait "$identity_lab_foreground_pid" || status=$?
  identity_lab_foreground_pid=''
  if identity_lab_uncertain_exit "$status"; then identity_lab_process_uncertain=true; fi
  return "$status"
}

identity_lab_stop_process() {
  local owned_pid="${1:-}" status=0 active_pid
  [[ -n "$owned_pid" ]] || return 0
  # Bash retains exited-child statuses, but the OS may reuse their PIDs. Signal
  # only a still-running job owned by this shell, then reap the recorded status.
  for active_pid in $(jobs -pr); do
    if [[ "$active_pid" == "$owned_pid" ]]; then kill -TERM "$owned_pid" 2>/dev/null || true; break; fi
  done
  wait "$owned_pid" 2>/dev/null || status=$?
  # Unexpected supervisor loss (e.g. OOM/SIGKILL) cannot certify its group gone.
  if identity_lab_uncertain_exit "$status"; then
    echo 'Process termination could not be verified; preserving the lab lock.' >&2
    return 1
  fi
}

identity_lab_cleanup() {
  local result="$1" cleanup_ok=true owned_pid
  shift
  trap - EXIT
  trap '' INT TERM
  if "$identity_lab_process_uncertain"; then cleanup_ok=false; fi
  for owned_pid in "$identity_lab_foreground_pid" "$@"; do
    identity_lab_stop_process "$owned_pid" || cleanup_ok=false
  done
  if "$compose_started"; then
    node "$repo_dir/scripts/identity-lab-runtime.mjs" cleanup "$log_dir/runtime.json" || cleanup_ok=false
  fi
  if "$cleanup_ok"; then
    identity_lab_release_lock || { [[ "$result" != 0 ]] || result=1; }
  else
    echo 'Cleanup is unconfirmed. Lock retained; inspect this run before recovery. The runner never restarts the VM.' >&2
    [[ "$result" != 0 ]] || result=1
  fi
  if [[ -n "$log_dir" ]]; then echo "Local diagnostic logs and runtime identity: $log_dir"; fi
  exit "$result"
}

identity_lab_acquire_lock() {
  identity_lab_lock_dir="${1:-/private/tmp/kcops-identity-lab.lock}"
  identity_lab_lock_token="$$-$RANDOM-$RANDOM"
  if ! mkdir -m 700 "$identity_lab_lock_dir" 2>/dev/null; then
    echo "Refusing to start: identity-lab lock already exists or cannot be created: $identity_lab_lock_dir" >&2
    echo 'Inspect the existing owner/resources separately; this runner never removes an existing or stale lock.' >&2
    return 1
  fi
  identity_lab_lock_held=true
  if ! (umask 077; set -o noclobber; printf '%s\n' "$identity_lab_lock_token" > "$identity_lab_lock_dir/owner"); then
    echo 'Unable to record lab lock ownership; preserving the lock for inspection.' >&2
    return 1
  fi
}

identity_lab_release_lock() {
  local recorded_owner=''
  if ! "$identity_lab_lock_held"; then return 0; fi
  if [[ ! -d "$identity_lab_lock_dir" || -L "$identity_lab_lock_dir" || -L "$identity_lab_lock_dir/owner" ]] \
      || ! IFS= read -r recorded_owner < "$identity_lab_lock_dir/owner" \
      || [[ "$recorded_owner" != "$identity_lab_lock_token" ]]; then
    echo 'Lab lock ownership changed or cannot be verified; preserving the lock for inspection.' >&2
    return 1
  fi
  # Remove only our marker and then an empty directory, never recurse or reclaim.
  if ! rm -- "$identity_lab_lock_dir/owner" || ! rmdir -- "$identity_lab_lock_dir"; then
    echo 'Unable to release the owned lab lock; inspect the remaining directory.' >&2
    return 1
  fi
  identity_lab_lock_held=false
}

identity_lab_assert_no_jwt() {
  local scan_status
  if [[ $# == 0 ]]; then
    echo 'FAIL: no local log files were supplied for the JWT check.' >&2
    return 1
  fi
  if rg --text --quiet 'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+' "$@" 2>/dev/null; then
    scan_status=0
  else
    scan_status=$?
  fi
  case "$scan_status" in
    1) echo 'PASS: no compact JWT found in local lab logs' ;;
    0) echo 'FAIL: compact JWT material found in local logs (contents withheld).' >&2; return 1 ;;
    *) echo "FAIL: JWT log verification could not complete (scanner exit $scan_status)." >&2; return 1 ;;
  esac
}
