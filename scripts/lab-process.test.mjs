import test from 'node:test';
import assert from 'node:assert/strict';
import { ChildProcess, spawn } from 'node:child_process';
import { once } from 'node:events';
import http from 'node:http';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';
import { runCommand, waitForUrl } from './lab-process.mjs';

const script = fileURLToPath(new URL('./lab-process.mjs', import.meta.url));
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const command = (code, options = {}) => runCommand(process.execPath, ['-e', code], { capture: true, timeoutMs: 3_000, graceMs: 80, ...options });
function exists(pid) {
  try { process.kill(pid, 0); return true; } catch (error) { if (error.code === 'ESRCH') return false; throw error; }
}

async function temporary(t) {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'kcops-process-test-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  return dir;
}

async function fileReady(file) {
  const deadline = performance.now() + 3_000;
  while (performance.now() < deadline) {
    try { const value = (await readFile(file, 'utf8')).trim(); if (value) return value; }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    await sleep(10);
  }
  throw new Error('Test child did not become ready');
}

async function server(t, handler) {
  const instance = http.createServer(handler);
  instance.listen(0, '127.0.0.1');
  await once(instance, 'listening');
  t.after(() => new Promise((resolve) => {
    instance.closeAllConnections();
    instance.close(resolve);
  }));
  return { instance, url: `http://127.0.0.1:${instance.address().port}/ready` };
}

test('preserves normal exit status and separate captured streams', async () => {
  const result = await command('process.stdout.write("okay"); process.stderr.write("notice"); process.exitCode = 7');
  assert.deepEqual(result, { code: 7, stdout: 'okay', stderr: 'notice', timedOut: false, aborted: false, outputExceeded: false, cleanupFailed: false });
});

test('supports explicit child environment without mutating its parent', async () => {
  const result = await command('process.stdout.write(process.env.KCOPS_TEST_SCOPE)', { env: { ...process.env, KCOPS_TEST_SCOPE: 'isolated' } });
  assert.equal(result.stdout, 'isolated');
  assert.equal(process.env.KCOPS_TEST_SCOPE, undefined);
});

test('spawn failure is nonzero without disclosing command arguments', async () => {
  const result = await runCommand('/nonexistent/kcops-test-command', ['secret-value'], { capture: true, timeoutMs: 100 });
  assert.equal(result.code, 127);
  assert.equal(result.stdout + result.stderr, '');
});

test('timeout terminates a gracefully exiting owned child', async () => {
  const started = performance.now();
  const result = await command('process.on("SIGTERM", () => process.exit(0)); setInterval(() => {}, 1000)', { timeoutMs: 150 });
  assert.equal(result.code, 124);
  assert.equal(result.timedOut, true);
  assert.equal(result.cleanupFailed, false);
  assert.ok(performance.now() - started < 1_500);
});

test('timeout escalates from TERM to KILL for an uncooperative child', async (t) => {
  const file = path.join(await temporary(t), 'pid');
  const result = await command(`require('node:fs').writeFileSync(${JSON.stringify(file)}, String(process.pid)); process.on('SIGTERM', () => {}); setInterval(() => {}, 1000)`, { timeoutMs: 200 });
  const pid = Number(await fileReady(file));
  assert.equal(result.code, 124);
  assert.equal(result.cleanupFailed, false);
  assert.equal(exists(pid), false);
});

test('normal parent exit still removes its lingering descendant', async (t) => {
  const file = path.join(await temporary(t), 'pid');
  const descendant = `require('node:fs').writeFileSync(${JSON.stringify(file)}, String(process.pid)); process.on('SIGTERM', () => {}); setInterval(() => {}, 1000)`;
  const resultPromise = command(`const {spawn}=require('node:child_process'); const child=spawn(process.execPath,['-e',${JSON.stringify(descendant)}],{stdio:'ignore'}); child.unref(); setTimeout(()=>process.exit(0),150)`);
  const pid = Number(await fileReady(file));
  const result = await resultPromise;
  assert.equal(result.code, 0);
  assert.equal(result.cleanupFailed, false);
  assert.equal(exists(pid), false);
});

test('timeout removes both parent and descendant in the owned group', async (t) => {
  const file = path.join(await temporary(t), 'pid');
  const descendant = `require('node:fs').writeFileSync(${JSON.stringify(file)}, String(process.pid)); process.on('SIGTERM', () => {}); setInterval(() => {}, 1000)`;
  const resultPromise = command(`require('node:child_process').spawn(process.execPath,['-e',${JSON.stringify(descendant)}],{stdio:'ignore'}); process.on('SIGTERM',()=>{}); setInterval(()=>{},1000)`, { timeoutMs: 250 });
  const pid = Number(await fileReady(file));
  const result = await resultPromise;
  assert.equal(result.code, 124);
  assert.equal(result.cleanupFailed, false);
  assert.equal(exists(pid), false);
});

test('verification allowance starts after actual KILL despite a stalled event loop', async (t) => {
  const file = path.join(await temporary(t), 'pid');
  const realKill = process.kill.bind(process);
  const controller = new AbortController();
  let killedAt;
  t.mock.method(process, 'kill', (pid, signal) => {
    if (pid < 0 && signal === 'SIGKILL') killedAt = performance.now();
    // Model a short OS reaping delay, including when the actual process has
    // already exited. This must not turn a confirmed interruption into exit125.
    if (pid < 0 && signal === 0 && killedAt !== undefined && performance.now() - killedAt < 100) return true;
    return realKill(pid, signal);
  });
  const resultPromise = command(`process.on('SIGTERM', () => {}); require('node:fs').writeFileSync(${JSON.stringify(file)}, String(process.pid)); setInterval(() => {}, 1000)`,
    { signal: controller.signal });
  const pid = Number(await fileReady(file));
  t.after(() => { if (exists(pid)) realKill(-pid, 'SIGKILL'); });
  controller.abort('SIGTERM');
  await new Promise(resolve => setTimeout(() => {
    // Both an 80ms grace timer and the former grace+250ms verification timer
    // would be overdue when this callback releases the event loop.
    const end = performance.now() + 400;
    while (performance.now() < end) { /* Deliberate local scheduling stall. */ }
    resolve();
  }, 10));
  const result = await resultPromise;
  assert.notEqual(killedAt, undefined);
  assert.equal(result.code, 143);
  assert.equal(result.aborted, true);
  assert.equal(result.cleanupFailed, false);
  assert.equal(exists(pid), false);
});

test('capture limit is shared across stdout and stderr and fails closed', async () => {
  const result = await command('process.stdout.write("a".repeat(800)); process.stderr.write("b".repeat(800)); setInterval(()=>{},1000)', { maxOutputBytes: 1_000 });
  assert.equal(result.code, 125);
  assert.equal(result.outputExceeded, true);
  assert.ok(Buffer.byteLength(result.stdout) + Buffer.byteLength(result.stderr) <= 1_000);
});

test('already aborted commands never spawn', async () => {
  const controller = new AbortController();
  controller.abort('SIGTERM');
  const result = await command('throw new Error("must not run")', { signal: controller.signal });
  assert.equal(result.code, 143);
  assert.equal(result.aborted, true);
  assert.equal(result.stderr, '');
});

test('API cancellation removes the child and preserves interruption status', async () => {
  const controller = new AbortController();
  const resultPromise = command('setInterval(()=>{},1000)', { signal: controller.signal });
  setTimeout(() => controller.abort('SIGINT'), 100);
  const result = await resultPromise;
  assert.equal(result.code, 130);
  assert.equal(result.aborted, true);
  assert.equal(result.cleanupFailed, false);
});

for (const operation of ['probe', 'signal']) {
  test(`transient ${operation} denial does not override subsequently verified group absence`, async t => {
    const realKill = process.kill.bind(process);
    let denied = false;
    t.mock.method(process, 'kill', (pid, signal) => {
      if (pid < 0 && !denied && signal === (operation === 'probe' ? 0 : 'SIGTERM')) {
        denied = true;
        throw Object.assign(new Error('temporary reaping denial'), { code: 'EPERM' });
      }
      return realKill(pid, signal);
    });
    const result = await command('process.exit(7)');
    assert.equal(denied, true);
    assert.equal(result.code, 7);
    assert.equal(result.cleanupFailed, false);
  });
}

test('unverifiable group cleanup overrides a successful exit and remains bounded', async (t) => {
  const realKill = process.kill.bind(process);
  const realUnref = ChildProcess.prototype.unref;
  let unreferenced = 0;
  t.mock.method(ChildProcess.prototype, 'unref', function () { unreferenced++; return realUnref.call(this); });
  t.mock.method(process, 'kill', (pid, signal) => {
    if (pid < 0) throw Object.assign(new Error('test denial'), { code: 'EPERM' });
    return realKill(pid, signal);
  });
  const started = performance.now();
  // This child exits by itself; the injected denial never leaves a live fixture.
  const result = await command('process.exit(0)', { graceMs: 10 });
  assert.equal(result.code, 125);
  assert.equal(result.cleanupFailed, true);
  assert.equal(unreferenced, 1);
  assert.ok(performance.now() - started < 2_000);
});

for (const [signal, expected] of [['SIGINT', 130], ['SIGTERM', 143]]) {
  test(`CLI forwards ${signal} and waits for owned group cleanup`, async (t) => {
    const file = path.join(await temporary(t), 'pid');
    const cli = spawn(process.execPath, [script, '5000', process.execPath, '-e', `require('node:fs').writeFileSync(${JSON.stringify(file)},String(process.pid)); setInterval(()=>{},1000)`], { stdio: 'pipe' });
    const completion = once(cli, 'close');
    t.after(() => { if (cli.exitCode === null) cli.kill('SIGTERM'); });
    const pid = Number(await fileReady(file));
    cli.kill(signal);
    const [code] = await completion;
    assert.equal(code, expected);
    assert.equal(exists(pid), false);
  });
}

test('CLI inherits stdin and normal nonzero status', async () => {
  const cli = spawn(process.execPath, [script, '3000', process.execPath, '-e', 'process.stdin.once("data",data=>{process.stdout.write(data);process.exitCode=9;process.stdin.destroy()})'], { stdio: 'pipe' });
  const output = [];
  cli.stdout.on('data', (chunk) => output.push(chunk));
  const completion = once(cli, 'close');
  cli.stdin.end('hello');
  const [code] = await completion;
  assert.equal(code, 9);
  assert.equal(Buffer.concat(output).toString(), 'hello');
});

test('invalid limits reject before a subprocess can run', () => {
  for (const timeoutMs of [0, -1, Infinity, NaN, 1.1]) {
    assert.throws(() => command('process.exit(0)', { timeoutMs }), /Invalid local runner limit/);
  }
});

test('readiness accepts 2xx without waiting for or retaining the body', async (t) => {
  const { url } = await server(t, (_request, response) => { response.writeHead(204); response.flushHeaders(); });
  assert.deepEqual(await waitForUrl(url, { timeoutMs: 300 }), { ready: true, reason: 'ready' });
});

test('readiness bounds stalled response headers by the overall deadline', async (t) => {
  let requests = 0;
  const { url } = await server(t, () => { requests++; });
  const started = performance.now();
  assert.deepEqual(await waitForUrl(url, { timeoutMs: 180, requestTimeoutMs: 50, intervalMs: 10 }), { ready: false, reason: 'timeout' });
  assert.ok(requests >= 2);
  assert.ok(performance.now() - started < 1_000);
});

test('readiness retries non-success statuses until success', async (t) => {
  let requests = 0;
  const { url } = await server(t, (_request, response) => { response.writeHead(++requests < 3 ? 503 : 200); response.end(); });
  assert.equal((await waitForUrl(url, { timeoutMs: 500, intervalMs: 10 })).ready, true);
  assert.equal(requests, 3);
});

test('readiness never follows redirects', async (t) => {
  let followed = 0;
  const { url } = await server(t, (request, response) => {
    if (request.url === '/destination') followed++;
    response.writeHead(302, { Location: '/destination' }); response.end();
  });
  assert.equal((await waitForUrl(url, { timeoutMs: 80, intervalMs: 10 })).reason, 'timeout');
  assert.equal(followed, 0);
});

test('readiness rejects non-loopback URLs, credentials and fragments before I/O', async () => {
  for (const url of ['https://localhost/', 'http://example.com/', 'http://127.0.0.2/', 'file:///etc/hosts', 'http://secret@localhost/', 'http://localhost/#secret', 'not a URL']) {
    assert.deepEqual(await waitForUrl(url), { ready: false, reason: 'invalid-url' });
  }
});

test('readiness checks monitored process before accepting a successful response', async (t) => {
  let requests = 0;
  const { url } = await server(t, (_request, response) => { requests++; response.end(); });
  const child = spawn(process.execPath, ['-e', 'process.exit(0)']);
  await once(child, 'close');
  assert.deepEqual(await waitForUrl(url, { pid: child.pid }), { ready: false, reason: 'process-exited' });
  assert.equal(requests, 0);
});

test('readiness abort cancels an in-flight request promptly', async (t) => {
  const controller = new AbortController();
  const { url } = await server(t, () => {});
  const promise = waitForUrl(url, { timeoutMs: 5_000, signal: controller.signal });
  setTimeout(() => controller.abort(), 50);
  const started = performance.now();
  assert.deepEqual(await promise, { ready: false, reason: 'aborted' });
  assert.ok(performance.now() - started < 1_000);
});

test('readiness CLI failure omits URL and exception details', async () => {
  const result = await runCommand(process.execPath, [script, 'wait-url', '100', 'http://secret-user:secret-password@example.com/?token=secret'], { capture: true });
  assert.equal(result.code, 1);
  assert.equal(result.stderr, 'Local readiness failed: invalid-url.\n');
  assert.equal(result.stdout, '');
});
