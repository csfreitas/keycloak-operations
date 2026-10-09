# H1 scoped Admin response and diagnostic boundaries — 2026-09-18

Status: **IMPLEMENTED / CLEAN BACKEND REGRESSION PASSED; DEFAULT LAB AND CLEANUP BLOCKED BY LOCAL PODMAN UNAVAILABILITY; H1 ACCEPTANCE OPEN**.

This slice extends the [shared collection budget](h1-compound-collection-2026-09-18.md)
with bounded raw Keycloak responses, validation before model defaults and safe
diagnostic admission. It changes only the scoped collection client, not ordinary
administration or controlled writes.

## Baseline and version decision

Fresh Java **21.0.10**, selected per command with `JENV_VERSION=21 jenv exec`,
passed `mvn clean verify`: **1050 tests / 9 opt-in ITs skipped**, exit 0 at
**2026-09-18 17:20:03 −03:00**, before implementation. Raw log:
`/private/tmp/kcops-h1-admin-boundaries-baseline-20260918.log`.

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. Accumulated tracked/untracked changes are preserved;
no commit, push, rebase, tag, release, image publication or global Java/Node change.
Backend/application/MCP/OpenAPI remain **0.8.1-SNAPSHOT**; UI/root lock
**0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. UI, dependencies,
deployment, grants and migrations are unchanged in this slice. The version decision
is continued unreleased hardening, not a new release. AGENTS.md invariants need no
change; architecture, context, milestones and changelog track the increment.

## Implemented contract

- Scoped transport counts received bytes without trusting Content-Length and caps
  Admin bodies at **1 MiB**. Chunked bodies are bounded too. Only absent/identity or
  a single gzip content encoding is accepted. Scoped gzip decoding has a **1 MiB**
  limit; the JSON provider additionally caps successful token responses at **64 KiB**
  after decompression. Reads inherit the collection budget; close aborts instead of
  draining an unconsumed response. No global compression setting is changed.
- The JSON reader rejects duplicate keys, trailing documents, scalar coercion and
  float-to-integer coercion. Parser bounds: depth **32**, property-name length
  **256**, string length **32768**, numeric token length **128**. Nested containers
  are capped at **1024** elements; realm/client lists at **500**. Oversized lists
  are unavailable, never silently trimmed to a favorable subset.
- Known token, server-info, realm and client representations are validated before
  model conversion. Required identities/types, duplicate list identities, explicit
  feature flags and token header safety are checked. Unknown extra fields remain
  compatible and missing nullable flags remain unknown. A valid empty array is
  distinct from a missing/null/malformed list. This is not the full Keycloak schema.
- Realm GET identity must match the requested realm, including the existence probe
  before listing clients. Failed realm listing preserves independent server metadata
  but cannot emit an invented empty realm set/count or aggregate client total.
  Mismatched detail cannot supply requested-realm facts; scoped failures remain
  partial. Provider realm names/raw error text are omitted from collector warnings.
- Before credential resolution/client creation and at request/body boundaries,
  collection rejects DEBUG/TRACE-enabled unsafe RESTEasy/Apache diagnostic
  categories with a fixed cause-free error. Cached clients are checked again.
  Production code never mutates operator logger levels. Integration tests exercise
  effective inherited logger levels and verify no token/Admin request is sent when
  admission fails. The version-specific list covers **31 categories**, not all
  possible logging in the application.
- Native Apache challenge authentication and cookie processing are disabled for
  scoped requests, preventing unnecessary challenge/cookie handling and its warning
  paths. Keycloak OAuth token acquisition and Bearer filters remain enabled. Scoped
  adapter failures retain appropriate codes with fixed messages, no raw causes,
  provider diagnostics or requested identifiers. Ordinary-client behavior remains
  unchanged.

## Backend execution evidence

Focused attempt 1: **245 tests, 0 failures, 1 error**, exit 1. The valid-gzip case
exposed that RESTEasy's default Apache setup disables decompression. Negative gzip
rejection alone had not proved correct decompression. The scoped client now
registers the bounded gzip decoder, with tests for valid gzip, malformed gzip,
decoded token excess and unsupported/multiple encodings; no assertion was removed.
Raw log: `/private/tmp/kcops-h1-admin-boundaries-focused-20260918.log`.

Focused attempt 2: **252 tests / 0 failures / 0 errors**, exit 0 at
**17:28:28 −03:00**. A subsequent realm-identity guard and regression were added
before final clean verification; the focused result is not claimed for that later
source. Raw log: `/private/tmp/kcops-h1-admin-boundaries-focused2-20260918.log`.

Final `mvn clean verify`: **1242 tests / 0 failures / 0 errors**, **9 opt-in ITs
skipped**, exit 0 at **2026-09-18 17:32:26 −03:00**: **192 additional invocations**
versus baseline. Raw log: `/private/tmp/kcops-h1-admin-boundaries-verify-20260918.log`.
[Test references](evidence/h1-admin-boundaries-tests-2026-09-18.json) retain hashes
of **110 unit reports** and **five skipped-integration reports**. Tests cover
loopback HTTP raw/chunked/compressed responses, invalid tokens preventing Admin
calls, safe errors/log parameters, dynamic diagnostic admission and ordinary-client
compatibility. Independent scoped reviews found no blocking defect; the suggested
log-parameter assertion was included. Existing framework/deprecation warnings remain.

[Source manifest](evidence/h1-admin-boundaries-source-2026-09-18.json): **571 inputs**,
aggregate SHA-256
`11ba9bf7fda4320263784fde565f87dbb6d3730eab0af21cf85aba00bd0be417`.
Versus the shared-collection manifest: **six existing files changed, seven added,
none removed**. Source/reference verification matched **571 source inputs / 117
test references**. These are unsigned local working-tree records, not a signed or
committed release attestation.

## Final-package laboratories and cleanup

| Scenario | Executed result | Scope |
|---|---|---|
| Identity + metrics | **75 checks passed**, JWT log scan passed, exit 0 after cleanup | Real local signed identity, Community Keycloak targets, PostgreSQL and Prometheus; not a real cluster |
| Installation discovery/confirmation | **83 checks passed**, JWT log scan passed, exit 0 after cleanup | Real local identity/database plus synthetic loopback cluster API; expiry uses disposable-database fault injection, not a real wait |
| Default identity/report | **56 checks passed before TimeoutError**, runner exit **1**; cleanup did not complete | No cluster connection or configured metrics; this is a failed attempt, not 70 passing checks |

Metrics diagnostic directory: `/private/tmp/kcops-identity.w4zome`.
Installation diagnostic directory: `/private/tmp/kcops-installation.FA06ab`.
Default diagnostic directory: `/private/tmp/kcops-identity.zr31NV`.
Node **25.6.1** ran standalone lab scripts only; no UI build was rerun.
Java **21.0.10** was selected per command via jenv. The
[run references](evidence/h1-admin-boundaries-runs-2026-09-18.json) retain hashes
and excerpts for seven baseline/intermediate/final/runtime logs plus three packaged
artifacts, including the failed default attempt. No failed run is overwritten.

The default runner reached A's REST report checks, then its 20-second caller timeout
expired while requesting B's report. B's earlier MCP report had passed in the same
attempt. The application stopped at **17:36:30 −03:00**; its shutdown recorded a
PostgreSQL I/O/closed-connection failure. This does not alone establish the timeout's
root cause or an application regression.

Independent local checks found that `podman ps` and the runner's `podman compose
down --volumes` stopped responding, B's public discovery endpoint timed out after
three seconds, and the configured local VM SSH connection timed out during banner
exchange. Machine metadata still reported running (4 CPUs / 8 GiB). The precise
cause of Podman/VM unavailability is **not established**. Only the two identified
stuck child commands from this execution were terminated; no VM restart, forced
container deletion or unrelated process termination was performed. The runner
returned exit 1 and released its owned lock. Its normal final JWT scan was not
reached; a separate post-failure scan of the retained platform log passed without
printing token contents.

**Cleanup is incomplete and must not be reported as a clean environment.** At the
last check, ports **15432, 18080, 18180 and 18280** remained occupied; the other seven
fixture ports were free and the shared lock was absent. The last started resources
were `kcops-identity-postgres`, `kcops-identity-idp`, `kcops-identity-target-a`,
`kcops-identity-target-b` and `keycloak-operations-identity-lab_default`. Their final
container/volume/network state could not be read. Inspect these exact resources
after connectivity recovers; do not globally prune or reclaim unrelated resources.

Before labs, Maven's owned database had been removed and Podman showed zero
containers/volumes. Metrics and installation runners each exited 0 after removing
their owned containers/network and disposable tmpfs data; those temporary data are
not recoverable. Four cached reusable images were preserved at preflight:
Community Keycloak26.7.1 `cc689d358fe6`, PostgreSQL16 `02ad0fee02ae`, RHBK26.6
`e7affbc8b409` and Prometheus2.55.1 `f59c592ea6d9`. No image pull or global prune was
performed. Final image/volume inventory is unverified because Podman is unavailable;
the failed run's data lifecycle cannot be asserted. Logs and source evidence remain.

## Limits and next handoff

Final evidence verification matched **571 source inputs, 117 test references and
10 run/artifact references**, with no hash mismatch. Offline documentation checks
passed for **129 Markdown documents, 747 local links and 15 indexed milestones**,
with zero errors/warnings; six parser self-check groups also passed.
`git diff --check` passed. These checks do not validate external links, approve
milestones or resolve the incomplete runtime/cleanup gate above.

Conservative fixed limits can make a legitimate large environment partial. A valid
accessible realm/client list is not proof of full-environment visibility. No full
Keycloak schema, blanket metadata sanitization, arbitrary-source verification or
new paging/endpoint discovery is claimed. Nullable missing fields and unobserved
sources must remain explicit gaps.

Diagnostic admission is not an atomic guarantee against logger reconfiguration
during blocked I/O or pool cleanup. Ordinary clients and unrelated log paths are
outside this control; category coverage must be reviewed on dependency upgrades.
This implementation was checked against locally resolved RESTEasy **6.2.18.Final**,
Apache HttpClient **4.5.14** and the installed Commons/JBoss logging bridge. It does
not install a global log filter or overwrite operator settings.

Collection deadlines remain cooperative, not hard DNS/TLS/I/O, persistence or
rendering limits. HTTP header-size bounds and immediate termination of every native
resource are not asserted. No new UI/browser run, CI execution, native run,
container-image build/scan or real RHBK/OpenShift acceptance is implied. Previous
unchanged-UI/browser results remain historical, including 133 UI tests on Node24.

Immediate handoff: obtain authorization before restarting the local Podman machine,
which can interrupt all workloads in that machine. Once it responds, inspect/remove
only the failed run's exact disposable resources, rerun the default lab and verify
cleanup. Do not start another lab against unresolved resources.

After that recovery, next local work: configured-versus-observed capability/source coverage and remaining
metadata trust gates, broader browser negatives/revocation, then the read-only AGT1
prototype. H1/D1 stay open. D2 still requires the dedicated explicitly approved
RHBK/OpenShift environment; no cluster access is needed for the next local slice.
