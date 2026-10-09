import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { scenarioConfiguration } from './transport.mjs';

const fixtureRoot = dirname(fileURLToPath(import.meta.url));
const uiRoot = resolve(fixtureRoot, '../../ui');
const scenario = process.env.KCOPS_BROWSER_SCENARIO;
const selected = scenarioConfiguration(scenario);
const origin = 'http://127.0.0.1:18300';

export default {
  root: fixtureRoot,
  envDir: false,
  cacheDir: resolve(uiRoot, 'node_modules/.vite-browser-negatives'),
  define: {
    'import.meta.env.VITE_AUTH_MODE': JSON.stringify('OIDC'),
    'import.meta.env.VITE_API_BASE_URL': JSON.stringify('http://localhost:18081'),
    'import.meta.env.VITE_OIDC_AUTHORITY': JSON.stringify(selected.authority),
    'import.meta.env.VITE_OIDC_CLIENT_ID': JSON.stringify(selected.clientId),
    'import.meta.env.KCOPS_BROWSER_SCENARIO': JSON.stringify(scenario),
  },
  esbuild: { jsx: 'automatic' },
  resolve: { alias: [
    { find: /^react(?=\/|$)/, replacement: resolve(uiRoot, 'node_modules/react') },
    { find: /^react-dom(?=\/|$)/, replacement: resolve(uiRoot, 'node_modules/react-dom') },
    { find: /^react-router-dom$/, replacement: resolve(uiRoot, 'node_modules/react-router-dom/dist/index.mjs') },
    { find: /^keycloak-js$/, replacement: resolve(uiRoot, 'node_modules/keycloak-js/lib/keycloak.js') },
  ] },
  server: { host: '127.0.0.1', port: 18300, strictPort: true, cors: false,
    fs: { strict: true, allow: [fixtureRoot, uiRoot] } },
  plugins: [{
    name: 'exact-loopback-browser-negative-fixture',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.headers.host !== '127.0.0.1:18300' || (req.headers.origin && req.headers.origin !== origin)
            || req.headers.authorization) {
          res.statusCode = 403;
          res.setHeader('Cache-Control', 'no-store');
          res.end('Browser fixture accepts only its exact loopback origin without credentials.');
          return;
        }
        res.setHeader('Cache-Control', 'no-store');
        next();
      });
    },
  }],
};
