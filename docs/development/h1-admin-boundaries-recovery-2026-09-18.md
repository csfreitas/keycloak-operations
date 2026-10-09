# H1 local Podman recovery attempt — 2026-09-18

Status: **BLOCKED — NORMAL STOP DID NOT COMPLETE; FORCED POWER-OFF REQUIRES EXPLICIT APPROVAL**.

This follows the [failed default laboratory](h1-admin-boundaries-2026-09-18.md).
That ledger, the failed 56-check attempt and its evidence manifests are preserved.
The user approved restarting the local Podman machine, cleaning only the failed
lab's disposable resources and repeating the default scenario.

## Observed recovery attempt

The exact machine is `podman-machine-default`, AppleHV, 4 CPUs / 8 GiB / 93 GiB.
Process arguments identified its matching virtual disk and local vfkit control
interface at `http://localhost:60435`; no ambient cluster was accessed.

1. `podman machine stop podman-machine-default` remained blocked without output.
   Only that identified CLI invocation (PID 20591) was terminated; its observed
   exit was 143. Log: `/private/tmp/kcops-podman-recovery-stop-20260918.log`.
2. Read-only vfkit state returned `VirtualMachineStateRunning`. Its documented
   normal `POST /vm/state` with `state=Stop` returned HTTP **202**, command exit 0.
   Log: `/private/tmp/kcops-podman-recovery-vfkit-stop-20260918.log`.
   Acceptance of the request did **not** establish shutdown.
3. At **2026-09-18 20:52:01 UTC (17:52:01 −03)** the control interface still
   returned `VirtualMachineStateRunning`; Podman metadata still reported running.
4. A proposed `HardStop` was rejected **before execution** by the execution safety
   review: restart authorization did not explicitly cover abrupt VM-wide power-off
   and its possible data-loss impact. No indirect workaround, hypervisor kill,
   forced deletion, disk reset or machine recreation was attempted.

API operation semantics were checked against the primary
[vfkit usage documentation](https://github.com/crc-org/vfkit/blob/main/doc/usage.md#restful-api).
The actual observed HTTP 202/state above take precedence over an example status in
documentation. A forceful power-off can interrupt writes in every workload in the
machine and may lose/corrupt data; persistent disk retention is not a safety guarantee.

## Evidence and unchanged scope

Read-only validation matched the prior **571 source inputs, 117 test references and
10 run/artifact references**, with no hash mismatch. Source aggregate remains
`11ba9bf7fda4320263784fde565f87dbb6d3730eab0af21cf85aba00bd0be417`.
HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`; accumulated dirty/untracked
work is preserved. Only documentation changes in this recovery attempt.

No new Maven, UI/browser or laboratory execution occurred. The previous **1242
backend tests / 9 skipped opt-in ITs**, metrics **75** and installation **83** checks
are historical results, not new runs. The default scenario and cleanup remain
incomplete. Final container/volume/network/image inventory is still unverified;
no resource was removed by this attempt and no clean-environment claim is made.

No code, architecture, AGENTS workflow, dependencies, grants, migration or runtime
setting changed. Versions remain backend/application/MCP/OpenAPI `0.8.1-SNAPSHOT`,
UI/root lock `0.8.1-dev.0`, report schema 1.1, Flyway V1–V10. No commit/push/tag/release.

## Required handoff

Ask explicit permission for forced power-off of this exact local VM, explaining
possible data loss and interruption of all its containers. Do not force it under
the earlier normal-restart permission. If approved and connectivity recovers,
inspect the four exact `kcops-identity-*` containers and the named lab network from
the original ledger, remove only verified disposable lab resources, preserve
reusable images/volumes, repeat the default lab on the verified package and check
runner exit/JWT scan plus final inventory, eleven ports and lock. No global prune.
H1/D1 remain open; neither recovery nor local tests establish RHBK/OpenShift acceptance.
