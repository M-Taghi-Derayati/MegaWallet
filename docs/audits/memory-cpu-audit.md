# Memory & CPU Audit (Phase 4)

**Scope:** heap retention & leaks — coroutine scope lifetimes, retained `Context`, unbounded/
mis-configured caches, sensitive-material lifetime, and CPU cost of long-running/animation work.
**Out of scope:** security depth of key handling (Phase 6 — cross-referenced), recomposition cost
detail (Phase 2/3 — cross-referenced).
**Method:** read-only pattern scan + targeted reads. No code modified.

---

## Executive Summary

Baseline memory hygiene is **good**: injected singletons consistently use `@ApplicationContext`
(no Activity-context leaks found), the event-dedup cache is TTL-bounded and self-pruning, the
chain/asset registries load once into bounded maps, and the notification socket's process-lifetime
scope cancels its heartbeat/reconnect jobs on `disconnect()`. There is **no** `GlobalScope`, and
Compose uses `rememberCoroutineScope()` correctly.

The findings are concentrated in **cache lifecycle**, and one is important for a wallet:

1. **Decrypted private keys are not cleared on app-lock.** `KeyManager.credentialsCache` holds
   `web3j.Credentials` (decrypted signing keys) for the active wallet. The only calls that clear it
   (`ActiveWalletManager.lockWallet()` → `keyManager.clearCache()`) fire on **wallet delete/switch**
   (`WalletRepositoryImpl:373,450`) — **not** on app-lock. The app-lock flow is a UI/state gate with
   no reference to `KeyManager`, so locking/backgrounding leaves keys resident in the heap for the
   process lifetime. This is both a retention and a **security** concern (Phase 6).
2. **`CacheManager.ASSETS_TTL` is ~10⁶× too large** (≈ 13,700 years), so asset-cached entries never
   expire in memory or on disk — permanent retention plus staleness.
3. Minor: the app cache has no size cap (lazy-only eviction), and process-static Web3j client caches
   are never `shutdown()`.

CPU: no runaway loops in business logic (no `Thread.sleep`, no busy-waits found); the meaningful
CPU cost is the **UI recomposition/allocation** already quantified in Phase 2/3, plus
animation-heavy screens to watch.

**Verdict:** 🟢 Solid leak hygiene, with 🟠 sensitive-key retention across lock as the item that
matters most.

---

## Strengths

- **No Context leaks.** 16 injection sites use `@ApplicationContext`; the handful of non-annotated
  `Context` references are function parameters (asset loading, registry init) or Compose
  `LocalContext.current` — none retained by singletons.
- **Bounded, self-pruning dedup cache.** `EventDeduplicationCache` prunes expired ids on every call
  (5 s TTL) and exposes `clear()`.
- **Bounded registries.** `BlockchainRegistry`/`AssetRegistry` maps load once from config and are
  sized by the network/asset catalog.
- **Socket scope discipline.** `NotificationSocketManager` (`@Singleton`, process-lifetime scope)
  cancels `reconnectJob` and stops the heartbeat on `disconnect()`.
- **`KeyManager` has the right primitives** (`clearCache()`, `loadKeysIntoCache()` clears first) —
  they are simply not wired to the lock lifecycle (see M4-1).

---

## Problems

Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low. Each: **Impact · Reason · Suggested Solution ·
Estimated Difficulty**.

### High 🟠

#### M4-1 — Decrypted key material retained in memory across app-lock
- **Severity:** 🟠 High (memory retention of sensitive data; security cross-ref Phase 6)
- **Impact:** After the user locks or backgrounds the app, `KeyManager.credentialsCache`
  (`ConcurrentHashMap<Long, Credentials>`) still holds the active wallet's **decrypted private
  keys** in the heap until wallet switch/delete or process death. Enlarges the window for
  heap-dump/forensic exposure and keeps sensitive objects alive longer than the "unlocked" session.
- **Reason:** `clearCache()`/`lockWallet()` are invoked only from wallet delete/switch
  (`WalletRepositoryImpl.kt:373,450`). The app-lock mechanism (`AppLockViewModel`,
  `usecase/security/*`, `session/*`) is a pure `isLocked` UI/state gate — grep shows **no**
  reference to `KeyManager`/`clearCache`/`lockWallet` anywhere in security/session code.
- **Suggested Solution:** Wire the lock transition (observe `isLocked` / app-background) to
  `ActiveWalletManager.lockWallet()` (or at least `keyManager.clearCache()`), and re-hydrate on
  successful unlock. Phase 6 should additionally consider zeroing key buffers.
- **Estimated Difficulty:** Low–Medium (hook one existing method into the lock flow).

### Medium 🟡

#### M4-2 — `CacheManager.ASSETS_TTL` overflow makes cached assets effectively immortal
- **Severity:** 🟡 Medium (retention + staleness/correctness)
- **Impact:** `ASSETS_TTL = 5 * 24 * 3600 * 1_000_000L` ≈ 4.32×10¹⁴ ms ≈ **13,700 years**. Anything
  cached with this TTL never expires in memory or on disk, so those entries are retained for the
  whole process and never refreshed (stale asset data persists indefinitely).
- **Reason:** Unit error — the intent was 5 days in ms (`5*24*3600*1000`); the trailing factor is
  `1_000_000` instead of `1_000`.
- **Suggested Solution:** Fix the constant to `5 * 24 * 3600 * 1000L`; consider a sane upper bound.
- **Estimated Difficulty:** Low (one constant).

### Low 🟢

#### M4-3 — App cache has no size cap; eviction is lazy-only
- **Severity:** 🟢 Low
- **Impact:** `CacheManager.memoryCache` evicts an entry only when it is next *accessed* (or via an
  explicit `clearExpired()` that nothing appears to schedule). Entries put and never re-read linger
  until process death; there is no max-size/LRU guard, so a growing key space would grow memory.
- **Reason:** No `LruCache`/size bound; no periodic `clearExpired()` scheduling.
- **Suggested Solution:** Back the memory tier with `LruCache` (bounded entries) and/or schedule
  `clearExpired()`. Low urgency given today's bounded key set.
- **Estimated Difficulty:** Low.

#### M4-4 — Process-static Web3j/HttpService caches never shut down
- **Severity:** 🟢 Low
- **Impact:** `EvmDataSource`, `TronDataSource`, `BitcoinDataSource`, and `DirectGaslessChainReader`
  keep `companion`/static maps of `Web3j`/`HttpService` keyed by RPC URL. Each retains a connection
  pool + scheduled executor for the process lifetime; none are `shutdown()`. Bounded by the number
  of RPC URLs (small), but native/thread resources are never released.
- **Reason:** Clients cached for reuse without a disposal path.
- **Suggested Solution:** Acceptable given the bound; if RPC URLs ever become dynamic/large, add an
  LRU with `shutdown()` on eviction.
- **Estimated Difficulty:** Low.

#### M4-5 — Verify `WalletSessionAuthCoordinator` scope is singleton-scoped
- **Severity:** 🟢 Low (verify)
- **Impact:** It holds a manual `CoroutineScope(SupervisorJob()+IO)` and is `start()`-ed from
  `MainActivityCompose.onCreate`. If the type is **not** `@Singleton`, Activity recreation
  (rotation/config change) could create a new coordinator + scope while the previous jobs (JWT mint,
  WS connect) keep running — duplicate auth/WS work and a retained scope.
- **Reason:** Manual scope + Activity-initiated `start()`; scoping not confirmed in this pass.
- **Suggested Solution:** Ensure `@Singleton` (or lifecycle-cancel the scope); make `start()`
  idempotent.
- **Estimated Difficulty:** Low.

### CPU (cross-referenced, no new primitive issues)

- No `Thread.sleep`, busy-waits, or `GlobalScope` in business logic. The dominant CPU cost is UI:
  **185 per-recomposition `FontFamily` allocations** and non-lifecycle collection (Phase 2 CU-1/CU-3,
  Phase 3 P-2). **Watch item:** animation-heavy screens (`GeneratingAnimation` ~1,477 LOC,
  `MainScreen` morph layers) — confirm frame-driven animation (`withFrameNanos`/`Animatable`) rather
  than tight loops during Phase-2 UI decomposition.

---

## Recommended Order
1. **M4-1** — clear keys on lock (security + memory; highest value).
2. **M4-2** — fix `ASSETS_TTL` overflow.
3. **M4-3 / M4-4 / M4-5** — bounded caches, client disposal, confirm coordinator scoping.

## Cross-references
- M4-1 feeds directly into the **Security** audit (Phase 6: key lifetime, zeroing).
- CPU items reuse Phase-2 CU-1/CU-3 and Phase-3 P-2 (TD-12/TD-13).
- New debt appended to `technical-debt.md` as TD-23…TD-27.

_Phase 4 complete. Awaiting approval before starting Phase 5 (Networking)._
