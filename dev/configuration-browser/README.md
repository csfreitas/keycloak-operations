# Synthetic configuration browser fixture

This development fixture renders the real Configuration page, API client and styles.
All HTTP data is synthetic. It has no backend, Keycloak, OIDC, database, containers,
outbound proxy or model. It does not establish live authorization/provider acceptance.

With Node 24 and the existing `ui/node_modules`, run from the repository root:

```sh
node ui/node_modules/vite/bin/vite.js --config dev/configuration-browser/vite.config.mjs
```

Open http://127.0.0.1:18312/. The exact loopback host and port are fixed; an occupied
port fails startup. No `.env` files are loaded. Requests with credentials in
Authorization, Proxy-Authorization or Cookie headers or another Host/Origin are rejected.
API and fixture-control query parameters are rejected. Do not attach credentials, override the listener, publish
or deploy this fixture. Vite source-serving semantics still apply.

Check that opening the page and selecting a scope only fetches the descriptor list.
`Run inspection` performs the explicit synthetic GET. Realm A takes 2.5 seconds and
returns a partial observation with true, false and null values; Client B is fast and
complete. Start A, switch to B and inspect: late A must not replace B. Switching a
loaded scope must clear its previous facts immediately. The request log shows only
API paths and status; pending synthetic responses remain scheduled after navigation.

Other scopes exercise 403 denial, a foreign response scope and an inconsistent
COMPLETE observation. Banner controls exercise empty/error lists and local session
invalidation. `Load normal scopes` starts a new synthetic session and remounts the
page; it is not real sign-in. Inspect keyboard focus and a narrow viewport separately.

Stop only the owned foreground process with Ctrl-C and verify port 18312 is free.
No data or volumes are persisted; Vite may reuse its dependency cache. The presence
of this fixture does not mean a browser scenario was executed successfully.
