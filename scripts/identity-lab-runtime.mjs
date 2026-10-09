// Local test harness only. Never exposed through REST/MCP or used for discovery.
import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { runCommand } from './lab-process.mjs';

const project = 'keycloak-operations-identity-lab';
const network = `${project}_default`;
const runLabel = 'io.github.keycloak-operations.run-id';
const purposeLabel = 'io.github.keycloak-operations.purpose';
const names = new Set(['postgres', 'idp', 'target-a', 'target-b', 'prometheus'].map(n => `kcops-identity-${n}`));
const fullId = /^[a-f0-9]{64}$/;
const transient = /ssh: handshake failed: EOF|ssh: handshake failed:.*connection reset by peer|connection reset by peer|broken pipe/i;
const composeFile = fileURLToPath(new URL('../dev/identity-lab/compose.yaml', import.meta.url));

export function validateRuntimeContext(context) {
  let endpoint;
  try { endpoint = new URL(context.uri); } catch { throw new Error('Invalid local runtime connection.'); }
  if (endpoint.protocol !== 'ssh:' || !['127.0.0.1', '[::1]', 'localhost'].includes(endpoint.hostname)
      || endpoint.password || endpoint.search || endpoint.hash || !endpoint.pathname.endsWith('/podman.sock')
      || !context.identity?.startsWith('/') || !/^[a-f0-9-]{36}$/.test(context.runId)) {
    throw new Error('Only an explicitly selected local Podman machine connection is allowed.');
  }
}

function owned(context, labels) {
  const projects = ['com.docker.compose.project', 'io.podman.compose.project'].filter(key => labels?.[key] !== undefined);
  return labels?.[runLabel] === context.runId && labels?.[purposeLabel] === 'identity-validation'
    && projects.length > 0 && projects.every(key => labels[key] === project);
}

export function validateFixtureDatabase(context, entries) {
  validateRuntimeContext(context);
  const item = Array.isArray(entries) && entries.length === 1 ? entries[0] : null;
  const id = item?.Id ?? item?.ID;
  if (!item || !fullId.test(id) || item.Name?.replace(/^\//, '') !== 'kcops-identity-postgres'
      || !owned(context, item.Config?.Labels)
      || !Object.hasOwn(item.HostConfig?.Tmpfs ?? {}, '/var/lib/postgresql/data')) {
    throw new Error('Fixture database ownership/identity/tmpfs is unverified.');
  }
  return id;
}

export function createRuntime(context, { command = runCommand, timeoutMs = 30_000, budgetMs = 180_000,
  retryDelayMs = 1_000, attempts = 3, log = message => console.error(message), signal } = {}) {
  validateRuntimeContext(context);
  const deadline = performance.now() + budgetMs;
  const prefix = ['--url', context.uri, '--identity', context.identity];
  const remaining = () => {
    const value = Math.floor(deadline - performance.now());
    if (signal?.aborted || value <= 0) throw new Error('Local runtime operation interrupted or deadline exceeded.');
    return value;
  };
  async function podman(args, { retry = true, allowed = [0], stream = false, limit = timeoutMs } = {}) {
    for (let attempt = 1; attempt <= attempts; attempt++) {
      const result = await command('podman', [...prefix, ...args], {
        timeoutMs: Math.min(limit, remaining()), capture: !stream, signal,
        env: { ...process.env, KCOPS_LAB_RUN_ID: context.runId },
      });
      if (result.cleanupFailed) {
        throw Object.assign(new Error('Podman process termination is unconfirmed; retain the lab lock.'), { cleanupFailed: true });
      }
      remaining();
      if (!result.timedOut && !result.aborted && !result.outputExceeded && allowed.includes(result.code)) return result;
      if (!retry || result.timedOut || result.aborted || result.outputExceeded || attempt === attempts
          || !transient.test(result.stderr ?? '')) {
        throw new Error(`Podman ${args[0]} failed (exit ${result.code}); cleanup/state must be verified.`);
      }
      log(`Transient Podman transport failure; retry ${attempt + 1}/${attempts} for ${args[0]}.`);
      const delay = Math.min(retryDelayMs, remaining());
      await new Promise(resolve => setTimeout(resolve, delay));
    }
  }
  const json = result => {
    try { return JSON.parse(result.stdout); } catch { throw new Error('Invalid Podman inventory; refusing cleanup.'); }
  };
  const exists = async (kind, name) => (await podman([kind, 'exists', name], { allowed: [0, 1] })).code === 0;
  async function inventory() {
    const rows = json(await podman(['ps', '-a', '--filter', `label=${runLabel}=${context.runId}`, '--format', 'json']));
    if (!Array.isArray(rows) || rows.length > names.size) throw new Error('Unexpected owned container inventory; refusing cleanup.');
    const ids = rows.map(row => row.Id ?? row.ID);
    if (ids.some(id => !fullId.test(id)) || new Set(ids).size !== ids.length) throw new Error('Invalid container IDs; refusing cleanup.');
    return ids;
  }
  async function inspectContainer(id) {
    const data = json(await podman(['container', 'inspect', id]));
    if (!Array.isArray(data) || data.length !== 1 || (data[0].Id ?? data[0].ID) !== id
        || !names.has(data[0].Name?.replace(/^\//, '')) || !owned(context, data[0].Config?.Labels)) {
      throw new Error('Container ownership is unverified; preserving resources.');
    }
    return data[0];
  }
  return {
    async preflight() {
      await podman(['info', '--format', 'json']);
      if (await exists('network', network)) throw new Error('Refusing to reuse an existing identity-lab network.');
      // Reserve all names, including the optional metrics service, in either mode.
      for (const name of names) if (await exists('container', name)) throw new Error(`Refusing to reuse existing container: ${name}`);
    },
    async up(metrics = false) {
      // Never retry a create: a failed/timed-out request may already have applied.
      await podman(['compose', '-f', composeFile, ...(metrics ? ['--profile', 'metrics'] : []), 'up', '-d', '--pull', 'never'],
        { retry: false, stream: true, limit: 120_000 });
    },
    async cleanup() {
      const ids = await inventory();
      // Validate the complete observed set before any destructive operation.
      for (const id of ids) await inspectContainer(id);
      for (const id of ids) {
        // Revalidate before removal and retry only this immutable ID. No volume flag.
        await inspectContainer(id);
        await podman(['rm', '-f', '--ignore', id]);
      }
      if ((await inventory()).length) throw new Error('Owned containers remain; preserving the lab lock.');
      if (await exists('network', network)) {
        const data = json(await podman(['network', 'inspect', network]));
        const value = Array.isArray(data) && data.length === 1 ? data[0] : null;
        if (!value || value.name !== network || !fullId.test(value.id) || !owned(context, value.labels)
            || !value.containers || typeof value.containers !== 'object' || Array.isArray(value.containers)
            || Object.keys(value.containers).length) {
          throw new Error('Network ownership or emptiness is unverified; preserving it and the lab lock.');
        }
        // No network --ignore in Podman. Exit 1 can mean an already removed ID
        // after an applied-but-disconnected request; require both absence reads.
        const removal = await podman(['network', 'rm', value.id], { allowed: [0, 1] });
        if (await exists('network', value.id) || await exists('network', network)) {
          throw new Error('Lab network still exists; preserving the lab lock.');
        }
        if (removal.code !== 0) log('Network removal returned nonzero; exact ID and expected name both verified absent.');
      }
      log(`Owned cleanup verified for run ${context.runId}; no images or volumes removed.`);
    },
  };
}

async function main() {
  const [operation, contextFile, mode] = process.argv.slice(2);
  if (!['init', 'preflight', 'up', 'cleanup'].includes(operation) || !contextFile) throw new Error('Invalid lab runtime command.');
  const controller = new AbortController();
  let exitCode = 1;
  const interrupt = signal => {
    if (controller.signal.aborted) return;
    exitCode = signal === 'SIGINT' ? 130 : 143;
    controller.abort(signal);
  };
  const onInterrupt = () => interrupt('SIGINT');
  const onTerminate = () => interrupt('SIGTERM');
  process.on('SIGINT', onInterrupt); process.on('SIGTERM', onTerminate);
  try {
    if (operation === 'init') {
      const connection = process.env.IDENTITY_LAB_PODMAN_CONNECTION || 'podman-machine-default-root';
      const result = await runCommand('podman', ['system', 'connection', 'list', '--format', 'json'],
        { timeoutMs: 10_000, capture: true, signal: controller.signal });
      if (result.code !== 0) throw Object.assign(new Error('Cannot inspect the selected local Podman connection.'), { cleanupFailed: result.cleanupFailed });
      const selected = JSON.parse(result.stdout).filter(item => item.Name === connection && item.IsMachine === true);
      if (selected.length !== 1) throw new Error('Selected local Podman machine connection was not found.');
      const context = { runId: randomUUID(), connection, uri: selected[0].URI, identity: selected[0].Identity };
      validateRuntimeContext(context);
      await writeFile(contextFile, JSON.stringify(context, null, 2) + '\n', { mode: 0o600, flag: 'wx' });
      console.log(`Local lab run ${context.runId}; selected connection ${connection}.`);
    } else {
      const context = JSON.parse(await readFile(contextFile, 'utf8'));
      const runtime = createRuntime(context, { signal: controller.signal });
      await runtime[operation](mode === 'metrics');
    }
  } catch (error) {
    // Messages above are fixed harness diagnostics; never print provider bodies.
    console.error(error instanceof SyntaxError ? 'Invalid local runtime context.' : error.message);
    process.exitCode = error.cleanupFailed ? 125 : exitCode;
  } finally {
    process.removeListener('SIGINT', onInterrupt); process.removeListener('SIGTERM', onTerminate);
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
