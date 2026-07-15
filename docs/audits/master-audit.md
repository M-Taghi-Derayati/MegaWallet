# MegaWallet — Master Audit Index

> Central index for the MegaWallet Android production audit. Each linked report is a
> self-contained audit. This file tracks scope, status, and cross-references so future
> audit passes reuse prior findings instead of re-scanning the codebase.

**Repo snapshot:** ~47,200 LOC of Kotlin (main source, excl. tests) · 5 Gradle modules ·
100% Jetpack Compose · Hilt DI · non-custodial multi-chain wallet (EVM / Bitcoin-UTXO / Tron).

## Audit phases & status

| Phase | Report | Status | Notes |
|------|--------|--------|-------|
| 0 | [Documentation cleanup](documentation-audit.md) | ✅ Done | Classification of existing docs |
| 1 | [Android architecture](android-architecture-audit.md) | ✅ Done | This phase |
| 2 | [Compose / UI](compose-audit.md) | ✅ Done | Font-alloc, lifecycle collection, theming, a11y |
| 3 | [Performance](performance-audit.md) | ✅ Done | Main-thread runBlocking; UI recompose cost |
| 4 | [Memory & CPU](memory-cpu-audit.md) | ✅ Done | Key retention on lock; cache TTL overflow |
| 5 | [Networking](networking-audit.md) | ✅ Done | Release cleartext+user-CA trust; no pinning; idempotency |
| 6 | [Security](security-audit.md) | ✅ Done | Strong at-rest/backup crypto; release-transport & key-binding gaps |
| 7 | [Production readiness (runtime)](../runtime/production-readiness-audit.md) | ✅ Done | Process-death, realtime loss, notif perm, matrix |
| — | [Technical debt](technical-debt.md) | ✅ Done | **46 items (TD-01…TD-46)** rolled up |
| — | [Master task register](../tasks/master-task-register.md) | ✅ Done | 24 consolidated tasks (Sprint 0–6) |
| — | [Execution roadmap](../roadmaps/execution-roadmap.md) | ✅ Done | Sprint sequencing + release gates |
| — | [Strategic roadmap](roadmap.md) | ✅ Done | Now/Next/Later + pre-release gate |

Legend: ✅ done · 🟡 partial/seeded · ⏳ pending approval.

## Documentation map (for future agents)

```
docs/
  audits/       ← Phase 1–6 reports + technical-debt (canonical debt) + strategic roadmap
  runtime/      ← Phase 7 production-readiness audit
  tasks/        ← TASK-01…07 specs + master-task-register (canonical tasks) + README index
  roadmaps/     ← execution-roadmap (canonical build order)
  architecture/ ← overview.md (module graph, mechanisms, ADR backlog)
  performance/, security/  ← index pointers into the canonical audits (no duplicated content)
```
Start here → this file. Debt → `audits/technical-debt.md`. What to build → `tasks/master-task-register.md`.
Order → `roadmaps/execution-roadmap.md`. Architecture facts → `architecture/overview.md` + repo `CLAUDE.md`.

## Roll-up — top items across all phases

**Release ship-blockers (do first — low effort, high value):**
1. 🔴 Network-security config permits **cleartext + user-CA trust in release** (S-1/N-1, TD-34).
2. 🟠 No `FLAG_SECURE` on seed/private-key screens (S-3, TD-36).
3. 🟠 `allowBackup="true"` with empty rules (S-6, TD-38).
4. 🟠 Hardcoded weak `APP_SECRET_KEY` (S-4, TD-37).
5. 🟠 Main-thread `runBlocking` at cold start (P-1, TD-19).

**Structural (schedule deliberately):**
- 🟠 `:domain` not framework-free (TD-02); god files (TD-01); logic-in-UI (TD-04).
- 🟠 185 inline `FontFamily` allocations / unwired typography (TD-12); no lifecycle-aware collection (TD-13).
- 🟠 Auth-bind the Keystore key + clear keys on lock (TD-35/TD-23); TLS + pinning (TD-30/TD-29).

**Correctness (from Phase-1 code review, still open):** multi-asset price param, chart lexicographic
sort, Tron PROXY feeLevel, L1 fee units, socket reconnect, idempotency double-fund (TD-31).

## Method & token discipline

- Audits are **read-only**. No production code is modified, refactored, deleted, or patched.
- Each phase runs independently and **reuses** facts recorded in earlier reports rather than
  reopening already-analyzed files.
- Findings use a fixed shape: **Severity · Impact · Reason · Suggested Solution · Estimated Difficulty**.
- Severity scale: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low.

## How to run the next phase

Phases run one at a time and **wait for explicit approval** before starting. See
[docs/prompts/](../prompts/) for the per-phase prompts.
