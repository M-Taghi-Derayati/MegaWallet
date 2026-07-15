# Execution Roadmap (Phases 1–7)

Implementation order for the [master task register](../tasks/master-task-register.md). Sequenced to
unblock release first, then de-risk structurally. **Nothing here is implemented yet** — this is the
plan. Estimates are ideal dev-days.

## Dependency-aware sequencing rules
- **Backend-coordinated** (do early to unblock): TASK-04 (attestation), TASK-07 (idempotency dedup),
  TASK-11 (relayer TLS). Kick off backend conversations at Sprint 0.
- **Enablers first:** TASK-12 (domain purity) before TASK-14 (use cases); TASK-08/09 before TASK-13/17.
- **Risky/migration:** TASK-19 (auth-bound key) needs a migration + feature flag; schedule with buffer.

---

## Sprint 0 — Release Readiness Hardening  (gate — must all pass to ship)
**Goal:** safe, stable, and **measurable** before Beta — no known remote/trivial exploit, no
fund-safety or cold-start ANR risk, crashes/leaks observable, release build proven, QA signed off.
Full scope + category audit: `docs/tasks/sprint-0/SPRINT-0-README.md`.

**0a — Security gate**
- ✅ Done: TASK-02 `FLAG_SECURE` · TASK-03 disable backup · TASK-05 remove main-thread `runBlocking` ·
  TASK-01 NSC debug-only (release close-out via TASK-11).
- ⏸ Held: TASK-06 clear keys on lock (needs unlock re-hydration + on-device test) ·
  TASK-04 secret/attestation · TASK-07 stable idempotency (both backend-coordinated).

**0b — Observability & safety nets** *(land immediately, independent)*
- TASK-25 crash reporting → TASK-26 StrictMode (after TASK-05) → TASK-27 LeakCanary.

**0c — Release verification & baseline**
- TASK-29 signed-release R8/secrets/logging smoke test · TASK-28 Baseline Profile + Macrobenchmark
  (measurement foundation for Sprint 1).

**0d — QA gate (last)**
- TASK-30 production QA matrix on ≥2 devices (debug w/ StrictMode+LeakCanary, then signed release).

**Exit criteria:** release HTTPS-only-by-construction · no seed screenshot · no cleartext backup ·
StrictMode-clean cold start · crash reporting live w/ mapping upload · LeakCanary-clean core flows ·
signed release runs all 7 wallet flows without R8 crashes · baseline metrics recorded · QA matrix
green · (fund-endpoint idempotency + no compiled-in secret once TASK-04/07 land with backend).
**Est:** ~5.75 dev-days security gate + ~4 dev-days hardening/verification/QA (+ backend for 04/07).

## Sprint 1 — Performance
**Goal:** kill sustained runtime cost.
- TASK-08 fonts → TASK-09 lifecycle collection → TASK-10 balances/history.
- **Exit:** no per-recomposition font allocs; no background collection; single-RTT balances.
- **Est:** ~3 dev-days.

## Sprint 2 — Architecture
**Goal:** restore clean boundaries; shrink the blast radius of future change.
- TASK-12 domain purity → TASK-14 logic→use-cases → TASK-13 decompose god files (rolling).
- **Exit:** `:domain` android-free; top-3 files < ~400 LOC; VMs orchestrate only.
- **Est:** ~10 dev-days (TASK-13 spans sprints).

## Sprint 3 — Compose / Runtime UX
**Goal:** reliability + correctness the user sees.
- TASK-15 process-death restoration · TASK-16 correctness cluster · TASK-17 a11y/dark/RTL/large-font/tablet.
- **Exit:** kill/restore keeps critical flow; code-review defects have regression tests; readiness matrix
  greens for dark/RTL/large-font/tablet.
- **Est:** ~7 dev-days.

## Sprint 4 — Security (post-gate hardening)
**Goal:** make the lock cryptographic; harden transport.
- TASK-11 TLS + pinning · TASK-19 auth-bound key (migration) · TASK-20 cache hygiene.
- **Exit:** pinned HTTPS; secret decrypt requires recent auth; caches bounded, clients disposed.
- **Est:** ~5.5 dev-days.

## Sprint 5 — Cleanup / Reliability
**Goal:** consolidate and make realtime dependable.
- TASK-18 DIRECT/PROXY routing · TASK-22 realtime buffer + reconnect re-sync · TASK-23 notif permission.
- **Exit:** single transport-routing point; no realtime event loss; notifications work on A13+.
- **Est:** ~4.5 dev-days.

## Sprint 6 — Optional Improvements
**Goal:** hygiene & polish.
- TASK-21 build/dev-infra (StrictMode, repos, logging, PBKDF2) · TASK-24 reuse/simplification.
- **Est:** ~2 dev-days.

---

## Release gates
- **Alpha (internal):** Sprint 0 + Sprint 1 complete; Sprint 3 TASK-16 (correctness) done.
- **Beta (closed):** Sprints 0–3 complete; Sprint 4 TASK-11 done.
- **Production:** Sprints 0–5 complete; Sprint 6 optional. Full production-readiness matrix green;
  StrictMode-clean; process-death & reconnect re-sync verified on device.

## Total
~38 dev-days engineering (excl. backend work for TASK-04/07/11). Critical path runs through Sprint 0
(backend items) → Sprint 4 TASK-19 migration.
