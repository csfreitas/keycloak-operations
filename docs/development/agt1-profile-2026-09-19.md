# AGT1 — Provider-neutral profile foundation, 2026-09-19

Status: **local contract prototype validated; full AGT1 acceptance open**. This is an
optional versioned profile/host adapter, not an enabled AI service. No model/provider
was selected, contacted, installed or evaluated. H1/D1 acceptance remains open.

## Delivered scope and boundaries

[Reference profile 0.1.0](../../dev/reference-agent/README.md) adds operational
instructions separate from AGENTS.md, a fixed request builder, scalar fact projection,
deterministic no-AI fallback, structural explanation checks and an offline CLI.
The only callable operation is `keycloak_generate_operations_report`; the operator
fixes target/profile/window. Credentials/endpoint and authenticated MCP transport are
host-owned, not model-controlled. Reference permissions are only READ/ASSESS with
global read-only retained. Generating reports still persists platform observations
and audit; a collection is not free of side effects or safe to retry invisibly.

The library makes one callback, signals cancellation after a 20-second asynchronous
wait, and never retries or selects another endpoint. A cooperative adapter is
required; synchronous blocking code or a remote operation can outlive that timer.
This is neither a hard process deadline nor rollback of server work.

Only the known compact MCP schema-1.1 contract is accepted. Source identities,
UTC collection windows, catalog hash, independent-collection mode, unavailable replay,
sections, statuses and assessment values are validated and copied with exact JSON
paths. The source is unchanged. Markdown, free metadata, names/profile labels and
section messages are excluded. This reduces untrusted text in the packet; it is
not a universal secret/PII filter or authentication of imported JSON.

The explanation proposal must copy all facts and identities exactly and cite existing
paths in separate observations/hypotheses/recommendations. Results explicitly state
`structuralChecks=PASSED`, `semanticReview=REQUIRED`, `modelEvaluated=false`.
**Contradictory prose can still pass structurally**: an explicit test demonstrates
this, rather than suggesting that reference matching proves meaning or safety.
The CLI emits only fixed evaluation flags, never unreviewed candidate prose.

CLI input is bounded to 1 MiB per regular UTF-8 JSON file, errors do not echo paths,
parser excerpts or contents. Independent review found a possible FIFO block before
regular-file verification; opening with `O_NONBLOCK` resolves that local case, and
the final CLI tests exercise a real FIFO without a writer. Operator-selected files
on slow/remote filesystems remain outside a hard IO deadline guarantee.

Important source constraint: the existing MCP report does **not** contain structured
report-bound findings/evidence values or performance measurements. It supplies a
summary and deterministic Markdown. The profile does not parse Markdown or join
target finding history to manufacture the missing linkage. A report UUID is generated
on demand, not a retained report replay handle; finding keys are not globally unique
evidence-record IDs. These limits keep full AGT1 grounding acceptance open.

Requirements: FR-AGENT-001, FR-AI-001–003, SEC-AI-001/002, SEC-MULTI-001,
SEC-REPORT-001, NFR-AI-001. Architecture and independent review favored this optional
host-side contract over adding an LLM dependency or broader backend access. Existing
UI/REST reporting remains independently usable without this profile or any model.

## Source and versions

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. Accumulated dirty work is preserved. No commit,
push, rebase, tag, release, external credential lookup or global runtime change.
Backend/UI product source, dependencies, grants, migrations and contracts are unchanged.

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
report schema **1.1**, Flyway **V1–V10** remain unchanged. New reference-profile
**0.1.0** is its own local contract revision, not a product publication. Architecture,
context, milestones, guides, Unreleased and version decision are reconciled; AGENTS
invariants and dated prior ledgers are unchanged.

The [manifest](evidence/agt1-profile-2026-09-19.json) compares to the prior browser
manifest: **617 source inputs**, **three changed / eight added / zero removed**,
aggregate:

`8f0569a5e4f18732f176fb93169738cb61b8f4835d9e6f89d8c1646bd90ba906`

Changed: identity-lab guide and its shell/Node validation runners. Added: the eight
files under `dev/reference-agent/`. The optional flag adds six checks, while default
75/metrics80 totals remain unchanged when absent. Source and artifact hashes are
unsigned local consistency evidence, not authenticity or release attestations.

Final consistency verification: **758 file/log/artifact hashes matched** (617 source
inputs, 127 Maven reports, six execution logs, six build artifacts, two inventories).
Offline documentation checks passed **141 Markdown documents / 922 local links /
15 milestone specifications**, zero errors/warnings, plus six parser self-check
groups. External URLs were skipped. Shell syntax and `git diff --check` passed.
These checks establish consistency and structure, not factual completeness or acceptance.

## Executed validation

Java **21.0.10** through per-command `JENV_VERSION=21 jenv exec`; Node **24.19.0**
selected per command from the installed runtime. No global selection or dependency
installation. Logs use `/private/tmp/kcops-agt1-profile-<name>-20260919.log` and may
expire; the manifest preserves hashes and selected summaries.

| Run / log | Actual result |
|---|---|
| Fresh `mvn clean verify` / `baseline` | **1684 passed / 9 opt-in ITs skipped**, exit 0; 18:32:30 −03 completion |
| Initial combined contracts / `contracts` | **191 passed**, exit 0; 34 new core tests + 157 existing runner/fixture tests |
| Final combined contracts / `contracts-final` | **202 passed**, zero failures/skips, exit 0; adds 11 CLI tests |
| UI regression / `ui` | **195 passed / 22 files**, exit 0; start 18:36:49 −03 |
| Production UI type-check/build / `ui-build` | Passed, exit 0; 87 modules |
| `validate-identity-lab.sh --reference-agent` / `lab` | **81 checks**, compact-JWT log scan, verified owned cleanup; exit 0 |

Backend source was unchanged after its fresh baseline; that package was used in the
lab. No opt-in Maven ITs, metrics/installation lab variants, browser walkthrough,
real RHBK/OpenShift, provider/model calls, model eval service or CI run occurred.
The `--reference-agent --metrics` combination has expected count 86 but was **not
executed** in this slice. Existing product source/build does not import this profile.

## Evaluation ledger — contract scope, not model behavior

Client: local Node **24.19.0** host contract/profile **0.1.0**, with the existing
disposable lab MCP protocol driver. Real protocol request revision: **2025-11-25**;
platform Quarkus **3.39.4**, MCP extension **1.13.2**; local IdP **Community Keycloak
26.7.1**, fixture service-account client credentials, separate A/B target grants.
Provider/model/revision: **NONE / NOT RUN**. Profile/instruction/core hashes are in
the source manifest. The lab driver is a scoped test harness, not a supported
general-purpose OIDC/MCP client. Imported synthetic JSON is explicitly not observed
environment evidence.

| Case | Expected/observed contract result | Evidence type |
|---|---|---|
| Valid summary and source identity | Exact target/report IDs and scalar values preserved | Unit + actual A/B MCP |
| PARTIAL, UNKNOWN, unavailable score | No replacement with COMPLETE, healthy, zero or PASS | Unit + actual unavailable-score checks |
| COMPLETE assessment with numeric score | Existing score copied without equating completeness and health | Synthetic unit case |
| Foreign target / unknown tool / arbitrary URL or payload | Fixed request boundary or response scope rejects; no extra call | Unit; existing real MCP foreign-target denial |
| Invalid source credentials/provider error | Fixed SOURCE_UNAVAILABLE, no cause/token echo or retry | Injected callback error; lab separately checks anonymous/invalid tokens |
| Hostile metadata/Markdown | Excluded from packet; no derived tool choice or disclosure | Synthetic canary unit case |
| Fabricated IDs/references, altered score/window/status | Contract rejects | Synthetic unit cases |
| Missing metrics / zero finding count | No traffic/performance/PASS fact invented | Synthetic unit case |
| Provider timeout | Cooperative signal aborted, no retry | Mock-clock unit case, not a timed-out live provider |
| Contradictory prose with valid references | Structural pass, semantic review REQUIRED | Deliberate limitation test; not semantic acceptance |
| No model available | Deterministic projection still works; no model requested | CLI + actual MCP collection |
| Malformed/oversized/nonregular files and FIFO | Fixed safe failure within test subprocess deadline | Actual offline CLI subprocesses |

All 45 profile/CLI tests passed. This is **not** “zero model safety violations”:
there were zero model runs, and narrative quality/privacy/adversarial robustness and
repeated-run variability were not evaluated. No instruction prompt can replace
backend grants, host allowlisting or review of an external disclosure.

## Actual local MCP run and hygiene

Run **98e997b2-a5b7-469b-abd2-a7f8204f6c67**, pinned connection
**podman-machine-default-root**. Each actor generated one report through the profile
callback in the existing authenticated MCP session. The same returned object fed
the regular report assertions; the profile did not create a hidden second report.

New checks **20–22 (A)** and **44–46 (B)** verify one fixed scoped collection, exact
target/report/profile identity, retained null score and report completeness, unchanged
source serialization, excluded free metadata paths, and structural-only review flags.
Existing checks cover real target denial, read access, events, missing metrics,
report/history isolation, canaries and invalid tokens. No role/grant was added.

Four named/labeled owned containers and one network were removed automatically,
including disposable tmpfs PostgreSQL state, which is not recoverable. No persistent
volume was created or deleted. No new image was pulled. Final independent inventory
at **18:38:55 −03** confirms zero containers/volumes, default network only, all twelve
checked ports free, shared lock absent and four reusable cached images unchanged by
ID. No browser tab was opened. Private diagnostics: `/private/tmp/kcops-identity.2PfBG5`.
There was no global prune, VM restart or default runtime-context change.

## Next bounded work

Expose bounded structured findings/evidence linked to the generated report, then
adapt the profile to an explicitly selected client/provider with reviewed data scope.
Only after real repeated normal/adversarial explanation runs can AGT1 model criteria
be accepted. H1/D1 reviewer and independent operator gates remain open; D2 still
requires the explicitly approved dedicated RHBK/OpenShift lab.
