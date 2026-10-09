# Keycloak Operations Fleet Console — UI

React + TypeScript + Vite frontend for the **Keycloak Operations Platform** (milestone 0.7).

## Requirements

- **Node.js 24 LTS recommended**; supported engine ranges are Node 22.12+ within 22.x or Node 24.x (see `engines.node` in `package.json`). The UI build image uses Node 24; no global workstation version change is required.
- **npm** (comes with Node)
- A running backend at `http://localhost:8081` (see main project `dev/` setup)

The Vite 7 production build uses its default browser target: Chrome/Edge 107,
Firefox 104 and Safari 16. This build target is not a completed browser support
matrix; see the [Vite migration notes](https://v7.vite.dev/guide/migration).

## Quick Start (with backend)

```bash
# 1. Start the backend (from repo root)
docker compose -f dev/compose.yaml up -d
JENV_VERSION=21 jenv exec mvn quarkus:dev
# Packaged loopback lab only: QUARKUS_PROFILE=local-lab java -jar target/quarkus-app/quarkus-run.jar

# 2. Start the UI dev server
cd ui
npm ci
npm run dev
# Open http://localhost:3000
```

## Environment Variables

Copy `.env.example` to `.env.local` and adjust:

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8081` | Backend base URL (no trailing slash) |
| `VITE_AUTH_MODE` | `OPEN_LAB` | `OPEN_LAB` (no auth) or `OIDC` |
| `VITE_OIDC_AUTHORITY` | — | OIDC issuer URL (OIDC mode only) |
| `VITE_OIDC_CLIENT_ID` | — | OIDC client ID (OIDC mode only) |

**Never put secrets in `.env` files committed to the repository.**

## Auth Modes

- **OPEN_LAB** (frontend default): Requires an explicitly local-lab/dev backend. Packaged backend defaults do not permit open access. Never expose this mode on shared/public networks.
- **OIDC**: Set `VITE_AUTH_MODE=OIDC` and the issuer/public-client variables before building. Implemented with `keycloak-js`, authorization code + PKCE S256, in-memory tokens, refresh and authenticated SSE. `/api/v1/me` must agree with the configured mode; mismatch fails closed. Register exact UI origin + `/` redirect/logout URIs and the backend token audience. Never configure a browser client secret.

The current adapter expects a Keycloak-style `/realms/{realm}` issuer. This is not universal IdP compatibility. [Recorded local tests](../docs/development/local-identity-validation-2026-09-11.md) do not complete browser login/logout/refresh acceptance against a real IdP.

## Vite Proxy

In dev mode, the Vite server proxies `/api` → `http://localhost:8081`, so you can configure `VITE_API_BASE_URL` as empty or the UI origin to avoid CORS issues.

## Scripts

| Script | Description |
|---|---|
| `npm run dev` | Start dev server at http://localhost:3000 |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview production build locally |
| `npm test` | Run tests in watch mode |
| `npm run test:run` | Run tests once (CI) |
| `npm run lint` | Type-check only (`tsc --noEmit`) |

## Production Build (nginx)

```bash
npm run build
# Serve ./dist/ with nginx; ensure nginx rewrites unknown paths to index.html
```

Example nginx snippet:

```nginx
server {
  root /usr/share/nginx/html;
  location / {
    try_files $uri $uri/ /index.html;
  }
  # Proxy API requests to the backend
  location /api/ {
    proxy_pass http://keycloak-ops-backend:8081/api/;
  }
}
```

## Architecture

```
Browser
  └─ React SPA (this module)
       └─ /api/v1 REST + SSE
            └─ Operations Backend (Quarkus)
                 ├─ Keycloak / RHBK (Admin REST — read-only)
                 ├─ OpenShift / Kubernetes (inventory)
                 ├─ Prometheus (metrics)
                 └─ PostgreSQL (history)
```

The browser **never** calls Keycloak Admin, Kubernetes/OpenShift APIs, Prometheus, or PostgreSQL directly.

## Pages

| Route | Page |
|---|---|
| `/` → `/targets` | Fleet dashboard |
| `/targets/:targetId` | Target overview |
| `/targets/:targetId/health` | Health checks |
| `/targets/:targetId/assessment` | Assessment + findings |
| `/targets/:targetId/performance` | Semantic metrics |
| `/targets/:targetId/infrastructure` | Snapshots / inventory |
| `/targets/:targetId/installation` | Discover/review/confirm installation on an existing target |
| `/targets/:targetId/report` | On-demand operations report |
| `/targets/:targetId/history` | History (assessments, health, snapshots, audit) |
| `/changes`, `/targets/:targetId/changes`, `/changes/:changeId` | Controlled change list/detail |

Installation confirmation requires explicit READ/DISCOVER/BIND permissions and administrator review of global read-only; the UI never enables these itself. It does not create targets or connections. See [installation contract](../docs/development/installation-onboarding-2026-09-11.md).

## Testing

For real local OIDC login/logout/refresh, two-target isolation and report/event validation, use the [disposable browser lab](../dev/identity-lab/README.md). Its dedicated UI origin is `http://127.0.0.1:18300/`; normal development settings are unchanged.

```bash
npm run test:run
```

Uses **Vitest** + **Testing Library** + **jsdom**. Tests cover:

- Fleet rendering (loading, error, empty, data)
- Target navigation
- Status badges (healthy/unhealthy)
- Assessment + findings rendering
- Metrics unavailable/stale (MetricValue never shows missing as 0)
- API error states
- Partial assessment handling
- Empty history
- Target isolation (switching targets clears stale data)
