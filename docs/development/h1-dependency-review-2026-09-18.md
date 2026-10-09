# H1 dependency review — 2026-09-18

Status: **REVIEW RECORDED / REMEDIATION OPEN**. This is a bounded npm advisory query and backend inventory/primary-source review, not a comprehensive backend vulnerability scan or a clean security certification. No dependency, package lock, permission, container, commit or release was changed by this review.

Related gate: [H1 trust closure](../milestones/h1-trust-closure.md). Working-tree reference: HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, with pre-existing dirty and untracked work preserved. Backend baseline uses **Java 21 through jenv**, coordinated separately; this read-only dependency review did not execute or claim another Java baseline or any remediation test. Product versions remain backend `0.8.1-SNAPSHOT` and UI `0.8.1-dev.0`.

## Method and retained evidence

The npm queries ran in `ui/` with Node **25.6.1** and npm **11.9.0**. They were repeated with bounded network retries/timeouts to retain the raw JSON. This is the observed workstation runtime, not a new recommended or minimum Node version.

```bash
npm audit --json --ignore-scripts --fetch-retries=0 --fetch-timeout=10000
npm audit --json --omit=dev --ignore-scripts --fetch-retries=0 --fetch-timeout=10000
```

Both commands returned exit **1** because advisories were reported:

| Query | Reported affected package nodes |
|---|---|
| All locked dependencies | **7**: 5 moderate, 1 high, 1 critical |
| Runtime dependencies only | **2 moderate**, no high/critical |

These counts are dependency nodes, **not seven unique vulnerabilities or proven exploit paths**. The raw responses retain registry advisory ranges/severities. Applicability below is a separate source/configuration assessment, not suppression or acceptance of those findings. An optional `npm view` candidate-version metadata request stalled and was cancelled (exit 130); no candidate upgrade was installed or validated.

Evidence was initially captured in `/private/tmp/kcops-dependency-review.zULy8x/` and copied byte-for-byte into the repository evidence directory below. These are unsigned, uncommitted local artifacts, not an immutable or signed attestation. Inventory capture time: **2026-09-18T17:03:18.648Z**. It contains **228 lockfile package entries** and the filenames/hashes of **353 packaged runtime/boot JARs**, read without invoking another Maven build. The [failure/bounds ledger](h1-failure-bounds-2026-09-18.md) records the separate code and regression work; these dependency inputs remained unchanged.

| Artifact | SHA-256 |
|---|---|
| [All-dependency audit JSON](evidence/h1-dependencies-2026-09-18-npm-audit-all.json) | `2f8b768c598caf7fd45ee8cc3e958fe0223a08b19477391bc997fd38b1423d82` |
| [Runtime-only audit JSON](evidence/h1-dependencies-2026-09-18-npm-audit-runtime.json) | `5322f230944ed4879b2d7fcc813d8648856206182ec6451c60bf8e0eb38d6c47` |
| [Resolved inventory](evidence/h1-dependencies-2026-09-18-resolved-inventory.json) | `8eae627a37eebcfe61d34162f062ed6c411122bfc14dd708cc1480b3077d8b93` |

Dependency source hashes at review time:

| Source | SHA-256 |
|---|---|
| [pom.xml](../../pom.xml) | `131895cdd7b31ec7bf21a15cf1078fe8d560541ae3e877cfec7df86b453a3440` |
| [UI package manifest](../../ui/package.json) | `a5c048e7c5765bf9185f1b561c23baa2f1ebde6e8854240deedcbc05177a57a2` |
| [UI lockfile](../../ui/package-lock.json) | `105bccffb19c901f8a55dcf22474b54ab5a2c5323725c48bfa769e1bb1c948c3` |

## Exact UI versions and advisory triage

| Component | Locked version | Scope |
|---|---|---|
| keycloak-js | 26.2.4 | Runtime |
| react / react-dom | 18.3.1 | Runtime |
| react-router / react-router-dom | 6.30.4 | Runtime |
| @remix-run/router | 1.23.3 | Runtime |
| vite | 5.4.21 | Development |
| vitest / @vitest/mocker | 2.1.9 | Development |
| esbuild / rollup | 0.21.5 / 4.62.4 | Development |
| @vitejs/plugin-react | 4.7.0 | Development |
| jsdom / typescript | 25.0.1 / 5.9.3 | Development |

| Maintainer advisory | Observed applicability and boundary | Published patch floor, not a validated upgrade |
|---|---|---|
| [React Router redirect/XSS, GHSA-jjmj-jmhj-qwj2](https://github.com/remix-run/react-router/security/advisories/GHSA-jjmj-jmhj-qwj2) | Installed `react-router-dom 6.30.4` is affected. Current navigation uses fixed internal prefixes and encoded IDs; no exploitable application path demonstrated. | v6 patch `6.30.6` addresses this advisory, not the following two. |
| [React Router unexpected external navigation, GHSA-wrjc-x8rr-h8h6](https://github.com/remix-run/react-router/security/advisories/GHSA-wrjc-x8rr-h8h6) | `react-router 6.30.4` is in the affected range. Current internal links and strict return-path allowlist reduce exposure to attacker-supplied navigation; dependency finding remains open. | `>=7.18.0`; controlled major migration. |
| [React Router SSR hydration, GHSA-337j-9hxr-rhxg](https://github.com/remix-run/react-router/security/advisories/GHSA-337j-9hxr-rhxg) | Version matches, but inspected app uses `createRoot`, not SSR/manual hydration. Required rendering path was not found; not applicable to that inspected path. | `>=7.18.0`. |
| [Vite optimized-dependency source-map traversal, GHSA-4w7w-66w2-5vf9](https://github.com/vitejs/vite/security/advisories/GHSA-4w7w-66w2-5vf9) | Vite `5.4.21` matches. Development-server issue; loopback restricts network exposure but does not patch vulnerable code. Not a static production-bundle issue. | `6.4.2`, `7.3.2`, `8.0.5`; later advisories require newer patches. |
| [Vite Windows alternate-path denial bypass, GHSA-fx2h-pf6j-xcff](https://github.com/vitejs/vite/security/advisories/GHSA-fx2h-pf6j-xcff) | Version matches; Windows/NTFS conditions absent on this macOS host. Still relevant to supported developer platforms. | `6.4.3`, `7.3.5`, `8.0.16`. |
| [launch-editor/Vite Windows NTLM disclosure, GHSA-v6wh-96g9-6wx3](https://github.com/vitejs/launch-editor/security/advisories/GHSA-v6wh-96g9-6wx3) | Version matches; requires Windows/NTLM plus reachable editor middleware. Windows conditions absent here. | Same Vite patch floors as previous row. |
| [esbuild development serving CORS, GHSA-67mh-4wv8-2f99](https://github.com/evanw/esbuild/security/advisories/GHSA-67mh-4wv8-2f99) | `0.21.5` matches. No direct esbuild serving invocation found; compilation via Vite does not itself establish the vulnerable serving path. | `>=0.25.0`; prefer coherent Vite upgrade over arbitrary transitive overrides. |
| [Vitest UI/API arbitrary file access/execution, GHSA-5xrq-8626-4rwp](https://github.com/vitest-dev/vitest/security/advisories/GHSA-5xrq-8626-4rwp) | `2.1.9` matches; npm severity critical. Current `vitest run`/jsdom configuration does not expose UI/API or enable Windows Browser Mode. Do not enable those surfaces as a workaround. | Maintainer/npm older patch floors differ slightly; select a version also covering the next advisory. |
| [Vitest/mocker redirect mock traversal, GHSA-82fw-gwwq-j7x9](https://github.com/vitest-dev/vitest/security/advisories/GHSA-82fw-gwwq-j7x9) | `vitest` and `@vitest/mocker 2.1.9` match. No standalone mocker/interceptor plugin configuration found. Unauthenticated issue requires the relevant reachable development WebSocket; ordinary browser-mode registration has a different authenticated boundary. | `4.1.11`; maintainers do not plan fixes for older 2.x/3.x branches. |

`vite-node` is also reported through vulnerable Vite, not as an independent advisory. Source/configuration observations used [Vite configuration](../../ui/vite.config.ts), [UI entrypoint](../../ui/src/main.tsx), [router](../../ui/src/routes/index.tsx), [return-path validation](../../ui/src/auth/returnPath.ts) and inspected navigation call sites. The return path permits only allowlisted internal routes; it rejects backslashes/external paths. This is containment, not dependency remediation or an exhaustive browser exploit test.

## Exact backend inventory and primary-source review

| Packaged component | Actual version |
|---|---|
| Quarkus | 3.38.1 |
| Quarkiverse MCP HTTP/core/SSE client | 1.13.1 |
| Keycloak Admin client | 26.0.12 |
| Keycloak client-common-synced | **26.0.11** |
| Fabric8 Kubernetes/OpenShift | 7.8.0 |
| Vert.x / Netty | 4.5.30 / 4.1.136.Final |
| Jackson core/databind | 2.22.0 |
| RESTEasy Classic client | 6.2.16.Final |
| PostgreSQL JDBC | 42.7.13 |
| SnakeYAML / SnakeYAML Engine | 2.4 / 3.0.1 |
| SmallRye JWT / jose4j | 4.6.3 / 0.9.6 |

### Quarkus

- **Maintenance gap:** official support table marks 3.38 community maintenance ended **2026-08-26**. Installed 3.38.1 is also behind 3.38.3. Updating only to 3.38.3 would not restore a maintained branch or resolve the following OIDC advisory. Sources: [release/support table](https://quarkus.io/releases/), [3.38.3 release](https://quarkus.io/blog/quarkus-3-38-3-released/).
- **Conditional OIDC exposure:** `quarkus-oidc 3.38.1` matches [GHSA-qfvj-8whj-27w4](https://github.com/quarkusio/quarkus/security/advisories/GHSA-qfvj-8whj-27w4), fixed in 3.39.2 and listed maintenance branches. Exploit requires multiple OIDC provider tenants sharing an enabled token-introspection cache. Inspected repository configuration uses one Identity A tenant, no tenant resolvers and no enabled introspection cache. Multiple target Keycloak environments are not multiple Quarkus OIDC tenants. This is conditional exposure, **not a demonstrated cross-target bypass**; deployment overrides require separate review.
- **Patched at installed version:** maintainer advisories explicitly list **3.38.1** as patched for [path normalization GHSA-qcxp-gm7m-4j5v](https://github.com/quarkusio/quarkus/security/advisories/GHSA-qcxp-gm7m-4j5v) and [multipart-header OOM GHSA-xrmg-9xc7-83fp](https://github.com/quarkusio/quarkus/security/advisories/GHSA-xrmg-9xc7-83fp). Earlier [authorization bypass](https://github.com/quarkusio/quarkus/security/advisories/GHSA-rc95-pcm8-65v9), [thread starvation](https://github.com/quarkusio/quarkus/security/advisories/GHSA-5rfx-cp42-p624) and [duplicated-context leakage](https://github.com/quarkusio/quarkus/security/advisories/GHSA-9623-mj7j-p9v4) advisories list older affected versions. These specific advisories are not unresolved findings merely because Quarkus is installed.
- **Affected packages absent:** [Spring Web header binding](https://github.com/quarkusio/quarkus/security/advisories/GHSA-vv4c-mhvm-c6gv) and [Qute reflection/SSTI](https://github.com/quarkusio/quarkus/security/advisories/GHSA-prf4-p7fp-fr79) extensions are absent from this POM/packaged runtime.

### MCP and Keycloak clients

- **MCP patch available, not a confirmed CVE:** [maintainer release notes](https://docs.quarkiverse.io/quarkus-mcp-server/dev/release-notes.html) list **1.13.2**, correcting HTTP transport runtime configuration, debug-notification log-level handling and schema/argument issues. Current application does not explicitly set the affected transport runtime properties. No specific published security advisory was successfully retrieved for this extension. That retrieval limit is not evidence of absence of vulnerabilities.
- **Keycloak alignment caveat:** Quarkus BOM 3.38.1 overrides transitive `keycloak-client-common-synced` to **26.0.11** despite explicit Admin client26.0.12. Confirmed by packaged JARs and local Maven POMs. [Official downloads](https://www.keycloak.org/downloads) list Admin client26.0.12 and JS26.2.4; this does not mean every resolved Java client artifact is26.0.12. The [26.0.12 security change](https://www.keycloak.org/2026/08/keycloak-client-26012-released) concerns [policy-enforcer PathConfigMatcher](https://github.com/keycloak/keycloak-client/issues/234). That artifact/class is absent here. Record dependency-family alignment work, not an invented Admin-client vulnerability.

## Open remediation and acceptance

1. Upgrade UI routing and Vite/Vitest in coherent, separately reviewed changes; check Node compatibility and peer dependencies, repeat all UI tests/build and authenticated browser negatives. Do not use `npm audit fix --force` to choose a migration.
2. Select a maintained Quarkus BOM compatible with the MCP extension and align Keycloak client artifacts. Repeat Java 21 build, exact resolved inventory and real-token REST/MCP/SSE/installation labs. No target version was selected or tested by this review.
3. Retain before/after audits and add a selected comprehensive backend dependency/SBOM scan before claiming full advisory coverage. Preserve justified applicability decisions with explicit scope, rather than hiding findings.
4. Until remediation, keep development servers loopback-only, do not expose Vitest UI/API, and do not introduce additional OIDC tenants/introspection caching without dedicated review. These restrictions are containment, not a security waiver.

**H1 dependency acceptance remains OPEN.** No backend-wide, container-image, JDK or OS vulnerability scanner ran; no exploit reproduction, package upgrade, package installation, changed product version or dependency-fix validation occurred. Current advisories can change after this dated review; refresh them before a release or externally exposed demonstration.
