import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { chmod, mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const scripts = dirname(fileURLToPath(import.meta.url));
const helper = join(scripts, 'identity-lab-runner-common.sh');
async function temporary(t) {
  const directory = await mkdtemp(join(tmpdir(), 'kcops-runner-test-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  return directory;
}
function shell(code, ...args) {
  return spawnSync('bash', ['-c', 'set -euo pipefail\n' + code, 'runner-test', ...args],
    { encoding: 'utf8', timeout: 5_000 });
}

async function isolatedRunner(t, runner) {
  const directory = await temporary(t);
  const isolatedScripts = join(directory, 'scripts');
  const bin = join(directory, 'bin');
  const lock = join(directory, 'shared.lock');
  const trace = join(directory, 'commands');
  await mkdir(isolatedScripts);
  await mkdir(bin);
  for (const file of [runner, 'identity-lab-runner-common.sh', 'lab-process.mjs', 'identity-lab-runtime.mjs']) {
    const source = (await readFile(join(scripts, file), 'utf8'))
      .replace('/private/tmp/kcops-identity-lab.lock', lock)
      .replace('/private/tmp/kcops-identity.XXXXXX', join(directory, 'identity.XXXXXX'))
      .replace('/private/tmp/kcops-configuration.XXXXXX', join(directory, 'configuration.XXXXXX'))
      .replace('/private/tmp/kcops-installation.XXXXXX', join(directory, 'installation.XXXXXX'));
    await writeFile(join(isolatedScripts, file), source);
  }
  // The runtime launches a subprocess, so an exported shell function is not a
  // sufficient fake. This executable shadows Podman for every child process.
  const podman = join(bin, 'podman');
  await writeFile(podman, `#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$RUNNER_TEST_TRACE"
if [[ "$1" == --url ]]; then
  [[ "$2" == ssh://root@127.0.0.1:62342/run/podman/podman.sock && "$3" == --identity && "$4" == /private/tmp/fakekey ]] || exit 98
  shift 4
fi
case "$1:\${2:-}" in
  system:connection)
    printf '%s\\n' '[{"Name":"podman-machine-default-root","IsMachine":true,"URI":"ssh://root@127.0.0.1:62342/run/podman/podman.sock","Identity":"/private/tmp/fakekey"}]' ;;
  info:*) exit 0 ;;
  network:exists|container:exists)
    if [[ "$1" == "$RUNNER_TEST_KIND" ]]; then exit "$RUNNER_TEST_STATUS"; fi
    exit 1 ;;
  *) exit 99 ;;
esac
`);
  await chmod(podman, 0o700);
  return { directory, isolatedScripts, bin, lock, trace };
}

test('atomic lock excludes another process and releases after owner exits', { timeout: 10_000 }, async t => {
  const directory = await temporary(t);
  const lock = join(directory, 'lock');
  const holder = spawn('bash', ['-c', `set -euo pipefail
    source "$1"
    trap identity_lab_release_lock EXIT
    trap 'exit 143' TERM
    identity_lab_acquire_lock "$2"
    printf 'ready\n'
    read -r release || true`, 'holder', helper, lock], { stdio: ['pipe', 'pipe', 'pipe'] });
  const exited = once(holder, 'exit');
  t.after(async () => {
    if (holder.exitCode === null && holder.signalCode === null) {
      holder.kill('SIGTERM');
      await exited;
    }
  });
  const [ready] = await Promise.race([
    once(holder.stdout, 'data'),
    exited.then(() => { throw new Error('Lock holder exited before readiness'); }),
  ]);
  assert.equal(ready.toString(), 'ready\n');
  const owner = await readFile(join(lock, 'owner'), 'utf8');
  const contender = shell('source "$1"; trap identity_lab_release_lock EXIT; identity_lab_acquire_lock "$2"', helper, lock);
  assert.equal(contender.status, 1);
  assert.equal(await readFile(join(lock, 'owner'), 'utf8'), owner);
  holder.stdin.end('release\n');
  assert.equal((await exited)[0], 0);
  await assert.rejects(readFile(join(lock, 'owner')), { code: 'ENOENT' });
});

test('pre-existing stale lock is preserved byte for byte', async t => {
  const lock = join(await temporary(t), 'lock');
  await mkdir(lock);
  await writeFile(join(lock, 'owner'), 'stale-owner\n');
  const result = shell('source "$1"; trap identity_lab_release_lock EXIT; identity_lab_acquire_lock "$2"', helper, lock);
  assert.equal(result.status, 1);
  assert.equal(await readFile(join(lock, 'owner'), 'utf8'), 'stale-owner\n');
});

test('release refuses a changed ownership marker', async t => {
  const lock = join(await temporary(t), 'lock');
  const result = shell(`source "$1"
    identity_lab_acquire_lock "$2"
    printf 'other-owner\n' > "$2/owner"
    identity_lab_release_lock`, helper, lock);
  assert.equal(result.status, 1);
  assert.equal(await readFile(join(lock, 'owner'), 'utf8'), 'other-owner\n');
});

test('exit trap releases an owned lock after a failed operation', async t => {
  const lock = join(await temporary(t), 'lock');
  const result = shell('source "$1"; trap identity_lab_release_lock EXIT; identity_lab_acquire_lock "$2"; exit 7', helper, lock);
  assert.equal(result.status, 7);
  await assert.rejects(readFile(join(lock, 'owner')), { code: 'ENOENT' });
});

for (const runner of ['validate-identity-lab.sh', 'validate-installation-lab.sh', 'validate-configuration-lab.sh']) {
  for (const kind of ['network', 'container']) {
    for (const status of [0, 125]) {
      const observation = status === 0 ? 'existing' : 'unavailable inventory for';
      test(`${runner}: ${observation} ${kind} causes no Compose cleanup`, async t => {
        const { isolatedScripts, bin, lock, trace } = await isolatedRunner(t, runner);
        const result = spawnSync('bash', [join(isolatedScripts, runner)], {
          encoding: 'utf8', timeout: 5_000,
          env: { ...process.env, PATH: `${bin}:${process.env.PATH}`,
            IDENTITY_LAB_PODMAN_CONNECTION: 'podman-machine-default-root',
            RUNNER_TEST_TRACE: trace, RUNNER_TEST_KIND: kind, RUNNER_TEST_STATUS: String(status) },
        });
        assert.equal(result.status, 1, result.stderr);
        const commands = await readFile(trace, 'utf8');
        assert.ok(commands.includes(kind + ' exists'), result.stderr);
        assert.ok(commands.includes('--url ssh://root@127.0.0.1:62342/run/podman/podman.sock --identity /private/tmp/fakekey'), commands);
        assert.ok(!commands.includes('compose'), commands);
        assert.ok(!commands.includes(' rm '), commands);
        assert.match(result.stderr, status === 0 ? /Refusing to reuse/ : /failed \(exit 125\)/);
        await assert.rejects(readFile(join(lock, 'owner')), { code: 'ENOENT' });
      });
    }
  }
}

for (const implicitConfig of ['.env', 'config/application.properties', 'config/application-oidc.yaml', 'ui/.env.development.local']) {
  test(`configuration runner refuses implicit ${implicitConfig} before any runtime access`, async t => {
    const { directory, isolatedScripts, bin, lock, trace } = await isolatedRunner(t, 'validate-configuration-lab.sh');
    const fixture = join(directory, implicitConfig);
    await mkdir(dirname(fixture), { recursive: true });
    const canary = 'fixture-private-override-not-for-logs';
    await writeFile(fixture, canary);
    const result = spawnSync('bash', [join(isolatedScripts, 'validate-configuration-lab.sh')], {
      encoding: 'utf8', timeout: 5_000,
      env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, RUNNER_TEST_TRACE: trace },
    });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Refusing implicit local configuration/);
    assert.equal((result.stdout + result.stderr).includes(canary), false);
    assert.equal(await readFile(fixture, 'utf8'), canary);
    await assert.rejects(readFile(trace), { code: 'ENOENT' });
    await assert.rejects(readFile(join(lock, 'owner')), { code: 'ENOENT' });
  });
}

for (const [name, operationStatus, cleanupStatus, expectedStatus, retained] of [
  ['retains the original failed operation', 7, 0, 7, false],
  ['retains interruption status', 143, 0, 143, false],
  ['fails a successful operation when cleanup fails', 0, 1, 1, true],
  ['does not overwrite an operation failure with a cleanup failure', 7, 1, 7, true],
  ['retains the lock when its runtime supervisor exits 137', 0, 137, 1, true],
]) {
  test(`cleanup ${name}`, async t => {
    const directory = await temporary(t);
    const lock = join(directory, 'lock');
    const trace = join(directory, 'cleanup');
    const result = shell(`source "$1"
      repo_dir="$2"; log_dir="$2"; compose_started=true
      node() {
        [[ "$1" == "$repo_dir/scripts/identity-lab-runtime.mjs" && "$2" == cleanup && "$3" == "$log_dir/runtime.json" ]] || return 99
        printf 'owned-runtime-cleanup\\n' >> "$cleanup_trace"
        return "$cleanup_status"
      }
      cleanup_trace="$4"; cleanup_status="$5"
      trap 'identity_lab_cleanup "$?"' EXIT
      identity_lab_acquire_lock "$3"
      exit "$6"`, helper, directory, lock, trace, String(cleanupStatus), String(operationStatus));
    assert.equal(result.status, expectedStatus, result.stderr);
    assert.equal(await readFile(trace, 'utf8'), 'owned-runtime-cleanup\n');
    if (retained) {
      assert.match(await readFile(join(lock, 'owner'), 'utf8'), /^\d+-\d+-\d+\n$/);
      assert.match(result.stderr, /Lock retained/);
    } else {
      await assert.rejects(readFile(join(lock, 'owner')), { code: 'ENOENT' });
    }
  });
}

test('uncertain supervisor exit classification preserves intentional interruption statuses', () => {
  const cases = [[0, false], [1, false], [124, false], [125, true], [126, false],
    [127, true], [128, true], [129, true], [130, false], [137, true], [143, false],
    [144, true], [255, true]];
  for (const [status, uncertain] of cases) {
    const result = shell('source "$1"; identity_lab_uncertain_exit "$2"', helper, String(status));
    assert.equal(result.status, uncertain ? 0 : 1, `Unexpected classification for exit ${status}: ${result.stderr}`);
  }
});

for (const status of [125, 127, 137]) {
  test(`unverified process cleanup (exit ${status}) retains the lock`, async t => {
    const directory = await temporary(t);
    const lock = join(directory, 'lock');
    const result = shell(`source "$1"
      repo_dir="$2"; log_dir=''; compose_started=false
      identity_lab_acquire_lock "$3"
      (exit "$4") & owned_pid=$!
      wait "$owned_pid" || [[ "$?" == "$4" ]]
      identity_lab_cleanup 0 "$owned_pid"`, helper, directory, lock, String(status));
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stderr, /Process termination could not be verified/);
    assert.match(result.stderr, /Lock retained/);
    assert.match(await readFile(join(lock, 'owner'), 'utf8'), /^\d+-\d+-\d+\n$/);
  });

  test(`foreground supervisor exit ${status} retains its failure and the lock`, async t => {
    const directory = await temporary(t);
    const lock = join(directory, 'lock');
    const result = shell(`source "$1"
      repo_dir="$2"; log_dir=''; compose_started=false
      trap 'identity_lab_cleanup "$?"' EXIT
      identity_lab_acquire_lock "$3"
      identity_lab_run bash -c 'exit "$1"' fixture "$4"`, helper, directory, lock, String(status));
    assert.equal(result.status, status, result.stderr);
    assert.match(result.stderr, /Lock retained/);
    assert.match(await readFile(join(lock, 'owner'), 'utf8'), /^\d+-\d+-\d+\n$/);
  });
}

test('process cleanup does not signal an already completed child', async t => {
  const trace = join(await temporary(t), 'signals');
  const result = shell(`source "$1"
    signal_trace="$2"
    (exit 0) & owned_pid=$!
    wait "$owned_pid"
    kill() { printf '%s\\n' "$*" >> "$signal_trace"; }
    identity_lab_stop_process "$owned_pid"`, helper, trace);
  assert.equal(result.status, 0, result.stderr);
  await assert.rejects(readFile(trace), { code: 'ENOENT' });
});

test('process cleanup ignores a reused PID absent from its owned running jobs', async t => {
  const trace = join(await temporary(t), 'commands');
  const result = shell(`source "$1"
    command_trace="$2"
    jobs() { [[ "$*" == -pr ]]; printf 'jobs\\n' >> "$command_trace"; printf '24681\\n'; }
    wait() { [[ "$1" == 24680 ]]; printf 'wait\\n' >> "$command_trace"; }
    kill() { printf 'unexpected-kill\\n' >> "$command_trace"; }
    identity_lab_stop_process 24680`, helper, trace);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(await readFile(trace, 'utf8'), 'jobs\nwait\n');
});

test('foreground supervisor responds to TERM, stops its child and retains exit 143', { timeout: 10_000 }, async t => {
  const directory = await temporary(t);
  const lock = join(directory, 'lock');
  const fixture = join(directory, 'pending.mjs');
  await writeFile(fixture, 'console.log(`ready:${process.pid}`); setInterval(() => {}, 1000);\n');
  const holder = spawn('bash', ['-c', `set -euo pipefail
    source "$1"
    repo_dir="$2"; log_dir=''; compose_started=false
    trap 'identity_lab_cleanup "$?"' EXIT
    trap 'exit 143' TERM
    identity_lab_acquire_lock "$3"
    identity_lab_run node "$4" 60000 node "$5"`, 'holder', helper, directory, lock,
  join(scripts, 'lab-process.mjs'), fixture], { detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
  let fixturePid;
  const exited = once(holder, 'exit');
  const errors = [];
  holder.stderr.on('data', chunk => errors.push(chunk));
  t.after(() => {
    // These are exact groups created by this fixture, never a process search.
    for (const pid of [holder.pid, fixturePid]) {
      if (pid) { try { process.kill(-pid, 'SIGKILL'); } catch (error) { if (error.code !== 'ESRCH') throw error; } }
    }
  });
  const [ready] = await Promise.race([
    once(holder.stdout, 'data'),
    exited.then(() => { throw new Error('Foreground fixture exited before readiness: ' + Buffer.concat(errors)); }),
  ]);
  const match = /^ready:(\d+)\n$/.exec(ready.toString());
  assert.ok(match, ready.toString());
  fixturePid = Number(match[1]);
  const started = performance.now();
  holder.kill('SIGTERM');
  const [code, signal] = await exited;
  assert.equal(code, 143, Buffer.concat(errors).toString());
  assert.equal(signal, null);
  assert.ok(performance.now() - started < 5_000, 'Foreground interruption exceeded its termination bound');
  assert.throws(() => process.kill(fixturePid, 0), { code: 'ESRCH' });
  await assert.rejects(readFile(join(lock, 'owner')), { code: 'ENOENT' });
});

for (const status of [0, 1, 2, 127]) {
  test(`JWT scan handles scanner status ${status} without a false PASS`, () => {
    const result = shell('source "$1"; scan_exit="$2"; rg() { return "$scan_exit"; }; identity_lab_assert_no_jwt unused.log', helper, String(status));
    assert.equal(result.status, status === 1 ? 0 : 1);
    assert.equal(result.stdout.includes('PASS:'), status === 1);
    if (status !== 1) assert.match(result.stderr, /FAIL:/);
  });
}

test('JWT scan detects fixture material, accepts clean logs, and rejects missing logs', async t => {
  const directory = await temporary(t);
  const clean = join(directory, 'clean.log');
  const marked = join(directory, 'marked.log');
  const marker = 'eyJmaXh0dXJl.eyJmaXh0dXJl.c2lnbmF0dXJl';
  await writeFile(clean, 'Ordinary fixture log\n');
  await writeFile(marked, marker + '\n');
  const run = path => shell('source "$1"; identity_lab_assert_no_jwt "$2"', helper, path);
  assert.equal(run(clean).status, 0);
  const rejected = run(marked);
  assert.equal(rejected.status, 1);
  assert.ok(!(rejected.stdout + rejected.stderr).includes(marker));
  const missing = run(join(directory, 'missing.log'));
  assert.equal(missing.status, 1);
  assert.ok(!missing.stdout.includes('PASS:'));
});
