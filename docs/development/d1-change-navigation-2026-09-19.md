# D1 — Change navigation and action ownership, 2026-09-19

Status: bounded frontend correction, locally validated. H1/D1 remain open. Browser
checks use synthetic HTTP; no real approval/apply, OIDC, RHBK/OpenShift or production
write/recovery acceptance is implied.

## Demonstrated defect and correction

The previous detail component retained the old record when the route changed. Late
GET/action success, error or finally handlers could overwrite the next record or
release its busy controls. Lists had the same race across target/status changes and
could repopulate after target removal. Neither surface checked response identity.

The detail wrapper now keys its stateful view by change ID. Each mounted visit owns
its generation and active lifetime; cleanup invalidates every pending handler, even
after A→B→A navigation. Reload clears the record/controls. A synchronous ref closes
the double-click window before disabled state is rendered. GET identity must match
the route; action responses must match both change and original target IDs.

The list toolbar remains mounted, preserving filter focus. Results remount for each
target/status visit and never mount without a target. Request generations protect
retry/cleanup and all response/error/completion paths. Any foreign-target item rejects
the entire returned page; no silently filtered partial result is presented as success.

Browser inspection also found black text on the dark background in bare secondary
buttons. These two pages now use the existing `btn--secondary` treatment; filters
expose `aria-pressed`. No shared CSS/design-system change was required. The fixture's
reset control uses the same existing style.

Requirements: SEC-MULTI-001, SEC-CHANGE-005, SEC-AUTHZ-001, FR-UI-001/002,
NFR-TEST-001/002. Contracts: [UI architecture](../ui-architecture.md),
[controlled administration](../architecture/controlled-administration.md),
[D1](../milestones/d1-local-workflow.md). Independent implementation/fixture reviews
found no scoped blocker. A review caveat about raw synthetic query logging was resolved
by documenting its limit, not claiming generic redaction.

## Source and version decision

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; accumulated dirty/untracked changes preserved.
No commit, push, rebase, release, tag, dependency installation or global runtime change.
The [manifest](evidence/d1-change-navigation-2026-09-19.json) compares against the
previous session manifest: **602 source inputs**, **three changed / six added / zero
removed**, aggregate:

`67ac915aeb11231720ab0057a777cfbce4b88e38e8c1159bdfd49c4f54da6303`

Changed: detail/list implementation and list tests. Added: detail tests and five
[synthetic fixture](../../dev/ui-change-navigation/README.md) files. Documentation is
reconciled separately; no backend, auth transport, grants, issuer, API, deployment,
dependency, rule or persistence implementation changed in this slice.

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
report schema **1.1**, Flyway **V1–V10** remain unchanged for this unreleased correction.
Context, architecture, milestone index/specifications, roadmap, Unreleased and version
decision are updated. AGENTS invariants and earlier dated ledgers are unchanged.
Hashes are unsigned local consistency evidence, not signed release attestations.

Final consistency checks: **750 file/log/artifact hashes matched**, including 602 source
inputs, 127 Maven reports, 13 execution logs, six build artifacts and two inventories.
Offline documentation checker: **139 Markdown documents / 883 local links / 15 milestone
specifications**, zero errors/warnings; six parser self-check groups passed. External
URLs were skipped. `git diff --check` passed. These are consistency/structure checks,
not proof of factual completeness or milestone acceptance.

## Automated validation

Java **21.0.10**, selected per command through `JENV_VERSION=21 jenv exec`.
Node **24.19.0** selected per-command from the installed bundled runtime; existing
`ui/node_modules` used. No global Java/Node modification. Logs use
`/private/tmp/kcops-d1-change-navigation-<name>-20260919.log` and may expire.

| Run / log name | Result |
|---|---|
| Fresh `mvn clean verify` / `baseline` | **1684 passed**, 9 opt-in ITs skipped; exit 0, 17:28:34 −03 |
| UI baseline / `ui-baseline` | **160 passed / 21 files**, exit 0 |
| Detail pre-fix / `detail-red` | **18 failed**, exit 1 |
| Detail initial corrected / `detail-green` | **18 passed**, exit 0 |
| List pre-fix / `list-red` | **12 failed / 3 passed**, exit 1 |
| List corrected / `list-green` | **15 passed**, exit 0 |
| Initial full suite/build / `ui-final`, `ui-build` | **193 passed / 22 files** and build passed, exit 0 |
| Secondary controls pre-fix / `controls-red` | **2 failed / 35 passed**, exit 1 |
| Post-control suite / `ui-verified` | **195 passed / 22 files**, exit 0 |
| Post-control build / `ui-build-final` | Exit 2: three RTL test selectors used unsupported `exact`; corrected by removing that test-only option |
| Final full suite / `ui-accepted` | **195 passed / 22 files**, exit 0, 17:43:33 −03 start |
| Final type-check/build / `ui-build-verified` | Passed, exit 0, 87 modules transformed |

**35 new tests**: 21 detail tests and 14 additional list tests. Detail coverage includes
four action types, old errors/finally while another action is pending, A→B→A reads and
actions, StrictMode effect replay, duplicate clicks, denied/missing records, mismatched
IDs and retry. List coverage includes first DOM commit before passive effects, target/
filter removal/replacement, success/error/finally, A→B→A and foreign-target rejection.
Control tests check existing style selection and exposed filter state, not pixel contrast.
Backend was unchanged after its fresh baseline, so it was not rebuilt again solely for
UI changes. No opt-in integration suite or identity/installation runner was rerun here.

## Browser observations — synthetic, not live authorization

The local [fixture](../../dev/ui-change-navigation/README.md) serves actual pages/API
client through Vite on fixed `127.0.0.1:18310`. No `.env`, AuthProvider, backend proxy,
IdP, containers or database. Request history is synthetic and bounded to 80 entries;
headers/bodies are not recorded, but paths/queries are not generically redacted. Never
attach credentials or expose this development server. Same-origin/Authorization checks
are fixture safeguards, not production authentication.

Observed through the in-app browser and SPA links, with pending/completed synthetic
requests visible. Response waits used specific UI/request content, not page reloads
for race scenarios:

- Slow A detail response completed after fast B; B remained visible.
- A approval completed while B approval was pending; B stayed visible with Verify
  disabled, then B completion enabled Apply. All approvals were mock in-memory changes.
- Navigating to denied/missing detail removed the previous record/actions and showed
  synthetic 403/404 errors.
- Wrong detail ID produced a load error without a record; wrong action target produced
  an action error without replacing the original target-A record.
- Late target-A list did not replace B; removing the target kept the selection prompt
  without a table after the pending request completed.
- Late WAITING_APPROVAL filter data did not replace APPROVED results; wrong-target list
  and denied list showed errors without a previous table.
- Enter activated navigation and filters; filter focus remained on the chosen button.
  Tab/Shift+Tab traversed native controls; Shift+Tab from Verify focused Apply.
- Post-correction filters exposed pressed state and secondary controls rendered
  `rgb(226, 232, 240)` instead of black. Desktop requested 1200×900 (1194 CSS content
  width) and narrow 390×844 (384 content width) had no body overflow. Narrow tables
  scrolled within their wrapper; buttons wrapped and focus was visible.
- Browser warning/error log queries returned no entries for this fixture. This is
  captured console evidence, not a full network audit.

Screenshots were inspected inline; this ledger does not claim archived video/screenshots,
screen-reader testing, complete WCAG conformance, zoom/motion checks or a browser matrix.
No WCAG version/level was specified for this bounded review. Existing primary-button
palette and unrelated shared controls were not audited or redesigned.

## Cleanup and limits

Owned browser tab closed and viewport override reset. Owned Vite session stopped with
Ctrl-C (process exit 1 due to interruption, not a test-run failure). Pre-browser and
final independent inventories use the pinned `podman-machine-default-root` connection.
At **17:42:51 −03**: **zero containers, zero volumes, default network only**, four cached
images unchanged, **12 fixture ports free** including 18310, identity-lab lock absent.
No global prune, image/volume deletion, VM restart or ambient cluster access. Vite's
documented reusable dependency optimization cache stays under `ui/node_modules`.

Ignoring a stale result cannot cancel/undo server work; backend policy, authorization,
idempotency and reconciliation remain authoritative. A valid GET route ID alone does
not prove that its target is authorized. No real-write recovery or distributed session
revocation is added. H1/D1 remain open for remaining browser issuer/audience/expiry
negatives, source trust/reviewer gates and AGT1. Next: remaining browser-negative cases,
then the local reference-agent prototype. D2 still needs an explicitly approved lab.
