# H1 authorized Podman recovery and default rerun — 2026-09-18

Status: **PODMAN RECOVERED / 70 FUNCTIONAL CHECKS PASSED / CLEANUP RECOVERED SEPARATELY; RUNNER EXIT 1 RETAINED**.

This is a new execution record after the [failed default run](h1-admin-boundaries-2026-09-18.md)
and [blocked normal-stop attempt](h1-admin-boundaries-recovery-2026-09-18.md).
Those ledgers and their failed results are preserved unchanged.

## Authorization and recovery

The user explicitly authorized forced power-off of `podman-machine-default` after
being told it could interrupt all containers and lose/corrupt data being written.
The exact machine/process arguments were rechecked before execution.

- The same local vfkit control interface accepted `HardStop` with HTTP **202**,
  command exit 0. Podman subsequently reported the machine stopped; its persistent
  disk/configuration were not deleted and the machine was not recreated.
- The first normal start command exited **0** and printed success, but follow-up
  inspection found the VM stopped and its new SSH endpoint refused connections.
  This was not treated as recovery success. Podman automatically reassigned the
  conflicting SSH port from **60082 to 62342**; no product endpoint was changed.
- The old `gvproxy` process **PID 2204** was still attached to this machine's exact
  sockets and prior SSH port. With the VM confirmed stopped, only that stale helper
  received TERM. The next normal start was launched in an independent session;
  its log printed success, machine state was running, and actual Podman container,
  volume, network and image queries succeeded. The detached launch returned PID
  **23422**; no final CLI exit code is inferred for that detached process.
- The machine retains **4 CPUs / 8 GiB / 93 GiB** and its existing disk. No global
  prune, image pull, disk reset, machine recreation or unrelated process kill.
  Successful recovery does not establish the original freeze's root cause or prove
  that abrupt shutdown is safe for arbitrary workloads.

Logs: `/private/tmp/kcops-podman-authorized-hardstop-20260918.log`,
`/private/tmp/kcops-podman-authorized-start-20260918.log`,
`/private/tmp/kcops-podman-authorized-start2-20260918.log`.

## Verified old resources and scoped cleanup

Post-restart inventory contained only the four stopped containers below, no volumes,
the lab/default networks and the four reusable images. Each container was created
at **17:35:17 −03**, matching the failed run. Inspection confirmed the exact Compose
project/config-file/service labels and `io.github.keycloak-operations.purpose=identity-validation`,
expected images and mount layout; names alone were not used as ownership proof.

| Removed container | ID prefix | Image / storage |
|---|---|---|
| `kcops-identity-postgres` | `68fd2c63cc15` | PostgreSQL16; database on tmpfs, no mounted volume |
| `kcops-identity-target-b` | `fdc1805a5623` | Community Keycloak26.7.1; target-b import bind read-only |
| `kcops-identity-idp` | `de1195a0cc35` | Community Keycloak26.7.1; idp import bind read-only |
| `kcops-identity-target-a` | `cc7336567c2c` | Community Keycloak26.7.1; target-a import bind read-only |

The lab network `keycloak-operations-identity-lab_default`, ID prefix **0b66e5449e0c**,
had the matching project label/creation time and no active attachments. All four
containers were removed using their verified full IDs without force; the network
was removed by its full ID. Both commands exited **0**. No volume, image or host
import directory was deleted. Initial inspection used an unsupported `Id` template
field and exited 125 without mutation; corrected `ID` inspection succeeded before
any removal.

Full selected ownership inspection is retained in
`/private/tmp/kcops-podman-recovered-containers-20260918.jsonl` and
`/private/tmp/kcops-podman-recovered-network-20260918.json`; removal output in
`/private/tmp/kcops-podman-recovery-cleanup-20260918.log`.

After old-resource cleanup: **zero containers/volumes**, all **11 fixture ports
free**, shared lock absent. The failed run's disposable database/runtime data are
not recoverable. Source import files, evidence/logs and cached images were retained.

## Same-package default rerun

Before rerunning, verification matched all **571 source inputs / 117 test references /
10 prior run-artifact references**, with no mismatch. Source aggregate remains
`11ba9bf7fda4320263784fde565f87dbb6d3730eab0af21cf85aba00bd0be417`.
The already-built package is reused; no code or build change is needed for recovery.

`bash scripts/validate-identity-lab.sh` ran with its existing process-local
Java **21.0.10 via jenv** and Node **25.6.1** standalone script. It uses the same
four named disposable resources, no cluster and no metrics provider. Raw log:
`/private/tmp/kcops-podman-recovered-default-20260918.log`.
All **70 checks passed**, and the compact-JWT log scan passed. Diagnostic directory:
`/private/tmp/kcops-identity.3BLMvm`. However, automatic cleanup failed with
`ssh: handshake failed: EOF`; the runner exited **1**, not 0. This is not recorded
as an entirely successful runner execution. The prior failed 56-check run is not
overwritten or converted into a pass.

An immediate independent check found the VM still running and Podman responsive,
with only this rerun's four containers active. No second forced restart was needed.
Fresh inspection confirmed creation at **18:05:35 −03**, the same purpose/project
labels, expected images, tmpfs database and read-only imports. The lab network's
four attachments matched only these exact containers:

- PostgreSQL: `79f3ba4ba0d38d2fe9c48fbd6891a08d7e1f9de39dbdb22acd11bdf1d70f53f1`.
- Target A: `935dfce6f4bd8040872323283575c1d9aa7167b3a7e6515d9ee1e7de560baf5c`.
- Target B: `969221dceedc673fd38c7216e9c637c0ba29ab7da5f22b406fcdef69d6f591d4`.
- IdP: `edf38c51a766d6f27ecae946f893ef401b6437d51d188b1e19d576fe676dd823`.
- Network: `abdd2602c2d09ddf74b5e05f0f25a5261afd08d54ee39c4f8847d09b651f878d`.

Repeating only the verified Compose project's cleanup exited **0** and removed
those four containers and their network. No persistent volumes existed and no
images/host imports were removed. This rerun's disposable data are not recoverable;
logs and evidence are retained. Inspection/removal logs use the
`/private/tmp/kcops-podman-rerun-` prefix and are hashed in the evidence manifest.

## Final independent verification

At **21:08:20 UTC (18:08:20 −03)**, independent inventory confirmed:

- Machine running, unchanged 4 CPUs / 8 GiB / 93 GiB, SSH port 62342.
- **Zero containers and zero volumes**; only the default `podman` network.
- Four reusable images with unchanged IDs: Community Keycloak26.7.1 `cc689d358fe6`,
  PostgreSQL16 `02ad0fee02ae`, RHBK26.6 `e7affbc8b409`, Prometheus2.55.1 `f59c592ea6d9`.
- All **eleven fixture ports free** and the shared lock absent.

The [recovery evidence manifest](evidence/h1-admin-boundaries-recovered-2026-09-18.json)
hashes **12 new log/inspection files** and the same three packaged artifacts;
previous 571 source / 117 test / 10 run-artifact references still match. The manifest
separates functional checks, runner failure, subsequent cleanup exit and actual
inventory. These are unsigned local observations, not a signed release or proof of
arbitrary VM data integrity. The first freeze and later SSH EOF cause remain unknown.

Final reference verification matched all **18 recovery references** plus the prior
571 source / 117 test / 10 run-artifact references. Offline documentation checks
passed for **131 Markdown documents, 759 local links and 15 milestone specifications**,
with zero errors/warnings; six parser self-check groups and `git diff --check`
passed. These structural checks do not approve milestone acceptance or external links.

Immediate environment recovery and scoped cleanup are complete. Before treating
the local runner as unattended/reliable, review bounded readiness/cleanup handling
and the intermittent Podman connection failure; do not suppress its nonzero result
or restart more labs solely to obtain a passing exit. No runner code was changed in
this recovery. Broader feature work and milestone acceptance remain separate.

## Versions and remaining gates

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; pre-existing dirty/untracked work is preserved.
Only documentation/evidence changed in this recovery. Backend/application/MCP/OpenAPI
remain **0.8.1-SNAPSHOT**, UI/root lock **0.8.1-dev.0**, report **1.1**, Flyway **V1–V10**.
No architecture, AGENTS invariant, dependency, grant, schema, migration or global
Java/Node change; no commit/push/rebase/tag/release/publication.

Prior **1242 backend tests / 9 skipped opt-in ITs**, **75 metrics** and **83 installation**
checks are still historical evidence, not new runs in this recovery. No browser/UI,
CI, native/image validation or real RHBK/OpenShift acceptance is implied. H1/D1 remain
open for capability/source coverage, metadata trust, browser negatives/revocation and
AGT1; D2 requires the explicitly approved dedicated cluster.
