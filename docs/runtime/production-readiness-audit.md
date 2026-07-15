# Phase 7 — Production Readiness Audit (Runtime)

**Mode:** read-only. **Stance:** senior Android Staff Engineer shipping a crypto wallet.
**Method:** reuses Phases 1–6 in full (cross-referenced, not repeated). Only areas **not yet deeply
validated** were freshly probed: process-death/config-change resilience, realtime event-loss &
backpressure, runtime notification delivery, offline behavior, and Android-version/ANR/StrictMode.
No production code modified.

> Prior phases (do not re-read): [architecture](../audits/android-architecture-audit.md) ·
> [compose](../audits/compose-audit.md) · [performance](../audits/performance-audit.md) ·
> [memory/cpu](../audits/memory-cpu-audit.md) · [networking](../audits/networking-audit.md) ·
> [security](../audits/security-audit.md).

---

## Executive Summary

The app is **architecturally production-shaped** (clean layering, structured concurrency, hardware-
backed key storage, offline-first config) but is **not yet production-ready** for a money-moving
release. The release-blocking gaps are already captured by the Phase-0 gate (TASK-01…TASK-07).
Phase 7 adds a small set of **runtime/reliability** gaps that were not visible from static review of
individual layers:

- **PR-1 (High) — Weak process-death restoration.** State-based navigation + only
  10 `rememberSaveable` / 2 `SavedStateHandle` means a low-memory process kill drops nav state and
  in-flight flows (selected tab/asset, in-progress send/confirm, import progress). ViewModels survive
  rotation but **not** process death.
- **PR-2 (Medium) — Realtime event loss/backpressure.** The socket exposes
  `MutableSharedFlow(replay = 1)` with no `extraBufferCapacity`/`onBufferOverflow`; bursts or a
  late/slow collector can miss intermediate events (a skipped `tx.status.changed`/`balance.updated`).
- **PR-3 (Medium) — Notifications may be silently suppressed on Android 13+.** `POST_NOTIFICATIONS`
  is declared and `checkSelfPermission`-guarded (no crash), but no runtime **request** was found — so
  push/local notifications never appear until the user is asked.
- **PR-4 (Low) — No StrictMode in debug**, so the main-thread `runBlocking` (P-1) and disk-on-main
  slipped through unflagged.
- **PR-5 (Low–Med) — Readiness-matrix gaps:** large-font and tablet/foldable layouts unvalidated;
  RTL inconsistent (CU-11); dark mode via 135 hardcoded colors (CU-5).

Everything else in Parts 1–10 resolves to **already-tracked** findings; this report cross-references
rather than restating them.

**Verdict:** 🟠 Runtime foundation is sound; ship-readiness gated by the Phase-0 blockers plus PR-1…PR-3.

---

## Parts 1–6 — resolves to prior phases (no new deep-dive)

| Part | Coverage | Net-new? |
|------|----------|----------|
| 1 Runtime performance / startup | Cold-start main-thread `runBlocking` = **P-1/TD-19**; config warm-up non-blocking; DI graph large but Hilt-lazy | + **PR-4** (no StrictMode) |
| 2 Memory runtime | Key retention on lock **M4-1/TD-23**; `ASSETS_TTL` overflow **TD-24**; no Context leaks; caches bounded | + **PR-1** (state lifetime on process death) |
| 3 CPU | Repeated in-composition formatting **CU-4/TD-14**; O(pages²) history **P-4/TD-21**; sequential balances **P-3/TD-20**; BigDecimal/crypto costs normal | none new |
| 4 Compose runtime | 185 `FontFamily` allocs **CU-1/TD-12**; no lifecycle collection **CU-3/TD-13**; god-composables **CU-2**; keys OK | none new |
| 5 Animation | Animation-heavy screens flagged as watch item (Phase 4 CPU) | see PR-5 note |
| 6 Coroutines | `awaitAll` used; only 2 `runBlocking` (P-1); no `GlobalScope`; socket scope cancels jobs | + **PR-2** (SharedFlow buffering) |

---

## Part 7 — Realtime (new validation)

- **Reconnect/heartbeat:** present (`pingInterval`, heartbeat job, `scheduleReconnect`), but the
  `connect()` guard can defeat the post-sign-in retry and backoff is duplicated — **N-6 / code review**.
- **PR-2 — Event loss / backpressure (NEW):** `NotificationSocketManager._events =
  MutableSharedFlow<SocketEvent>(replay = 1)` (also `GlobalEventBus` replay=1). With
  `extraBufferCapacity = 0` and default `SUSPEND` overflow, a burst on the socket thread can suspend
  emission, and a subscriber that attaches after two quick events only replays the **last** — an
  intermediate `tx.status.changed`/`balance.updated` can be missed. Dedup (`EventDeduplicationCache`)
  handles WS+FCM duplicates but does **not** recover a dropped event.
- **Ordering:** no explicit sequence/version on events; UI relies on last-write. Acceptable if the
  server is authoritative and the client re-syncs on reconnect — **confirm a reconnect re-sync** (a
  full history/balance refresh) exists, else dropped events persist until manual refresh.
- **Offline recovery:** socket defers while unauthenticated (good) but see N-6 (may not re-arm).

## Part 8 — Offline (new validation)

- `NetworkConnectionInterceptor` fails fast when offline (no silent hang). No general **outbox/queue**
  for normal sends (expected for a wallet — signed txs aren't queued blindly); `PendingGaslessTxStore`
  tracks pending gasless tx for status follow-up.
- Weak-network UX is bounded by **35 s** timeouts (Phase 5) — long spinners on flaky links; consider
  shorter connect timeouts + explicit retry affordance.
- DIRECT/PROXY both reachable; parity bugs (Tron feeLevel, multi-asset price) tracked in code review.
- **Recommendation:** document expected offline behavior (fail-fast + manual retry) and ensure a
  **reconnect/online-transition re-sync** for balances & history (ties to PR-2).

## Part 9 — Wallet runtime (reuse + spot-checks)

- **Send/gasless/sponsor:** fund-safety idempotency gap **TASK-07/TD-31**; `SendConfirmScreen` fee
  tabs on hardcoded data **CU-9**; Tron PROXY `feeLevel` unmapped & multi-asset price param — code review.
- **Balance/history:** O(pages²) normalization **TD-21**; sequential proxy balances **TD-20**.
- **Lock/unlock/biometric/session:** app-lock is UI-only; keys not cleared on lock **TASK-06/TD-23**;
  Keystore key not auth-bound **TD-35**; session JWT host-scoped (good).
- **Config/migration/feature-flags:** offline-first `ConfigManager` (secp256k1-verified) +
  `CapabilityManager`/`FeatureAvailabilityResolver` — **healthy**; no Room DB, so no schema-migration
  surface; prefs are Keystore-encrypted. `ASSETS_TTL` overflow **TD-24** affects config/asset cache freshness.
- **Multi-wallet/import/export:** switch clears key cache correctly (`ActiveWalletManager`); cloud
  backup password-encrypted (Phase 6 strength).

## Part 10 — Production Readiness Matrix

| Dimension | Status | Evidence / action |
|-----------|--------|-------------------|
| Battery | 🟡 | No lifecycle-aware collection (**CU-3**) → background recomposition/collection drains battery |
| Memory | 🟡 | Key retention (**TD-23**), `ASSETS_TTL` overflow (**TD-24**) |
| CPU | 🟡 | 185 font allocs (**TD-12**), O(pages²) history (**TD-21**) |
| Offline | 🟡 | Fail-fast; needs online-transition re-sync (PR-2/Part 8) |
| Background / Foreground | 🟠 | **PR-1** process-death restoration; keys not cleared on background (**TD-23**) |
| Configuration change | 🟢/🟡 | ViewModel state survives rotation; local `remember` UI state does not (see PR-1) |
| Dark mode | 🟡 | 135 hardcoded colors bypass theme (**CU-5**) |
| RTL | 🟡 | Inconsistent per-site overrides (**CU-11**) — Persian app, needs a pass |
| Large font / display scaling | 🔴 unknown | **Not validated** — fixed `.sp`/`.dp` + god layouts risk clipping (PR-5) |
| Accessibility | 🟡 | 70 `contentDescription = null` (**CU-6**) |
| Tablets / Foldables | 🔴 unknown | **Not validated** — single-pane, hardcoded widths (PR-5) |
| Low RAM | 🟠 | Process-death (PR-1); large god-composables |
| Android 8–15 (minSdk 26 / target 36) | 🟡 | `POST_NOTIFICATIONS` runtime request missing (**PR-3**, A13+); predictive-back opted in with custom nav (PR-6); edge-to-edge on |
| Crash recovery | 🟠 | PR-1; verify no crash on cold deep-link into a killed flow |
| ANR risk | 🟠 | Main-thread `runBlocking` (**P-1/TD-19**) |
| StrictMode | 🔴 | **Not enabled** (**PR-4**) |

## Part 11 — Blind spots (net-new)

1. **PR-1 process-death restoration** — biggest untested reliability risk (Play reviewers kill+restore).
2. **PR-2 realtime event loss** — silent missed status/balance updates.
3. **PR-3 notifications not requested** — realtime feature effectively off on A13+ until asked.
4. **PR-4 no StrictMode** — the class of bug that hid P-1.
5. **PR-5 large-font / tablet / foldable** — never rendered/validated; likely layout breakage in
   god-composables with fixed dimensions.
6. **PR-6 predictive back** — `enableOnBackInvokedCallback="true"` with custom `BackHandler` nav;
   verify gesture animation/behavior, especially overlay/sheet dismissal.
7. **Reconnect re-sync** — confirm balances/history fully refresh on online/socket-reconnect.

---

## New debt (appended to register): TD-41…TD-46
See `../audits/technical-debt.md`. Task specs: `../tasks/master-task-register.md`.
Execution order: `../roadmaps/execution-roadmap.md`.

_Phase 7 complete (audit portion). Master task register + roadmap generated. Awaiting approval before
any implementation._
