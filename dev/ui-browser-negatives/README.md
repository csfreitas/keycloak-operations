# Real-identity browser negatives (development only)

This loopback-only entry renders the **actual production App, AuthProvider,
keycloak-js PKCE adapter and API client** against the disposable Community Keycloak
26.7.1 identity lab. Responses are native backend responses, not synthetic 401s.
It is not RHBK/OpenShift acceptance and does not grant administrative permissions.
Never deploy this fixture or use real credentials. Production builds do not import it.

Prefer the supervised [identity-lab runner](../identity-lab/README.md):

```sh
bash scripts/validate-identity-lab.sh --browser-negatives
```

It runs the automated identity checks and presents wrong issuer, wrong audience,
expired token and valid control sequentially under one shared 900-second browser
budget. Close each lab tab before acknowledging the next scenario. Enter only
advances the runner; it does not establish a passing browser result. See the
[observed local results](../../docs/development/d1-browser-negatives-2026-09-19.md).

With the explicitly owned identity lab already ready and its normal Vite process
stopped, select exactly one scenario and launch with the repository's installed UI
dependencies and Node 24. The runner must own/supervise this process and its cleanup:

```sh
KCOPS_BROWSER_SCENARIO=wrong-issuer node ui/node_modules/vite/bin/vite.js --config dev/ui-browser-negatives/vite.config.mjs
```

Open **http://127.0.0.1:18300/**, without host/port overrides. Use the public disposable
Alice fixture account. No `.env` file is loaded. Host/Origin are restricted to this
exact loopback origin; Authorization headers to the Vite server are refused. The
actual API remains `http://localhost:18081`. Do not add wildcards or change grants.
Close the lab tab and stop the owned Vite process before switching scenarios.

| Scenario | Credential source | Expected observation |
| --- | --- | --- |
| `valid` | Normal public UI client and expected issuer | Signature/issuer/audience/expiry true; real `/me` 200; protected UI mounts. |
| `wrong-issuer` | Same realm/client through `127.0.0.1`, while backend expects `localhost` | Signature/audience true, issuer false, unexpired; actual 401 and no protected UI. Dynamic issuer must be observed, not assumed. |
| `wrong-audience` | Fixture-only public PKCE client without backend audience mapper | Signature/issuer/expiry true, audience false; actual 401 and no protected UI. |
| `expired` | Fixture-only public PKCE client with correct audience and two-second access lifetime | Observer delays first identity GET until expiry + two seconds; same signed token reaches backend and receives actual 401. |

The expiry delay is an explicit **controlled transport fault**, not ordinary adapter
refresh behavior. The real adapter continues refreshing normally before requests.
The delay is bounded to eight seconds, respects the original AbortSignal and never
modifies the token, request arguments or native response. A pre-dispatch aborted
attempt releases the delay selection for the next attempt (including StrictMode).
The scenario has no token-copy button, token endpoint, runtime control API or storage.

The banner outside protected routes displays only fixed stage labels, request
sequence/status and boolean checks. Signature verification uses browser Web Crypto
RS256 and the **fixed expected issuer's JWKS**, never a JWT-supplied URL. JWKS fetch
omits credentials, refuses redirects, has a three-second timeout and 64 KiB response
limit. `signatureValid: null` means unavailable (including CORS failure); do not claim
signature proof in that case. Claims are inspected only in memory; no tokens, decoded
claims, callback query, headers or response bodies are displayed, logged or persisted.

Record each actual observed scenario and cleanup separately. Inspect only sanitized
status evidence; do not export HAR/traces or print bearer headers. Backend JWT checks
remain authoritative. Browser rejection does not establish immediate revocation,
per-event SSE revalidation, production write cancellation or broad H1/D1 acceptance.

Pure harness tests (synthetic signed tokens, fake fetch; no server/containers):

```sh
node --test dev/ui-browser-negatives/transport.test.mjs
```
