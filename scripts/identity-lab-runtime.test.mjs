import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { createRuntime, validateFixtureDatabase } from './identity-lab-runtime.mjs';

// Unit commands are injected; CLI cases use an executable fake in an isolated
// PATH. No test contacts a real container runtime or changes the host environment.
const runId = '123e4567-e89b-42d3-a456-426614174000';
const project = 'keycloak-operations-identity-lab';
const networkName = `${project}_default`;
const runLabel = 'io.github.keycloak-operations.run-id';
const purposeLabel = 'io.github.keycloak-operations.purpose';
const containerId = 'a'.repeat(64);
const replacementId = 'c'.repeat(64);
const networkId = 'b'.repeat(64);
const context = Object.freeze({
  runId,
  uri: 'ssh://root@127.0.0.1:62342/run/podman/podman.sock',
  identity: '/private/tmp/fixture-key',
  connection: 'podman-machine-default-root',
});
const names = [
  'kcops-identity-postgres', 'kcops-identity-idp', 'kcops-identity-target-a',
  'kcops-identity-target-b', 'kcops-identity-prometheus',
];
function labels(extra = {}) {
  return { [runLabel]: runId, [purposeLabel]: 'identity-validation',
    'com.docker.compose.project': project, ...extra };
}
function container(extra = {}) {
  return { Id: containerId, ID: containerId, Name: names[0],
    Config: { Labels: labels() },
    HostConfig: { Tmpfs: { '/var/lib/postgresql/data': 'rw' } },
    Mounts: [], ...extra };
}
function network(extra = {}) {
  return { id: networkId, name: networkName, labels: labels(), containers: {}, ...extra };
}
function result(code = 0, stdout = '', stderr = '') {
  return { code, stdout, stderr, timedOut: false, signal: null };
}
const json = value => result(0, JSON.stringify(value));
const sshEof = () => result(125, '', 'Error: ssh: handshake failed: EOF\n');

function fixture({ containers = [], ownedNetwork = null, intercept = () => undefined,
  runtimeOptions = {} } = {}) {
  const calls = [];
  const logs = [];
  const state = { containers: new Map(containers.map(item => [item.Id ?? item.ID, item])),
    network: ownedNetwork };
  const command = async (executable, suppliedArgs, options) => {
    assert.equal(executable, 'podman');
    assert.deepEqual(suppliedArgs.slice(0, 4), ['--url', context.uri, '--identity', context.identity],
      'Each command must retain the selected runtime, independent of the default connection');
    const args = suppliedArgs.slice(4);
    calls.push({ args, options });
    const intercepted = await intercept(args, state, calls);
    if (intercepted !== undefined) return intercepted;
    if (args[0] === 'info') return json({ host: { os: 'linux' }, store: {} });
    if (args[0] === 'network' && args[1] === 'exists') {
      return result(state.network && [state.network.name, state.network.id].includes(args[2]) ? 0 : 1);
    }
    if (args[0] === 'container' && args[1] === 'exists') {
      return result([...state.containers.values()].some(item => item.Name === args[2]) ? 0 : 1);
    }
    if (args[0] === 'ps') {
      assert.ok(args.includes(`label=${runLabel}=${runId}`), 'Listing must filter the exact run');
      return json([...state.containers.values()].filter(item => item.Config.Labels[runLabel] === runId)
        .map(item => ({ Id: item.Id, ID: item.ID, Names: [item.Name], Labels: item.Config.Labels })));
    }
    if (args[0] === 'container' && args[1] === 'inspect') {
      const item = state.containers.get(args[2]);
      return item ? json([item]) : result(125, '', 'Error: no such container');
    }
    if (args[0] === 'rm') {
      assert.deepEqual(args, ['rm', '-f', '--ignore', args[3]], 'No volume or broad deletion options are allowed');
      assert.match(args[3], /^[0-9a-f]{64}$/, 'Removal must use the inspected full ID');
      // --ignore makes retrying the same already-removed ID idempotent.
      state.containers.delete(args[3]);
      return result(0, `${args[3]}\n`);
    }
    if (args[0] === 'network' && args[1] === 'inspect') return json([state.network]);
    if (args[0] === 'network' && args[1] === 'rm') {
      assert.deepEqual(args, ['network', 'rm', networkId], 'No forced/name-based network deletion');
      if (!state.network) return result(1, '', 'Error: no such network');
      state.network = null;
      return result(0, `${networkId}\n`);
    }
    if (args[0] === 'compose') return result();
    throw new Error(`Unexpected fake runtime command: ${args.join(' ')}`);
  };
  const runtime = createRuntime(context, { command, timeoutMs: 100, budgetMs: 1000,
    retryDelayMs: 1, attempts: 3, log: message => logs.push(message), ...runtimeOptions });
  return { runtime, state, calls, logs };
}
const matching = (calls, ...prefix) => calls.filter(({ args }) => prefix.every((part, index) => args[index] === part));
const mutations = calls => calls.filter(({ args }) => ['rm', 'compose', 'volume', 'image', 'system'].includes(args[0])
  || (args[0] === 'network' && args[1] === 'rm'));

test('preflight pins every command and checks all reserved names without mutation', async () => {
  const { runtime, calls } = fixture();
  await runtime.preflight();
  assert.equal(matching(calls, 'info').length, 1);
  assert.deepEqual(matching(calls, 'container', 'exists').map(call => call.args[2]).sort(), [...names].sort());
  assert.ok(matching(calls, 'network', 'exists').some(call => call.args[2] === networkName));
  assert.equal(mutations(calls).length, 0);
});

for (const existing of ['container', 'network']) {
  test(`preflight refuses existing ${existing} without cleanup`, async () => {
    const { runtime, calls } = fixture(existing === 'container'
      ? { containers: [container()] } : { ownedNetwork: network() });
    await assert.rejects(runtime.preflight());
    assert.equal(mutations(calls).length, 0);
  });
  test(`preflight does not interpret ${existing} existence error 125 as absence`, async () => {
    const { runtime, calls } = fixture({ intercept: args => args[0] === existing && args[1] === 'exists'
      ? result(125, '', 'Error: connection configuration is invalid') : undefined });
    await assert.rejects(runtime.preflight());
    assert.equal(matching(calls, existing, 'exists').length, 1);
    assert.equal(mutations(calls).length, 0);
  });
}

test('preflight retries a transient SSH EOF read and records the recovery', async () => {
  let attempts = 0;
  const { runtime, calls, logs } = fixture({ intercept: args => args[0] === 'info' && ++attempts === 1
    ? sshEof() : undefined });
  await runtime.preflight();
  assert.equal(matching(calls, 'info').length, 2);
  assert.ok(logs.length > 0, 'A recovered transport failure must remain visible');
});

test('transient read retries stop at the configured attempt limit', async () => {
  const { runtime, calls } = fixture({ intercept: args => args[0] === 'info' ? sshEof() : undefined });
  await assert.rejects(runtime.preflight());
  assert.equal(matching(calls, 'info').length, 3);
  assert.equal(mutations(calls).length, 0);
});

for (const diagnostic of [
  'Error: Permission denied (publickey)', 'Error: authentication failed',
  'Error: permission denied', 'Error: connection configuration is invalid',
  'Error: EOF while parsing configuration',
]) {
  test(`permanent or unrelated error is not retried: ${diagnostic}`, async () => {
    const { runtime, calls } = fixture({ intercept: args => args[0] === 'info'
      ? result(125, '', diagnostic) : undefined });
    await assert.rejects(runtime.preflight());
    assert.equal(matching(calls, 'info').length, 1);
  });
}

test('startup is never retried after ambiguous SSH failure', async () => {
  const { runtime, calls } = fixture({ intercept: args => args[0] === 'compose' ? sshEof() : undefined });
  await assert.rejects(runtime.up(false));
  assert.equal(matching(calls, 'compose').length, 1);
  const args = matching(calls, 'compose')[0].args;
  assert.ok(args.includes('up'));
  assert.equal(args[args.indexOf('--pull') + 1], 'never');
});

test('startup passes the run token and optional metrics profile without changing the selected runtime', async () => {
  const { runtime, calls } = fixture();
  await runtime.up(true);
  const call = matching(calls, 'compose')[0];
  assert.equal(call.args[call.args.indexOf('--profile') + 1], 'metrics');
  assert.equal(call.options.env.KCOPS_LAB_RUN_ID, runId);
  assert.equal(matching(calls, 'compose').length, 1);
});

for (const failure of [{ timedOut: true }, { aborted: true }, { outputExceeded: true }, { cleanupFailed: true }]) {
  test(`a ${Object.keys(failure)[0]} command is never retried even with SSH-looking stderr`, async () => {
    const { runtime, calls } = fixture({ intercept: () => ({ ...sshEof(), ...failure }) });
    await assert.rejects(runtime.preflight());
    assert.equal(calls.length, 1);
  });
}

test('a successful exit cannot override unconfirmed command process cleanup', async () => {
  const { runtime, calls } = fixture({ intercept: () => ({ ...result(), cleanupFailed: true }) });
  await assert.rejects(runtime.up(false));
  assert.equal(calls.length, 1);
});

test('an already-aborted operation starts no runtime command', async () => {
  const controller = new AbortController();
  controller.abort();
  const { runtime, calls } = fixture({ runtimeOptions: { signal: controller.signal } });
  await assert.rejects(runtime.preflight());
  assert.equal(calls.length, 0);
});

test('retry backoff consumes the shared deadline instead of renewing it', async () => {
  const { runtime, calls } = fixture({ intercept: () => sshEof(),
    runtimeOptions: { budgetMs: 5, retryDelayMs: 20, attempts: 10 } });
  await assert.rejects(runtime.preflight());
  assert.ok(calls.length <= 2, 'Retries must stop at the deadline, not continue for ten attempts');
  for (const call of calls) assert.ok(call.options.timeoutMs <= 5 && call.options.timeoutMs > 0);
});

test('a command success observed after the shared deadline is still rejected', async () => {
  const { runtime, calls } = fixture({ runtimeOptions: { budgetMs: 5 }, intercept: async () => {
    await new Promise(resolve => setTimeout(resolve, 20));
    return result();
  } });
  await assert.rejects(runtime.up(false));
  assert.equal(calls.length, 1);
});

test('a command success observed after cancellation is still rejected', async () => {
  const controller = new AbortController();
  const { runtime, calls } = fixture({ runtimeOptions: { signal: controller.signal }, intercept: () => {
    controller.abort();
    return result();
  } });
  await assert.rejects(runtime.up(false));
  assert.equal(calls.length, 1);
});

test('cleanup removes only verified full IDs, verifies absence, and never touches volumes/images', async () => {
  const { runtime, state, calls } = fixture({ containers: [container()], ownedNetwork: network() });
  await runtime.cleanup();
  assert.equal(state.containers.size, 0);
  assert.equal(state.network, null);
  assert.deepEqual(matching(calls, 'rm').map(call => call.args), [['rm', '-f', '--ignore', containerId]]);
  assert.deepEqual(matching(calls, 'network', 'rm').map(call => call.args), [['network', 'rm', networkId]]);
  assert.ok(matching(calls, 'ps').length >= 2, 'Successful deletion must be independently re-listed');
  assert.ok(matching(calls, 'network', 'exists').length >= 2, 'Network absence must be confirmed');
  assert.equal(calls.some(({ args }) => args.some(arg => ['--volumes', '-v', 'prune'].includes(arg))), false);
  assert.equal(calls.some(({ args }) => ['image', 'volume', 'system'].includes(args[0])), false);
});

test('partial startup failure remains rejected while separately scoped cleanup can succeed', async () => {
  const { runtime, state, calls } = fixture({ intercept: (args, current) => {
    if (args[0] !== 'compose') return undefined;
    current.containers.set(containerId, container());
    current.network = network();
    return result(125, '', 'Error: fixture failed after first container creation');
  } });
  await assert.rejects(runtime.up(false));
  assert.equal(state.containers.size, 1);
  await runtime.cleanup();
  assert.equal(state.containers.size, 0);
  assert.equal(state.network, null);
  assert.equal(matching(calls, 'compose').length, 1);
});

test('cleanup retries a transient exact-ID removal failure, not an entire Compose teardown', async () => {
  let attempts = 0;
  const { runtime, state, calls } = fixture({ containers: [container()], intercept: args =>
    args[0] === 'rm' && ++attempts === 1 ? sshEof() : undefined });
  await runtime.cleanup();
  assert.equal(state.containers.size, 0);
  assert.deepEqual(matching(calls, 'rm').map(call => call.args[3]), [containerId, containerId]);
  assert.equal(matching(calls, 'compose').length, 0);
});

test('cleanup tolerates lost container-removal acknowledgement only by retrying the same ID and verifying absence', async () => {
  let attempts = 0;
  const { runtime, state, calls } = fixture({ containers: [container()], intercept: (args, current) => {
    if (args[0] === 'rm' && ++attempts === 1) {
      current.containers.delete(containerId);
      return sshEof();
    }
    return undefined;
  } });
  await runtime.cleanup();
  assert.equal(state.containers.size, 0);
  assert.deepEqual(matching(calls, 'rm').map(call => call.args[3]), [containerId, containerId]);
  assert.ok(matching(calls, 'ps').length >= 2);
});

test('cleanup confirms network absence when its removal applied before an SSH EOF', async () => {
  let attempts = 0;
  const { runtime, state, calls } = fixture({ ownedNetwork: network(), intercept: (args, current) => {
    if (args[0] === 'network' && args[1] === 'rm' && ++attempts === 1) {
      current.network = null;
      return sshEof();
    }
    return undefined;
  } });
  await runtime.cleanup();
  assert.equal(state.network, null);
  assert.ok(matching(calls, 'network', 'exists').some(call => call.args[2] === networkId));
  assert.deepEqual(matching(calls, 'network', 'rm').map(call => call.args[2]), [networkId, networkId]);
});

test('cleanup preserves a replacement network and refuses an all-clean result', async () => {
  const { runtime, state, calls } = fixture({ ownedNetwork: network(), intercept: (args, current) => {
    if (args[0] === 'network' && args[1] === 'rm') {
      current.network = network({ id: replacementId, labels: labels({ [runLabel]: 'foreign-run' }) });
      return result(0, `${networkId}\n`);
    }
    return undefined;
  } });
  await assert.rejects(runtime.cleanup());
  assert.equal(state.network.id, replacementId);
  assert.deepEqual(matching(calls, 'network', 'rm').map(call => call.args[2]), [networkId]);
  assert.ok(matching(calls, 'network', 'exists').some(call => call.args[2] === networkId));
  assert.ok(matching(calls, 'network', 'exists').some(call => call.args[2] === networkName));
});

test('cleanup exhaustion is a failure and leaves the remaining resource visible', async () => {
  const { runtime, state, calls } = fixture({ containers: [container()], intercept: args =>
    args[0] === 'rm' ? sshEof() : undefined });
  await assert.rejects(runtime.cleanup());
  assert.equal(matching(calls, 'rm').length, 3);
  assert.equal(state.containers.size, 1);
});

for (const [description, replacement] of [
  ['changed run token', container({ Config: { Labels: labels({ [runLabel]: 'other-run' }) } })],
  ['wrong purpose', container({ Config: { Labels: labels({ [purposeLabel]: 'unrelated' }) } })],
  ['wrong project', container({ Config: { Labels: labels({ 'com.docker.compose.project': 'other' }) } })],
  ['unexpected name', container({ Name: 'unrelated-postgres' })],
  ['replaced full ID', container({ Id: replacementId, ID: replacementId })],
]) {
  test(`cleanup preserves a container with ${description}`, async () => {
    const { runtime, state, calls } = fixture({ containers: [container()], intercept: args =>
      args[0] === 'container' && args[1] === 'inspect' ? json([replacement]) : undefined });
    await assert.rejects(runtime.cleanup());
    assert.equal(matching(calls, 'rm').length, 0);
    assert.equal(state.containers.size, 1);
  });
}

test('cleanup rejects conflicting Compose ownership labels', async () => {
  const conflicted = container({ Config: { Labels: labels({ 'io.podman.compose.project': 'other-project' }) } });
  const { runtime, calls } = fixture({ containers: [conflicted] });
  await assert.rejects(runtime.cleanup());
  assert.equal(matching(calls, 'rm').length, 0);
});

test('cleanup accepts the Podman Compose project label without requiring the Docker variant', async () => {
  const selectedLabels = labels({ 'io.podman.compose.project': project });
  delete selectedLabels['com.docker.compose.project'];
  const { runtime, state } = fixture({ containers: [container({ Config: { Labels: selectedLabels } })],
    ownedNetwork: network({ labels: selectedLabels }) });
  await runtime.cleanup();
  assert.equal(state.containers.size, 0);
  assert.equal(state.network, null);
});

test('cleanup validates the entire observed container set before deleting the first resource', async () => {
  const invalid = container({ Id: replacementId, ID: replacementId, Name: names[1],
    Config: { Labels: labels({ [purposeLabel]: 'other-purpose' }) } });
  const { runtime, state, calls } = fixture({ containers: [container(), invalid] });
  await assert.rejects(runtime.cleanup());
  assert.equal(matching(calls, 'rm').length, 0);
  assert.equal(state.containers.size, 2);
});

test('cleanup revalidates ownership immediately before deleting an inspected ID', async () => {
  let inspections = 0;
  const { runtime, calls } = fixture({ containers: [container()], intercept: args => {
    if (args[0] === 'container' && args[1] === 'inspect' && ++inspections === 2) {
      return json([container({ Config: { Labels: labels({ [runLabel]: 'changed-run' }) } })]);
    }
    return undefined;
  } });
  await assert.rejects(runtime.cleanup());
  assert.equal(inspections, 2);
  assert.equal(matching(calls, 'rm').length, 0);
});

for (const [description, candidate] of [
  ['wrong run token', network({ labels: labels({ [runLabel]: 'other-run' }) })],
  ['wrong purpose', network({ labels: labels({ [purposeLabel]: 'unrelated' }) })],
  ['wrong project', network({ labels: labels({ 'com.docker.compose.project': 'other' }) })],
  ['conflicting project labels', network({ labels: labels({ 'io.podman.compose.project': 'other' }) })],
  ['unexpected name', network({ name: 'foreign-network' })],
  ['foreign attachment', network({ containers: { [replacementId]: { name: 'foreign-container' } } })],
]) {
  test(`cleanup preserves network with ${description}`, async () => {
    const { runtime, state, calls } = fixture({ ownedNetwork: network(), intercept: args =>
      args[0] === 'network' && args[1] === 'inspect' ? json([candidate]) : undefined });
    await assert.rejects(runtime.cleanup());
    assert.equal(matching(calls, 'network', 'rm').length, 0);
    assert.ok(state.network);
  });
}

for (const output of ['not JSON', '{}', 'null', '[null]']) {
  test(`invalid container-list output fails closed: ${output}`, async () => {
    const { runtime, calls } = fixture({ intercept: args => args[0] === 'ps' ? result(0, output) : undefined });
    await assert.rejects(runtime.cleanup());
    assert.equal(mutations(calls).length, 0);
  });
}

for (const [description, output] of [
  ['duplicate IDs', [{ Id: containerId }, { Id: containerId }]],
  ['short ID', [{ Id: containerId.slice(0, 12) }]],
  ['name instead of ID', [{ Id: names[0] }]],
  ['missing ID', [{}]],
  ['too many resources', Array.from({ length: 6 }, (_, index) => ({ Id: String(index).repeat(64) }))],
]) {
  test(`invalid container inventory (${description}) authorizes no inspection or deletion`, async () => {
    const { runtime, calls } = fixture({ intercept: args => args[0] === 'ps' ? json(output) : undefined });
    await assert.rejects(runtime.cleanup());
    assert.equal(matching(calls, 'container', 'inspect').length, 0);
    assert.equal(mutations(calls).length, 0);
  });
}

for (const selected of ['container', 'network']) {
  test(`malformed ${selected} inspection cannot authorize deletion`, async () => {
    const { runtime, calls } = fixture({ containers: selected === 'container' ? [container()] : [],
      ownedNetwork: selected === 'network' ? network() : null,
      intercept: args => args[0] === selected && args[1] === 'inspect' ? result(0, 'not JSON') : undefined });
    await assert.rejects(runtime.cleanup());
    assert.equal(mutations(calls).length, 0);
  });
}

test('successful removal command without confirmed absence still fails cleanup', async () => {
  const { runtime, state } = fixture({ containers: [container()], intercept: args =>
    args[0] === 'rm' ? result(0, `${containerId}\n`) : undefined });
  await assert.rejects(runtime.cleanup());
  assert.equal(state.containers.size, 1);
});

test('cleanup never interprets network existence transport failure as absence', async () => {
  const { runtime, calls } = fixture({ intercept: args => args[0] === 'network' && args[1] === 'exists'
    ? result(125, '', 'Error: permission denied') : undefined });
  await assert.rejects(runtime.cleanup());
  assert.equal(mutations(calls).length, 0);
});

for (const field of ['Id', 'ID']) {
  test(`fixture database accepts one owned tmpfs instance with ${field} and returns its full ID`, () => {
    const item = container({ Name: '/kcops-identity-postgres' });
    delete item[field === 'Id' ? 'ID' : 'Id'];
    assert.equal(validateFixtureDatabase(context, [item]), containerId);
  });
}

for (const [description, item] of [
  ['foreign run', container({ Config: { Labels: labels({ [runLabel]: 'foreign-run' }) } })],
  ['wrong purpose', container({ Config: { Labels: labels({ [purposeLabel]: 'other' }) } })],
  ['wrong project', container({ Config: { Labels: labels({ 'com.docker.compose.project': 'other' }) } })],
  ['conflicting projects', container({ Config: { Labels: labels({ 'io.podman.compose.project': 'other' }) } })],
  ['wrong name', container({ Name: 'reusable-production-postgres' })],
  ['short ID', container({ Id: containerId.slice(0, 12), ID: containerId.slice(0, 12) })],
  ['missing ID', container({ Id: undefined, ID: undefined })],
  ['non-hex ID', container({ Id: 'z'.repeat(64), ID: 'z'.repeat(64) })],
  ['missing tmpfs', container({ HostConfig: {} })],
  ['different tmpfs path', container({ HostConfig: { Tmpfs: { '/other/path': 'rw' } } })],
  ['null tmpfs map', container({ HostConfig: { Tmpfs: null } })],
]) {
  test(`fixture database refuses ${description} before authorizing SQL`, () => {
    assert.throws(() => validateFixtureDatabase(context, [item]));
  });
}

for (const [description, entries] of [
  ['object', container()], ['null', null], ['empty list', []], ['null entry', [null]],
  ['multiple entries', [container(), container()]], ['missing entries', undefined],
]) {
  test(`fixture database refuses an invalid ${description} inspection envelope`, () => {
    assert.throws(() => validateFixtureDatabase(context, entries));
  });
}

test('fixture database refuses a remote runtime even when the resource labels match', () => {
  assert.throws(() => validateFixtureDatabase({ ...context,
    uri: 'ssh://root@remote.example/run/podman/podman.sock' }, [container()]));
});

for (const [firstSignal, secondSignal, expectedCode] of [
  ['SIGINT', 'SIGTERM', 130], ['SIGTERM', 'SIGTERM', 143],
]) {
  test(`CLI preserves ${firstSignal} after another ${secondSignal} while cleaning an uncooperative child`,
    { timeout: 6_000 }, async t => {
      const directory = await mkdtemp(join(tmpdir(), 'kcops-runtime-signal-test-'));
      const marker = join(directory, 'fake-podman-ready.json');
      const fakePodman = join(directory, 'podman');
      const contextFile = join(directory, 'context.json');
      const runtimeFile = fileURLToPath(new URL('./identity-lab-runtime.mjs', import.meta.url));
      const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
      const exists = pid => {
        try { process.kill(pid, 0); return true; }
        catch (error) { if (error.code === 'ESRCH') return false; throw error; }
      };
      let cli;
      let childPid;
      let completion;
      t.after(async () => {
        // Only processes created by this test are eligible for emergency cleanup.
        if (childPid && exists(-childPid)) process.kill(-childPid, 'SIGKILL');
        if (cli && cli.exitCode === null && cli.signalCode === null) cli.kill('SIGKILL');
        if (completion) await completion;
        await rm(directory, { recursive: true, force: true });
      });
      await writeFile(fakePodman, `#!/usr/bin/env node
const fs = require('node:fs');
process.on('SIGTERM', () => {});
process.on('SIGINT', () => {});
fs.writeFileSync(${JSON.stringify(marker + '.pending')}, JSON.stringify({ pid: process.pid, args: process.argv.slice(2) }));
fs.renameSync(${JSON.stringify(marker + '.pending')}, ${JSON.stringify(marker)});
setInterval(() => {}, 1000);
`, { mode: 0o700 });
      cli = spawn(process.execPath, [runtimeFile, 'init', contextFile], {
        env: { ...process.env, PATH: `${directory}:${dirname(process.execPath)}:/usr/bin:/bin` },
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      cli.stdout.resume();
      let diagnostic = '';
      cli.stderr.on('data', chunk => { diagnostic += chunk.toString(); });
      completion = once(cli, 'close');
      const readinessDeadline = performance.now() + 1_500;
      while (performance.now() < readinessDeadline) {
        try {
          const ready = JSON.parse(await readFile(marker, 'utf8'));
          assert.deepEqual(ready.args, ['system', 'connection', 'list', '--format', 'json']);
          childPid = ready.pid;
          break;
        } catch (error) {
          if (error.code !== 'ENOENT') throw error;
        }
        await delay(10);
      }
      assert.ok(Number.isSafeInteger(childPid) && childPid > 0, 'Fake Podman must install its handlers before signaling');
      const started = performance.now();
      assert.equal(cli.kill(firstSignal), true);
      await delay(50);
      assert.equal(cli.kill(secondSignal), true, 'Supervisor must stay alive to finish the owned child cleanup');
      const [code, signal] = await completion;
      const childStillExists = exists(childPid);
      const groupStillExists = exists(-childPid);
      assert.equal(code, expectedCode, `The first interruption controls the final exit status; signal=${signal}; `
        + `childPresent=${childStillExists}; groupPresent=${groupStillExists}; diagnostic=${diagnostic.trim()}`);
      assert.equal(signal, null, 'The second signal must not terminate the supervisor directly');
      assert.equal(childStillExists, false, 'The TERM-ignoring child must be gone');
      assert.equal(groupStillExists, false, 'The owned process group must be gone');
      assert.ok(performance.now() - started < 5_000, 'Cleanup must remain bounded');
      await assert.rejects(readFile(contextFile), { code: 'ENOENT' });
    });
}
