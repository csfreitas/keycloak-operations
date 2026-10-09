import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const fixtureRoot = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(fixtureRoot, '../../ui');
const origin = 'http://127.0.0.1:18310';
const timestamp = '2026-09-19T12:00:00Z';

function record(changeId, targetId) {
  return {
    changeId, targetId, environment: 'DEV', resourceType: 'CLIENT',
    resourceId: `fixture-${changeId}`, realm: 'synthetic-realm', operation: 'UPDATE',
    status: 'WAITING_APPROVAL', risk: 'LOW', policyDecision: 'APPROVAL_REQUIRED',
    policyReason: 'Synthetic fixture; no real authorization or mutation.', requiresApproval: true,
    planFingerprint: `synthetic-plan-${changeId}`, baselineFingerprint: 'synthetic-baseline',
    approvalFingerprint: null,
    diff: [{ property: 'name', kind: 'CHANGED', before: 'Synthetic before', after: 'Synthetic after' }],
    verificationStatus: null, verificationMessage: null, resultMessage: null,
    approvedBy: null, approvedAt: null, appliedAt: null, createdAt: timestamp, updatedAt: timestamp,
  };
}

function syntheticChanges() {
  let records;
  let requests = [];
  let sequence = 0;
  const timers = new Set();
  const reset = () => {
    records = new Map([
      ['synthetic-a', record('synthetic-a', 'synthetic-target-a')],
      ['synthetic-b', record('synthetic-b', 'synthetic-target-b')],
      ['synthetic-wrong-action', record('synthetic-wrong-action', 'synthetic-target-a')],
    ]);
    requests = [];
  };
  reset();

  return {
    name: 'synthetic-change-navigation-only',
    configureServer(server) {
      server.httpServer?.once('close', () => {
        for (const timer of timers) clearTimeout(timer);
        timers.clear();
      });
      server.middlewares.use((req, res, next) => {
        const json = (status, body) => {
          if (res.destroyed) return;
          res.statusCode = status;
          res.setHeader('Content-Type', 'application/json');
          res.setHeader('Cache-Control', 'no-store');
          res.end(JSON.stringify(body));
        };
        const error = (status, message) => ({ code: 'SYNTHETIC_FIXTURE', status, message });
        if (req.headers.host !== '127.0.0.1:18310' || (req.headers.origin && req.headers.origin !== origin)) {
          json(403, error(403, 'Synthetic fixture is restricted to its exact loopback origin.'));
          return;
        }
        if (req.headers.authorization) {
          json(400, error(400, 'Do not send credentials to this synthetic fixture.'));
          return;
        }
        const url = new URL(req.url ?? '/', origin);
        if (url.pathname === '/__fixture/requests' && req.method === 'GET') {
          json(200, requests);
          return;
        }
        if (url.pathname === '/__fixture/reset' && req.method === 'POST') {
          if (timers.size) json(409, error(409, 'Synthetic requests are still pending.'));
          else { reset(); json(200, { reset: true, synthetic: true }); }
          return;
        }
        if (!url.pathname.startsWith('/api/')) {
          next();
          return;
        }
        const entry = { sequence: ++sequence, method: req.method, path: url.pathname + url.search, state: 'pending' };
        requests.push(entry);
        if (requests.length > 80) requests.shift();
        const reply = (delay, status, body) => {
          const timer = setTimeout(() => {
            timers.delete(timer);
            entry.state = `completed HTTP ${status}`;
            json(status, typeof body === 'function' ? body() : body);
          }, delay);
          timers.add(timer);
        };
        if (url.pathname === '/api/v1/changes' && req.method === 'GET') {
          const targetId = url.searchParams.get('targetId');
          const status = url.searchParams.get('status');
          if (targetId === 'synthetic-target-denied') {
            reply(100, 403, error(403, 'Synthetic target is denied.'));
            return;
          }
          const validTargets = ['synthetic-target-a', 'synthetic-target-b', 'synthetic-target-mismatch'];
          if (!validTargets.includes(targetId)) {
            reply(100, 400, error(400, 'Select an explicit synthetic target.'));
            return;
          }
          const selected = records.get(targetId === 'synthetic-target-b' ? 'synthetic-b' : 'synthetic-a');
          const item = { ...selected, ...(status ? { status } : {}) };
          const delay = targetId === 'synthetic-target-a' && status !== 'APPROVED' ? 2000 : 100;
          reply(delay, 200, { items: [item], page: 0, size: 50, total: 1 });
          return;
        }
        const match = /^\/api\/v1\/changes\/(synthetic-[a-z-]+)(?:\/(approve|reject|apply|verify))?$/.exec(url.pathname);
        if (!match) {
          reply(0, 404, error(404, 'No real API exists in this synthetic fixture.'));
          return;
        }
        const [, changeId, action] = match;
        if (!action && req.method === 'GET') {
          if (changeId === 'synthetic-denied') reply(100, 403, error(403, 'Synthetic change is denied.'));
          else if (changeId === 'synthetic-wrong-id') reply(100, 200, { ...records.get('synthetic-b') });
          else if (!records.has(changeId)) reply(100, 404, error(404, 'Synthetic change was not found.'));
          else reply(changeId === 'synthetic-a' ? 2000 : 100, 200, { ...records.get(changeId) });
          return;
        }
        if (action && req.method === 'POST' && records.has(changeId)) {
          // Bodies/actor fields are deliberately neither read nor logged; there is no remote mutation.
          reply(changeId === 'synthetic-b' ? 4000 : 2000, 200, () => {
            const result = { ...records.get(changeId), resultMessage: `Synthetic ${action} completed.` };
            if (action === 'approve') {
              result.status = 'APPROVED';
              result.approvedBy = 'synthetic-fixture';
              result.approvedAt = timestamp;
              result.approvalFingerprint = result.planFingerprint;
            } else if (action === 'reject') result.status = 'REJECTED';
            else if (action === 'apply') { result.status = 'APPLIED'; result.appliedAt = timestamp; }
            else { result.verificationStatus = 'VERIFIED'; result.verificationMessage = 'Synthetic comparison only.'; }
            records.set(changeId, result);
            return changeId === 'synthetic-wrong-action' ? { ...result, targetId: 'synthetic-target-b' } : result;
          });
          return;
        }
        reply(0, 405, error(405, 'Unsupported synthetic fixture operation.'));
      });
    },
  };
}

export default {
  root: fixtureRoot,
  envDir: false,
  cacheDir: resolve(uiRoot, 'node_modules/.vite-change-navigation'),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(origin) },
  esbuild: { jsx: 'automatic' },
  resolve: {
    alias: {
      react: resolve(uiRoot, 'node_modules/react'),
      'react-dom': resolve(uiRoot, 'node_modules/react-dom'),
      'react-router-dom': resolve(uiRoot, 'node_modules/react-router-dom'),
    },
    dedupe: ['react', 'react-dom', 'react-router', 'react-router-dom'],
  },
  plugins: [syntheticChanges()],
  server: {
    host: '127.0.0.1', port: 18310, strictPort: true, cors: false,
    fs: { strict: true, allow: [fixtureRoot, uiRoot] },
  },
};
