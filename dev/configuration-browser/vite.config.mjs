import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const fixtureRoot = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(fixtureRoot, '../../ui');
const origin = 'http://127.0.0.1:18312';
const realm = {
  scopeId: 'realm-a', targetId: 'synthetic-target-a', realm: 'synthetic-realm', kind: 'REALM',
  fields: ['enabled', 'registrationAllowed', 'resetPasswordAllowed', 'bruteForceProtected', 'verifyEmail'],
};
const client = {
  scopeId: 'client-b', targetId: 'synthetic-target-b', realm: 'synthetic-realm', kind: 'CLIENT',
  fields: ['enabled', 'publicClient', 'standardFlowEnabled', 'implicitFlowEnabled', 'directAccessGrantsEnabled', 'serviceAccountsEnabled'],
};
const scopes = [realm, client, ...['denied', 'wrong-scope', 'invalid-complete'].map(scopeId => ({ ...realm, scopeId }))];

function observation(scope) {
  const facts = scope.kind === 'REALM'
    ? { enabled: true, registrationAllowed: false, resetPasswordAllowed: true, bruteForceProtected: null, verifyEmail: false }
    : { enabled: true, publicClient: false, standardFlowEnabled: true, implicitFlowEnabled: false, directAccessGrantsEnabled: false, serviceAccountsEnabled: true };
  return {
    schemaVersion: '1.0', observationId: scope.kind === 'REALM'
      ? '11111111-1111-4111-8111-111111111111' : '22222222-2222-4222-8222-222222222222',
    scope, collectedAt: '2026-09-19T12:00:00Z', source: 'KEYCLOAK_ADMIN_API', productVersion: 'UNKNOWN',
    status: scope.kind === 'REALM' ? 'PARTIAL' : 'COMPLETE', facts,
    missingFields: scope.kind === 'REALM' ? ['bruteForceProtected'] : [],
  };
}

function syntheticConfiguration() {
  let mode = 'normal';
  let sequence = 0;
  const requests = [];
  const timers = new Set();
  return {
    name: 'synthetic-configuration-only',
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
        if (req.headers.host !== '127.0.0.1:18312' || (req.headers.origin && req.headers.origin !== origin)) {
          json(403, error(403, 'Synthetic fixture is restricted to its exact loopback origin.'));
          return;
        }
        if (req.headers.authorization || req.headers.cookie || req.headers['proxy-authorization']) {
          json(400, error(400, 'Do not send credentials to this synthetic fixture.'));
          return;
        }
        const url = new URL(req.url ?? '/', origin);
        if (url.search && (url.pathname.startsWith('/api/') || url.pathname.startsWith('/__fixture/'))) {
          json(400, error(400, 'Query parameters are unsupported.'));
          return;
        }
        if (url.pathname === '/__fixture/requests' && req.method === 'GET') {
          json(200, requests);
          return;
        }
        const scenario = /^\/__fixture\/mode\/(normal|empty|error)$/.exec(url.pathname);
        if (scenario && req.method === 'POST') {
          mode = scenario[1];
          json(200, { synthetic: true, mode });
          return;
        }
        if (!url.pathname.startsWith('/api/')) {
          if (url.pathname.startsWith('/__fixture/')) json(404, error(404, 'Unknown fixture operation.'));
          else next();
          return;
        }
        if (req.method !== 'GET') {
          json(405, error(405, 'Synthetic configuration inspections only support GET.'));
          return;
        }
        const entry = { sequence: ++sequence, path: url.pathname, state: 'pending' };
        requests.push(entry);
        if (requests.length > 80) requests.shift();
        const reply = (delay, status, body) => {
          const timer = setTimeout(() => {
            timers.delete(timer);
            entry.state = `completed HTTP ${status}`;
            json(status, body);
          }, delay);
          timers.add(timer);
        };
        if (url.pathname === '/api/v1/configuration-reads') {
          if (mode === 'error') reply(100, 503, error(503, 'Synthetic descriptor list is unavailable.'));
          else reply(100, 200, mode === 'empty' ? [] : scopes);
          return;
        }
        const id = url.pathname.slice('/api/v1/configuration-reads/'.length);
        const scope = url.pathname.startsWith('/api/v1/configuration-reads/') && scopes.find(value => value.scopeId === id);
        if (!scope) reply(0, 404, error(404, 'No real API exists in this synthetic fixture.'));
        else if (id === 'denied') reply(100, 403, error(403, 'Synthetic configuration scope is denied.'));
        else if (id === 'wrong-scope') reply(100, 200, observation(client));
        else if (id === 'invalid-complete') reply(100, 200, { ...observation(scope), status: 'COMPLETE' });
        else reply(id === 'realm-a' ? 2500 : 100, 200, observation(scope));
      });
    },
  };
}

export default {
  root: fixtureRoot,
  envDir: false,
  cacheDir: resolve(uiRoot, 'node_modules/.vite-configuration-browser'),
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
  plugins: [syntheticConfiguration()],
  server: {
    host: '127.0.0.1', port: 18312, strictPort: true, cors: false,
    fs: { strict: true, allow: [fixtureRoot, uiRoot] },
  },
};
