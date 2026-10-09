# H1 dependency remediation — 2026-09-18

Status: **REVIEWED DEPENDENCIES UPDATED / LOCAL REGRESSION PASSED; H1 ACCEPTANCE OPEN**. This corrects the selected findings from the [dated dependency review](h1-dependency-review-2026-09-18.md), following [provider failure/bounds hardening](h1-failure-bounds-2026-09-18.md). Historical ledgers retain their original versions/results. No commit, push, rebase, release, permission expansion, real cluster access or global Java/Node change.

## Selected changes and rationale

| Component | Before | After |
|---|---|---|
| Quarkus BOM and Maven plugin | 3.38.1 | **3.39.4** |
| MCP HTTP/core/test extension | 1.13.1 | **1.13.2** |
| Keycloak Admin / client-common-synced | 26.0.12 / 26.0.11 | **26.0.12 / 26.0.12**, aligned by the BOM |
| React Router DOM / Router | 6.30.4 | **7.18.4** |
| Vite / Vitest | 5.4.21 / 2.1.9 | **7.3.6 / 4.1.11** |
| React Vite plugin / esbuild | 4.7.0 / 0.21.5 | **5.2.0 / 0.28.2** |
| UI CI and image build runtime | Node 20 | **Node 24 LTS** |

React 18.3.1, keycloak-js 26.2.4 and Java 21 remain unchanged. No application/test source or Vite configuration adjustment was needed; no tests were removed or skipped to make this migration pass. Identity, PKCE, callback/return-path allowlists, target permissions, loopback lab origins, read-only defaults and fixed proxies are unchanged. No MCP 2.x/protocol migration, Vite 8/Rolldown migration, SSR, arbitrary transitive override or `npm audit fix --force` was introduced.

Quarkus 3.39 was the active non-LTS branch at review time; 3.39.4 includes the OIDC shared-introspection-cache correction published in 3.39.2. The previous configuration did not demonstrate the advisory's multi-OIDC-tenant exploit conditions; updating removes the affected version rather than claiming a reproduced application bypass. The new BOM already aligns both Keycloak client artifacts, so no extra override is needed. Sources: [Quarkus maintenance table](https://quarkus.io/releases/), [OIDC advisory](https://github.com/quarkusio/quarkus/security/advisories/GHSA-qfvj-8whj-27w4), [published BOM](https://repo.maven.apache.org/maven2/io/quarkus/platform/quarkus-bom/3.39.4/quarkus-bom-3.39.4.pom).

MCP 1.13.2 is the conservative patch in the existing protocol family. The Quarkus 3.39 migration guide concerns Quarkus Data/Panache Next; this project uses classic `io.quarkus.hibernate.orm.panache`. The selected combination is supported here by executed local tests, not inferred solely from upstream POM compatibility. Sources: [MCP notes](https://docs.quarkiverse.io/quarkus-mcp-server/dev/release-notes.html), [Quarkus 3.39 migration](https://github.com/quarkusio/quarkus/wiki/Migration-Guide-3.39).

UI selections meet the reviewed Router, Vite and Vitest advisory patch floors and compatible peer requirements without requiring React 19. Sources: [Router advisory](https://github.com/remix-run/react-router/security/advisories/GHSA-wrjc-x8rr-h8h6), [Vite advisory](https://github.com/vitejs/vite/security/advisories/GHSA-fx2h-pf6j-xcff), [Vitest advisory](https://github.com/vitest-dev/vitest/security/advisories/GHSA-82fw-gwwq-j7x9). The retained audits below cover the current npm advisory response, not every possible application weakness.

## Runtime and compatibility decision

- Backend/application/MCP/OpenAPI stay **0.8.1-SNAPSHOT**; UI/root lockfile stay **0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. This is dependency correction within the existing uncommitted development increment, not a new product release.
- UI engines are now **`^22.12.0 || ^24.0.0`**; Node 24 LTS is recommended and selected in CI/Docker builder. Node 22 is allowed by dependency requirements but not separately executed here. Existing global Node **25.6.1** was not replaced; it was used only for the historical baseline/intermediate run and emits the expected engine warning after this change.
- Final UI validation and lab checker/browser server used **Node 24.21.0 / npm 11.19.0**, downloaded from the official Node site into a task-specific temporary directory and selected per command. Archive SHA-256 `bed7eea5325e1108f32ce5228ddd6a5f0f08a499ee42aa7442aea583702f6057` matched the official SHASUMS256 file. This checksum check is not independent signature verification. Sources: [Node lifecycle](https://nodejs.org/en/about/previous-releases), [Node release](https://nodejs.org/en/blog/release/v24.21.0).
- Vite 7 changes its default production target to Chrome/Edge 107, Firefox 104 and Safari 16. This is a build target, not an executed browser support matrix. No custom target overrides were introduced. [Vite migration](https://v7.vite.dev/guide/migration).
- CI now includes `npm audit --audit-level=moderate --ignore-scripts`, covering development and runtime dependency nodes. The workflow was edited locally; **GitHub CI and the UI container-image build were not run**.

## Executed validation

| Check | Result |
|---|---|
| Fresh Java 21/jenv baseline before edits | **496 passed**, 0 failures/errors, **9 opt-in ITs skipped**, BUILD SUCCESS |
| Fresh pre-update UI baseline on existing Node 25 | **133 tests / 19 files passed** |
| Updated Java 21/jenv `mvn clean verify` | **496 passed**, 0 failures/errors, **9 opt-in ITs skipped**, BUILD SUCCESS, 2026-09-18 14:29:22 −03:00 |
| Updated UI, intermediate Node 25 | **133 passed**, build passed; engine warning retained, not declared the supported-runtime gate |
| Clean lockfile install and UI on Node 24.21.0 | `npm ci --ignore-scripts` passed; **133 tests / 19 files passed**, production build passed |
| Complete npm audit | **0 reported vulnerabilities**, exit 0; previous review: seven affected dependency nodes |
| Runtime-only npm audit | **0 reported vulnerabilities**, exit 0; previous review: two moderate nodes |
| New CI audit threshold command, executed locally on Node 24 | **0 reported vulnerabilities**, exit 0; not a GitHub workflow execution |
| Real local identity + Prometheus + browser mode | **75 automated checks + post-browser compact-JWT log check passed**, exit 0 after cleanup; browser observations below |
| Real local identity + installation workflow | **83 automated checks + compact-JWT log check passed**, exit 0 after cleanup; synthetic loopback cluster API, not a real cluster |
| Default real local identity/report regression, metrics disabled | **70 automated checks + compact-JWT log check passed**, exit 0 after cleanup |

The Node 24 clean install was repeated after browser cleanup with the same lockfile; no lifecycle scripts ran. The existing `whatwg-encoding` deprecation warning remains; it is not an npm advisory failure. Build warnings about the mixed Quarkus REST server/RESTEasy Classic client and existing unchecked/deprecated source uses remain visible. The separate nine Maven opt-in Keycloak/RHBK/Prometheus ITs were not enabled. Real local labs are separate executions and are not counted as those skipped ITs.

## Browser observations after migration

Observed using the in-app browser at the exact fixture origin `http://127.0.0.1:18300`:

1. Direct A report deep link redirected to the local IdP. Login as disposable `alice-a` returned to the requested A report, with connected event stream.
2. A report showed **PARTIAL**, health **UNKNOWN**, inconclusive assessment and a **COMPLETE performance section** with real JVM/Prometheus samples. Missing infrastructure remained null, not a favorable posture conclusion.
3. Without reloading, a fresh report completed after more than **65 seconds since the already authenticated report observation**, beyond the fixture's 45-second access-token lifetime. Generated timestamps observed: 14:31:46, 14:32:36 and 14:32:59 −03:00. This proves continued authenticated operation beyond the original token lifetime, not immediate revocation.
4. Direct navigation as A to B's report showed `Failed to load target` / not authorized, without a mounted report or another target's data. Logout returned to the IdP sign-in form.
5. Login as `bob-b` showed only **one Fleet target, B**. B's generated report had a **SKIPPED performance section** because its metrics provider is not configured; it did not inherit A's telemetry.
6. Direct navigation as B to A's report was likewise denied with no mounted report. Final logout returned to the IdP form; the temporary test tab was closed before runner cleanup/JWT log verification.

These are fresh positive and bidirectional denied-navigation observations, not exhaustive browser race/revocation/cross-browser coverage. No browser password saving or origin/permission expansion was used.

## Retained evidence and boundaries

- [Selected execution excerpts](evidence/h1-dependency-fix-runs-2026-09-18.txt) retain result lines, observed exit codes, raw-log hashes and cleanup observations. Raw diagnostic logs are temporary local files, not committed secrets or durable CI artifacts. Installation expiry uses explicit disposable-database fault injection; no elapsed-ten-minute or installation-browser validation is claimed for this slice.
- [All-dependency npm audit](evidence/h1-dependency-fix-npm-audit-all-2026-09-18.json) and [runtime-only audit](evidence/h1-dependency-fix-npm-audit-runtime-2026-09-18.json) retain the raw successful responses. The earlier nonzero audit artifacts remain unchanged.
- [Resolved inventory](evidence/h1-dependency-fix-inventory-2026-09-18.json): **231 UI lockfile entries**, **354 packaged JARs**. Includes exact hashes, test-result references, toolchain, comparison and aligned Keycloak clients. No old Quarkus 3.38.1, MCP 1.13.1 or client-common-synced 26.0.11 JAR remains in the inspected package. Inventory SHA-256: `f9d25f2eca4743c9c70aaf6742c6ce83ec14416a67607344301472e2f1149a99`.
- [Source manifest](evidence/h1-dependency-fix-source-2026-09-18.json): **537 files**, aggregate SHA-256 `a4cd60240687a6248d0a7f7df880dae351c54d0bdf6d8b5df44951bbb15f6771`. Scope now includes `.github` configuration in addition to source/tests/scripts/build/fixtures/deploy inputs; excludes generated output, installed dependencies and root/docs documentation. The extra file is a scope expansion for CI, not a new application source file.
- HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch `feature/0.8.1-client-lifecycle`; prior tracked/untracked work preserved. Manifests and logs are local unsigned evidence, not immutable provenance or reproducible-build certification.

Final verification rehashed all **537 source inputs** and **453 inventory file references** without mismatches; retained npm responses byte-match their originals. Independent review found no blocking issue in the selected dependency/configuration changes and confirmed no application/test source migration was needed. AGENTS.md invariants were reviewed and did not require a change in this slice.

Offline documentation validation passed for **123 Markdown documents, 651 local links and 15 milestone specifications**, with zero errors/warnings; the checker also passed six parser self-check groups. This checks local structure/references, not external URLs or milestone completion. `git diff --check` passed.

Final local hygiene: **0 Podman containers, 0 volumes, default network only**, four reusable images preserved; all fixture TCP listeners and the shared lab lock were absent. The task-only downloaded Node runtime/archive was removed after the last lab exited; it can be downloaded again from the cited source. Global Node/npm and jenv selections were untouched; reusable dependency/build caches and diagnostic evidence were retained. No global prune or unrelated resource deletion.

The specific reviewed dependency corrections are locally verified, but **H1/D1 remain OPEN**. No comprehensive backend dependency/SBOM, image, JDK or OS vulnerability scanner ran; npm zero findings is not a whole-platform security certificate. No actual RHBK/OpenShift cluster, native/STDIO packaging, populated production upgrade or container image build/scan was validated. Quarkus 3.39 maintenance is dated, not guaranteed through January: recheck advisories and maintenance before freeze/release.

Next local work remains inventory-warning sanitization and legacy assessment evidence defaults, total-operation/temporal-metrics completeness, broader browser negatives/revocation and the read-only AGT1 prototype. See [H1](../milestones/h1-trust-closure.md), [D1](../milestones/d1-local-workflow.md) and [roadmap](../roadmap.md); D2 requires the separately approved dedicated RHBK/OpenShift environment.
