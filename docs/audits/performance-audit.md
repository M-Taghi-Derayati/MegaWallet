# Performance Audit (Phase 3)

**Scope:** runtime performance — thread blocking, coroutine concurrency vs sequential I/O,
redundant computation/I-O, startup work, dispatcher discipline, and the runtime cost of the UI
patterns quantified in Phase 2.
**Out of scope:** memory retention/leaks (Phase 4), networking protocol/TLS (Phase 5).
**Method:** read-only whole-source pattern scan + targeted spot-reads. No code modified.

---

## Executive Summary

Concurrency hygiene is **better than typical**: structured concurrency with `awaitAll` is used in
the per-chain data sources (`EvmDataSource`, `BitcoinDataSource`, `TronDataSource`),
`WalletBalanceSynchronizerImpl`, and `MultiWalletViewModel`; dispatcher usage is disciplined
(35 `Dispatchers.IO`, 2 `Default`, **no** heavy `Main` work, **no** `GlobalScope`, **no**
`Thread.sleep`); the synchronous Google Drive SDK is correctly wrapped in `withContext(IO)`; and
config resolution is offline-first with a disk cache + cheap version probe.

The performance debt is **narrow and specific**, plus one **broad runtime cost inherited from the
UI layer**:

1. **Main-thread blocking at cold start** — `MainScreenViewModel`'s field initializer calls
   `connectionModeProvider.currentMode()`, which does `runBlocking { preferences.read() }` on the
   **main thread** during ViewModel construction (the only two `runBlocking` calls in the codebase
   are both here).
2. **UI recomposition/allocation cost** (from Phase 2): 185 per-recomposition `FontFamily`
   allocations and 0 lifecycle-aware collectors → sustained GC/CPU during scroll/animation and
   wasted recomposition while backgrounded. This is the largest *continuous* cost.
3. **A sequential balance loop** in `ProxyChainDataSource` (N × RTT) where the DIRECT sources
   already parallelize.
4. **O(pages²) history re-normalization** on paginated scroll.

There are **no** systemic threading disasters (no main-thread network, no blocking sync HTTP on
the UI thread, no `GlobalScope` leaks). Fixing items 1–4 is mostly local and mechanical.

**Verdict:** 🟢 Healthy concurrency foundation with 🟠 a cold-start main-thread stall and inherited
UI recomposition cost as the top items.

---

## Evidence (whole-source scan)

| Signal | Result | Reading |
|--------|--------|---------|
| `runBlocking` | 2 (both in `DefaultBlockchainConnectionModeProvider`) | contained, but on a main-thread path |
| `GlobalScope` / `Thread.sleep` | 0 / 0 | good |
| sync OkHttp `.execute()` | 4 (all `GoogleDriveDataSource`, all in `withContext(IO)`) | correct |
| `async {` / `awaitAll` / `.await()` | 8 / 15 / 20 | structured concurrency present |
| `withContext(` | 26 | dispatcher offloading used |
| `Dispatchers` | 35 IO · 2 Default · 0 heavy Main | disciplined |
| `getValidatedConfig()` callers | 1 (startup warm-up) | no hot-path repetition today |

---

## Strengths

- **Parallel fan-out where it matters:** per-address/per-token balance reads in the DIRECT sources
  and multi-wallet aggregation use `awaitAll`, not sequential loops.
- **Dispatcher discipline:** IO-bound work is consistently moved off the main thread with
  `withContext(Dispatchers.IO)`, including the blocking Drive SDK.
- **Offline-first config** with disk cache + `/config/version` probe + secp256k1 verification, and
  a **good caching template already exists** in `CapabilityManager` (in-memory snapshot + TTL +
  `Mutex`).
- **No pathological primitives:** no `GlobalScope`, `Thread.sleep`, or main-thread network.
- **Single Coil `ImageLoader`** with bounded memory/disk cache (per-item loaders removed).

---

## Problems

Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low. Each: **Impact · Reason · Suggested Solution ·
Estimated Difficulty**.

### High 🟠

#### P-1 — Main-thread `runBlocking` preferences read during ViewModel construction
- **Severity:** 🟠 High
- **Impact:** On cold start / first main-screen composition, the UI thread blocks on a
  DataStore/SharedPreferences read. It's a StrictMode disk-read-on-main violation and is
  ANR-adjacent under slow storage; it delays first frame of the primary screen.
- **Reason:** `MainScreenViewModel.kt:33` initializes
  `_connectionMode = MutableStateFlow(connectionModeProvider.currentMode())` as a **field**, and
  `DefaultBlockchainConnectionModeProvider.currentMode()` resolves its first (uncached) value via
  `runBlocking { userPreferencesRepository.getConnectionMode() }`.
- **Suggested Solution:** Seed asynchronously — start the `StateFlow` with a default and update it
  from `viewModelScope` (`launch { _connectionMode.value = provider.awaitMode() }`), or hydrate the
  provider's cache once off-main at app start. Remove `runBlocking` from any main-reachable path.
- **Estimated Difficulty:** Low.

#### P-2 — UI recomposition & allocation cost (inherited from Phase 2)
- **Severity:** 🟠 High (continuous runtime cost)
- **Impact:** 185 inline `FontFamily(Font(...))` allocations recur on every recomposition, and 0
  `collectAsStateWithLifecycle` means flows drive recomposition even when backgrounded — together
  the dominant *sustained* CPU/GC cost on scrolling lists (history) and animated screens
  (`GeneratingAnimation`, `MainScreen` morph layers).
- **Reason:** See Phase 2 CU-1 (unwired `Typography`) and CU-3 (no lifecycle collection).
- **Suggested Solution:** Implement Phase-2 CU-1 and CU-3 (centralize fonts;
  `collectAsStateWithLifecycle`). Highest runtime-per-effort payoff.
- **Estimated Difficulty:** Medium (fonts) + Low (collection swap). Cross-ref TD-12, TD-13.

### Medium 🟡

#### P-3 — Sequential per-address balance fetch in PROXY mode
- **Severity:** 🟡 Medium
- **Impact:** `getBalancesForMultipleAddresses` loops addresses serially → latency ≈ N × RTT. A
  10-address wallet on a 200 ms relayer takes ~2 s instead of ~200 ms; the DIRECT sources already
  parallelize the same work, so PROXY mode feels slower for no reason.
- **Reason:** Plain `for (address in addresses)` with a suspend call per iteration
  (`ProxyChainDataSource.kt` ~line 85), instead of `awaitAll` or the existing batch endpoint.
- **Suggested Solution:** Fan out with `coroutineScope { addresses.map { async { … } }.awaitAll() }`
  (matching the DIRECT sources) or use the batch balances endpoint.
- **Estimated Difficulty:** Low.

#### P-4 — O(pages²) history re-normalization on pagination
- **Severity:** 🟡 Medium
- **Impact:** Each "load more" re-normalizes the entire accumulated list, so scrolling deep history
  redoes prior work; "load more" gets progressively slower and runs on the flow-update path.
- **Reason:** `TransactionHistoryViewModel.applyUnifiedPage` calls the normalize use case on
  `_transactions.value + page.items` every page.
- **Suggested Solution:** Normalize only the new page and merge (or sort/dedup incrementally).
- **Estimated Difficulty:** Medium.

### Low 🟢

#### P-5 — `ConfigManager.getValidatedConfig()` has no in-memory memoization
- **Severity:** 🟢 Low (latent)
- **Impact:** Each call does a disk read plus a `/config/version` network probe (and possibly a
  full fetch + secp256k1 verify). Currently harmless — it's called **once** at startup — but
  becomes repeated I/O the moment more callers are added (e.g. wiring dynamic config into the
  registries).
- **Reason:** Unlike `CapabilityManager`, `ConfigManager` keeps no in-memory snapshot/TTL.
- **Suggested Solution:** Add an in-memory snapshot + short TTL (mirror `CapabilityManager`) before
  broadening callers.
- **Estimated Difficulty:** Low.

---

## Recommended Order

1. **P-1** — remove main-thread `runBlocking` (cold-start first-frame win, trivial).
2. **P-2** — fonts + lifecycle collection (largest sustained cost; shared with Phase 2).
3. **P-3** — parallelize PROXY balances.
4. **P-4** — incremental history normalization.
5. **P-5** — memoize config before its callers multiply.

## Cross-references
- P-2 depends on Phase-2 CU-1/CU-3 (TD-12, TD-13).
- Retention/leak aspects of long-lived singletons and coroutine scopes → Phase 4 (Memory/CPU).
- New debt appended to `technical-debt.md` as TD-19…TD-22.

_Phase 3 complete. Awaiting approval before starting Phase 4 (Memory & CPU)._
