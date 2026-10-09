#!/usr/bin/env node
// Local test harness only: this is not an application/API command execution tool.
import { spawn } from 'node:child_process';
import http from 'node:http';
import { pathToFileURL } from 'node:url';
import { performance } from 'node:perf_hooks';

function boundedInteger(value, minimum, maximum) {
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new TypeError('Invalid local runner limit');
  }
  return value;
}

function interruptionCode(signal) {
  return signal?.reason === 'SIGTERM' ? 143 : 130;
}

/** Supervise only the process group created by this call; never discover/kill peers. */
export function runCommand(command, args, {
  timeoutMs = 30_000, graceMs = 2_000, capture = false,
  maxOutputBytes = 1_048_576, signal, env = process.env,
} = {}) {
  boundedInteger(timeoutMs, 1, 86_400_000);
  boundedInteger(graceMs, 0, 60_000);
  boundedInteger(maxOutputBytes, 1, 16_777_216);
  if (typeof command !== 'string' || !command || !Array.isArray(args)
      || !args.every((arg) => typeof arg === 'string')) {
    throw new TypeError('Invalid local runner command');
  }
  const result = { code: 1, stdout: '', stderr: '', timedOut: false, aborted: false, outputExceeded: false, cleanupFailed: false };
  if (signal?.aborted) return Promise.resolve({ ...result, code: interruptionCode(signal), aborted: true });
  // These harnesses require POSIX groups. Failing closed is safer than a partial tree kill.
  if (process.platform === 'win32') return Promise.resolve(result);

  return new Promise((resolve) => {
    const started = performance.now();
    let child;
    let settled = false;
    let cleaning = false;
    let cleanupDone = false;
    let exited = false;
    let closed = false;
    let cleanupFailed = false;
    let exitCode = 1;
    let totalBytes = 0;
    const stdout = [];
    const stderr = [];
    let deadlineTimer;
    let graceTimer;
    let pollTimer;
    let drainTimer;

    function groupExists() {
      if (!child?.pid) return false;
      try { process.kill(-child.pid, 0); return true; }
      // A denied/failed probe is unknown (keep waiting), never absence. Some
      // hosts briefly return EPERM while reaping, then ESRCH. Only the final
      // bounded observation decides whether cleanup remains unconfirmed.
      catch (error) { return error.code !== 'ESRCH'; }
    }

    function killGroup(name) {
      if (!child?.pid) return;
      try { process.kill(-child.pid, name); }
      // Signal acknowledgement is not proof of termination. Whether signaling
      // succeeds or fails, require an independent group-absence observation.
      catch { /* The bounded probe/escalation path verifies the outcome. */ }
    }

    function finish(force = false) {
      if (settled || !cleanupDone || (!closed && !force)) return;
      settled = true;
      clearTimeout(deadlineTimer);
      clearTimeout(graceTimer);
      clearInterval(pollTimer);
      clearTimeout(drainTimer);
      signal?.removeEventListener('abort', onAbort);
      child?.stdout?.destroy();
      child?.stderr?.destroy();
      // An unkillable OS process must not keep the supervisor alive after its
      // bounded, explicitly failed cleanup. The caller retains its ownership lock.
      if (force || cleanupFailed) child?.unref();
      result.stdout = Buffer.concat(stdout).toString('utf8');
      result.stderr = Buffer.concat(stderr).toString('utf8');
      result.cleanupFailed = cleanupFailed;
      result.code = cleanupFailed ? 125 : result.aborted ? interruptionCode(signal)
        : result.timedOut ? 124 : result.outputExceeded ? 125
          : cleanupFailed || !exited ? 1 : exitCode;
      resolve(result);
    }

    function cleanup() {
      if (cleaning || settled) return;
      cleaning = true;
      clearTimeout(deadlineTimer);
      // Also clean a surviving descendant when its direct parent exited normally.
      killGroup('SIGTERM');
      if (!groupExists()) {
        cleanupDone = true;
        finish();
        if (settled) return;
        armVerificationDeadline();
      } else {
        pollTimer = setInterval(() => {
          if (!groupExists()) {
            cleanupDone = true;
            clearInterval(pollTimer);
            clearTimeout(graceTimer);
            finish();
            if (!settled) armVerificationDeadline();
          }
        }, 25);
        graceTimer = setTimeout(() => {
          killGroup('SIGKILL');
          // Start the verification allowance after KILL actually runs. A stalled
          // event loop must not make escalation and final verification expire
          // together before the OS can reap the killed process group.
          armVerificationDeadline();
        }, graceMs);
      }
    }

    function armVerificationDeadline() {
      // A killed process can leave pipes open in an escaped/unkillable descendant.
      // Never wait indefinitely for stdio closure; no process-tree scan is attempted.
      // Observing group absence must not renew an existing post-KILL allowance.
      if (drainTimer !== undefined || settled) return;
      drainTimer = setTimeout(() => {
        if (groupExists()) cleanupFailed = true;
        cleanupDone = true;
        finish(true);
      }, 1_000);
    }

    function onAbort() { result.aborted = true; cleanup(); }
    function collect(destination, chunk) {
      if (settled || result.outputExceeded) return;
      const available = maxOutputBytes - totalBytes;
      if (chunk.length > available) {
        if (available > 0) destination.push(Buffer.from(chunk.subarray(0, available)));
        totalBytes = maxOutputBytes;
        result.outputExceeded = true;
        cleanup();
      } else {
        totalBytes += chunk.length;
        destination.push(chunk);
      }
    }

    try {
      child = spawn(command, args, { detached: true, env, stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit' });
    } catch {
      resolve({ ...result, code: 127 });
      return;
    }
    child.on('error', () => {
      exitCode = 127;
      exited = true;
      closed = true;
      cleanup();
      finish();
    });
    child.on('exit', (code, childSignal) => {
      exited = true;
      exitCode = Number.isInteger(code) ? code : childSignal === 'SIGINT' ? 130 : childSignal === 'SIGTERM' ? 143 : 1;
      cleanup();
      finish();
    });
    child.on('close', () => { closed = true; finish(); });
    if (capture) {
      child.stdout.on('data', (chunk) => collect(stdout, chunk));
      child.stderr.on('data', (chunk) => collect(stderr, chunk));
    }
    signal?.addEventListener('abort', onAbort, { once: true });
    if (signal?.aborted) onAbort();
    if (!cleaning) {
      deadlineTimer = setTimeout(() => { result.timedOut = true; cleanup(); },
        Math.max(1, timeoutMs - (performance.now() - started)));
    }
  });
}

function alive(pid) {
  if (pid === undefined) return true;
  try { process.kill(pid, 0); return true; } catch { return false; }
}

function loopbackUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'http:' || url.username || url.password || url.hash
        || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) return null;
    return url;
  } catch { return null; }
}

function probe(url, timeoutMs, signal) {
  return new Promise((resolve) => {
    let done = false;
    let request;
    const finish = (ready) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      request?.destroy();
      resolve(ready);
    };
    const abort = () => finish(false);
    const timer = setTimeout(abort, timeoutMs);
    // Pin localhost to a loopback address, independent of host-file/DNS changes.
    request = http.get(url, {
      agent: false,
      ...(url.hostname === 'localhost' ? { hostname: '127.0.0.1' } : {}),
    }, (response) => {
      const ready = response.statusCode >= 200 && response.statusCode < 300;
      response.on('error', () => {});
      response.destroy(); // No body drain or redirect follow; headers suffice for readiness.
      finish(ready);
    });
    request.on('error', () => finish(false));
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort();
  });
}

function delay(ms, signal) {
  return new Promise((resolve) => {
    const finish = () => { clearTimeout(timer); signal?.removeEventListener('abort', finish); resolve(); };
    const timer = setTimeout(finish, ms);
    signal?.addEventListener('abort', finish, { once: true });
    if (signal?.aborted) finish();
  });
}

export async function waitForUrl(urlValue, {
  timeoutMs = 180_000, requestTimeoutMs = 2_000, intervalMs = 1_000, pid, signal,
} = {}) {
  boundedInteger(timeoutMs, 1, 86_400_000);
  boundedInteger(requestTimeoutMs, 1, 60_000);
  boundedInteger(intervalMs, 1, 60_000);
  if (pid !== undefined) boundedInteger(pid, 1, 2_147_483_647);
  const url = loopbackUrl(urlValue);
  if (!url) return { ready: false, reason: 'invalid-url' };
  const deadline = performance.now() + timeoutMs;
  while (true) {
    if (signal?.aborted) return { ready: false, reason: 'aborted' };
    if (!alive(pid)) return { ready: false, reason: 'process-exited' };
    const remaining = deadline - performance.now();
    if (remaining <= 0) return { ready: false, reason: 'timeout' };
    const ready = await probe(url, Math.max(1, Math.min(requestTimeoutMs, remaining)), signal);
    if (signal?.aborted) return { ready: false, reason: 'aborted' };
    if (!alive(pid)) return { ready: false, reason: 'process-exited' };
    if (performance.now() >= deadline) return { ready: false, reason: 'timeout' };
    if (ready) return { ready: true, reason: 'ready' };
    await delay(Math.max(1, Math.min(intervalMs, deadline - performance.now())), signal);
  }
}

async function main(args) {
  const controller = new AbortController();
  const interrupt = () => controller.abort('SIGINT');
  const terminate = () => controller.abort('SIGTERM');
  process.on('SIGINT', interrupt);
  process.on('SIGTERM', terminate);
  try {
    if (args[0] === 'wait-url') {
      if (args.length < 3 || args.length > 4) throw new TypeError();
      const result = await waitForUrl(args[2], {
        timeoutMs: Number(args[1]), pid: args[3] === undefined ? undefined : Number(args[3]), signal: controller.signal,
      });
      if (!result.ready) process.stderr.write(`Local readiness failed: ${result.reason}.\n`);
      return result.ready ? 0 : result.reason === 'aborted' ? interruptionCode(controller.signal)
        : result.reason === 'timeout' ? 124 : 1;
    }
    if (args.length < 2) throw new TypeError();
    const result = await runCommand(args[1], args.slice(2), {
      timeoutMs: Number(args[0]), signal: controller.signal,
    });
    if (result.timedOut) process.stderr.write('Local command exceeded its time limit.\n');
    if (result.cleanupFailed) process.stderr.write('Local process-group cleanup could not be confirmed.\n');
    return result.code;
  } catch {
    process.stderr.write('Local process supervision failed: invalid arguments or execution error.\n');
    return 2;
  } finally {
    process.removeListener('SIGINT', interrupt);
    process.removeListener('SIGTERM', terminate);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await main(process.argv.slice(2));
}
