# Synthetic change-navigation browser fixture

This loopback-only development fixture renders the real `ChangesPage` and
`ChangeDetailPage`, their styles and API client. The local Vite middleware supplies
**synthetic HTTP responses**, including intentionally mismatched identities.
It has no backend, Keycloak, OIDC provider, database, containers or outbound proxy.
Approve/reject/apply/verify only update disposable in-memory fixture records.
This is not authentication, authorization, provider integration or operational-write
acceptance; the production backend remains the authorization boundary.

## Run

Prerequisites: Node 24 and the repository's already installed `ui/node_modules`.
From the repository root, with Node 24 selected for this process only:

```sh
node ui/node_modules/vite/bin/vite.js --config dev/ui-change-navigation/vite.config.mjs
```

Open [the synthetic fixture](http://127.0.0.1:18310/changes). The host/port and API
origin are intentionally fixed; startup fails if port 18310 is occupied. No `.env`
files are loaded. Do not pass host/port overrides or attach real credentials. Requests
with an Authorization header or another Host/Origin are refused. Do not expose or
deploy this fixture. Vite's development source-serving semantics still apply.

Stop the owned foreground process with Ctrl-C after validation and verify its port
is free. No persistent data or volumes are created; dependency optimization may
reuse `ui/node_modules/.vite-change-navigation`. Restarting resets all records.
The reset button works only when no synthetic API reply is pending and does not
reload the currently rendered page; navigate to fetch reset state.

## Browser scenarios

Use the banner's **SPA navigation links**, not address-bar reloads: route reuse and
pending promises are the behavior under test. The visible request log records method,
path/query and pending/completed status, not headers or request bodies. Do not place
credentials in URLs: this synthetic fixture does not provide generic query redaction. Responses stay
scheduled after navigation deliberately; ignoring them is not rollback.

| Scenario | Procedure / expected corrected behavior |
| --- | --- |
| Late detail read | Open Detail A (2 s), immediately open Detail B (100 ms). Only B remains after A completes. |
| Loaded record to denied/missing | Load A, then open denied (403) or missing (404). A and its actions disappear immediately, then only the current error remains. |
| Late action with new action pending | Load A, approve, open B, approve B. A completes in 2 s; B takes 4 s. A cannot replace B or release B's disabled actions. |
| Wrong detail identity | Open Detail wrong identity; its 200 response contains B's ID. Display a load error, not B or actionable controls. |
| Wrong action target | Open Action wrong target and approve. The response retains the change ID but contains target B. It must not replace the displayed target-A record. |
| Target list race | Open List target A (2 s), immediately List target B (100 ms). Late A must not replace B's table. |
| Removed target | Open List target A and then List without target while pending. The target selection prompt stays without any table. |
| Filter race | Open List target A, then APPROVED (100 ms). Late unfiltered data must not replace the selected APPROVED result. |
| List identity mismatch | Open List wrong target. Its item intentionally belongs to A; show the load error, not the mismatched table. |
| List denial | Open List denied and confirm no earlier target's table remains. |

The list status response is an intentionally deterministic synthetic projection, not
a lifecycle-policy implementation. Backend tests own those policies. Also inspect
keyboard focus, a narrow viewport, related browser errors and request counts. Record
what actually ran in the slice's evidence ledger; the fixture's presence is not proof
that any scenario passed.
