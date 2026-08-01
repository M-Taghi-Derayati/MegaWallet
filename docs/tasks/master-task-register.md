# Master Task Register (Phases 1–7)

Single source of truth for implementation work. Merges all findings (TD-01…TD-46), **de-duplicated**
and **consolidated** into executable tasks. The seven Phase-0 gate tasks keep their full specs in
their own files ([TASK-01…TASK-07](README.md)); everything else is specced densely below.

**Field key:** ID · Title · Problem · Root Cause · Files · Modules · Deps · Difficulty · Est · Risk ·
Priority · Steps · Acceptance · Rollback · Regression · Testing. **Priority:** P0 blocker · P1 high ·
P2 medium · P3 low. **Est** in ideal dev-days.

**Dedup/merge log:** TD-12 (font) folds CU-1; TD-13 = lifecycle collection; TD-04/CU-4 (in-composition
logic) merged into TASK-14; correctness code-review items merged into TASK-16; TD-05/M-1 transport
routing merged with N-5 into TASK-18; TD-25/26/27 folded into TASK-20 (cache hygiene); TD-09/S-10
(repos) + TD-44 (StrictMode) folded into TASK-21 (build/dev-infra); S-5/N-2 pinning = TASK-11.

---

## Sprint 0 — Release Readiness Hardening (gate)

Scope, non-goals, and the full category audit: [`sprint-0/SPRINT-0-README.md`](sprint-0/SPRINT-0-README.md).

### 0a — Security gate (specs in `TASK-01…07`)
| ID | Title | TD | Pri | Est | Risk | Status |
|----|-------|----|-----|-----|------|--------|
| [TASK-01](TASK-01-network-security-config.md) | NSC cleartext + user-CA trust → debug-only | 34 | P0 | 0.5 | Med (needs relayer TLS) | ✅ Implemented |
| [TASK-02](TASK-02-flag-secure.md) | `FLAG_SECURE` on seed/key/passcode screens | 36 | P0 | 0.5 | Low | ✅ Implemented |
| [TASK-03](TASK-03-disable-allowbackup.md) | Disable/scope auto-backup | 38 | P0 | 0.25 | Low | ✅ Implemented |
| [TASK-04](TASK-04-app-secret-key.md) | Remove dead `APP_SECRET_KEY`; device attestation wired | 37 | P2 | 1 | Low | ✅ Implemented — dead `APP_SECRET_KEY` removed; `DEVICE_ATTEST_HMAC_SECRET` per-env (testnet embedded, mainnet via prop); `HmacUtils` = server 2-level scheme; **04a wired**: `verify()` does `/device-challenge`→2-level HMAC→`/verify` with **soft fallback** (never breaks login); secret injected (tests default `""`); attested-path test added. **Verify on-device** (`deviceVerified=true` + a gas-credit call non-401); set mainnet secret at mainnet launch. |
| [TASK-05](TASK-05-remove-mainthread-runblocking.md) | Remove main-thread `runBlocking` | 19 | P0 | 0.5 | Low | ✅ Implemented |
| [TASK-06](TASK-06-clear-keys-on-lock.md) | Clear key cache on lock/background (scoped) | 23 | P0 | 0.5 | Low→Med | ✅ Implemented — new app-layer `WalletLockKeyCoordinator` enforces "keys cached **iff** unlocked AND foreground": clears `KeyManager` cache on lock/background, re-hydrates off-main via `loadExistingWallet()` on unlock/foreground. Foreground signal comes from **`ProcessLifecycleOwner`** (same as the realtime socket coordinator), so a rotation/config-change doesn't churn keys. Clears **only** the credential cache (keeps JWT session + wallet UI state — no socket churn). Gated on app-lock enabled (no-passcode users unaffected). Unit tests added. **Verify on-device** (heap-dump/`am kill` after lock shows no `Credentials`; signing works after unlock). TD-35/TASK-19 = auth-bound-key follow-up. |
| [TASK-07](TASK-07-stable-idempotency-key.md) | Stable per-operation idempotency key | 31 | P0 | 1.5 | Med | ✅ Implemented — `IdempotencyInterceptor` now derives the key from request content (SHA-256 of method+path+body); byte-identical retry → same key; test updated. Gasless `/relay` already uses a session-stable key. |
| [TASK-31](sprint-0/TASK-31-relayer-https-cutover.md) | Point client at live HTTPS relayer (`wallet.intexchange.ir`) | 30 | P0 | 0.25 | Low | ✅ Implemented — `RELAYER_*` → `https://wallet.intexchange.ir` / `wss://…/ws`; debug NSC host → `103.112.69.154`. Pinning = TASK-11. |

### 0b — Release-readiness hardening (specs in `sprint-0/`)
| ID | Title | TD | Pri | Est | Risk | Status |
|----|-------|----|-----|-----|------|--------|
| [TASK-25](sprint-0/TASK-25-crash-reporting.md) | Crash & error reporting (observability) | 47 | P0 | 0.75 | Low | ✅ Implemented — Crashlytics wired (`google-services.json` added) + global crash/coroutine safety net; collection off in debug. **Verify PII-scrub** on first captured report. |
| [TASK-26](sprint-0/TASK-26-strictmode-debug.md) | StrictMode in debug (implements TD-44) | 44 | P1 | 0.25 | Low | ✅ Implemented |
| [TASK-27](sprint-0/TASK-27-leakcanary-debug.md) | LeakCanary in debug | 48 | P1 | 0.25 | Low | ✅ Implemented |
| [TASK-28](sprint-0/TASK-28-performance-baseline-infra.md) | Baseline Profile + Macrobenchmark (measure only) | new | P2 | 1.25 | Low | 🆕 Planned |
| [TASK-29](sprint-0/TASK-29-release-build-verification.md) | Release build verification (R8/secrets/logging) | 09,33 | P0 | 1 | Med | 🆕 Planned |
| [TASK-30](sprint-0/TASK-30-production-qa-matrix.md) | Production QA manual test matrix | 06 | P0 | 1.5 | Low | 🆕 Planned |

_TASK-26 supersedes the StrictMode slice of TASK-21; TASK-29 folds TD-09 (repos) + TD-33 (logging).
TASK-21 (Sprint 6) now covers only PBKDF2 iterations (TD-39) + reuse cleanup. No duplicate tasks created._

---

## Sprint 1 — Performance

### TASK-08 — Centralize fonts into theme (kill 185 FontFamily allocations) — ✅ Implemented (verify visual parity)
- **Status:** ✅ Implemented (commit c9f649c) — new `common_ui/theme/Fonts.kt` holds hoisted, process-once
  font-family `val`s; all ~185 inline `FontFamily(Font(R.font.x, …))` sites across 33 UI files now reference
  them, and the now-unused `Font`/`FontFamily`/`FontWeight` imports were removed. Each `val` is a **verbatim
  hoist** of the exact inline expression (same resource + same declared `FontWeight`), so single-font
  faux-bold synthesis and rendering are byte-identical — the only collapse is `Font(x)` ≡
  `Font(x, FontWeight.Normal)`. The custom multi-weight **IranSans** family is also wired into `Typography`
  (was `FontFamily.Default`). Covers IranSans (light/regular/bold), Inter (regular/medium/bold), Vazirmatn
  (medium/bold). **Not built here** (Gradle unavailable) — inspection-verified. **Verify on-device:** visual
  parity light/dark on key screens (numerals via Inter, Persian via IranSans); the `Typography` default
  change specifically needs a screenshot pass. `grep 'FontFamily(Font(' app/**/ui` is now 0.
- **Problem:** 185 inline `FontFamily(Font(...))` re-allocate per recomposition. **Root cause:**
  `Typography` uses `FontFamily.Default`; custom fonts never wired. **TD:** 12 (CU-1).
- **Files:** `common_ui/theme/Type.kt`, all `ui/**` Text call sites. **Modules:** common_ui, app. **Deps:** none.
- **Difficulty:** Med · **Est:** 1.5 · **Risk:** Low · **Priority:** P1.
- **Steps:** define `val IranSans/Inter = FontFamily(...)` once; wire into `Typography`; replace inline
  constructions with `MaterialTheme.typography.*` or the shared vals.
- **Acceptance:** grep for `FontFamily(Font(` ≈ 0 in `ui/**`; visual parity light/dark.
- **Rollback:** revert theme + call sites (pure UI). **Regression:** font weights/sizes drift. **Testing:**
  screenshot diff on 5 key screens (light/dark), Layout Inspector recomposition counts.

### TASK-09 — Lifecycle-aware state collection — ✅ Implemented
- **Status:** ✅ Implemented — all `collectAsState()` call sites (9 files: MainActivityCompose,
  MainScreen, TransactionHistoryScreen, Send/SendConfirm, AssetDetail, MultiWallet, Receive, Wallet)
  converted to `collectAsStateWithLifecycle()`; `androidx.compose.runtime.collectAsState` imports
  swapped for `androidx.lifecycle.compose.collectAsStateWithLifecycle`. Dep (`lifecycle-runtime-compose`)
  was already on the app classpath. All no-arg (StateFlow) collectors, so no `initialValue` needed.
- **Problem:** 45 `collectAsState()`, 0 lifecycle-aware → background recomposition/battery. **Root
  cause:** wrong collector API. **TD:** 13 (CU-3). **Files:** all `ui/**` collectors. **Modules:** app.
- **Difficulty:** Low · **Est:** 0.5 · **Risk:** Low · **Priority:** P1.
- **Steps:** replace `collectAsState()` → `collectAsStateWithLifecycle()`.
- **Acceptance:** 0 `collectAsState()` remaining; no state read regressions. **Rollback:** revert.
  **Regression:** state not updating while foreground (verify). **Testing:** background/foreground a
  live screen; confirm no collection while stopped (log/Studio energy profiler).

### TASK-10 — Parallelize proxy balances + incremental history normalize — ✅ Implemented
- **Status:** ✅ Implemented:
  - ✅ **Parallel proxy balances** — `ProxyChainDataSource.getBalancesForMultipleAddresses` was a
    sequential `for` loop (N addresses → N serial RTTs). Now fans the per-address calls out via
    `coroutineScope { … async … }.await()`, so a multi-address wallet refreshes in ~1 RTT wall-time.
    Per-address failure isolation is unchanged (error → `emptyList`, matching DIRECT); duplicate
    addresses collapse via `.distinct()`. The direct sources' single-address batch endpoint (`/balances/batch`)
    was intentionally **not** repurposed here — its multi-network/wallet shape is used by a different path
    and swapping it in would change response mapping (higher risk for the same win).
  - ✅ **Incremental history normalize** — `TransactionHistoryViewModel.applyUnifiedPage` re-normalized the
    entire accumulated list on every appended page (`normalize(all + newPage)`), re-running per-item catalog
    lookups over already-processed rows → ~O(pages²). Added `NormalizeTransactionHistoryUseCase.merge(existing,
    newItems, address)` that reconciles/filters **only the new page** then unions → dedupes → sorts against the
    already-normalized list. `invoke` was refactored onto shared `reconcileAndFilter` + `dedupeAndSort` helpers
    with **zero behavior change**; a unit test asserts `merge` is output-identical to the full-normalize path
    (ordering, dedup "existing wins", pending-first, zero-amount drop).
  - **Test note:** the existing `multi-address balances isolate per-address failures` test was made
    order-agnostic (concurrent calls draw the FIFO mock responses nondeterministically); it now asserts the
    isolation property (one resolved, one empty, no propagated error) rather than which address got which.
- **Problem:** sequential N×RTT balances; O(pages²) history. **TD:** 20, 21 (P-3, P-4).
- **Files:** `ProxyChainDataSource.getBalancesForMultipleAddresses`,
  `TransactionHistoryViewModel.applyUnifiedPage`. **Modules:** data, app. **Deps:** none.
- **Difficulty:** Low–Med · **Est:** 1 · **Risk:** Med (concurrency) · **Priority:** P1.
- **Steps:** `awaitAll`/batch endpoint for balances; normalize only the appended page and merge.
- **Acceptance:** balance refresh ≈ single RTT; per-page normalize is O(page). **Rollback:** revert per file.
  **Regression:** balance failure-isolation per address; history ordering/dedup. **Testing:** unit tests
  for merge; multi-address wallet timing; deep-scroll history.

### TASK-05b — (see TASK-05; cold-start) — already Sprint 0.

---

## Sprint 2 — Architecture

### TASK-12 — Make `:domain` framework-free
- **Problem:** `:domain` depends on `androidx.core.ktx`, `material`, Hilt. **Root cause:** android.library
  module + annotations in domain. **TD:** 02. **Files:** `domain/build.gradle.kts`, domain annotations.
  **Modules:** domain (ripples to data/app DI). **Deps:** none (do early).
- **Difficulty:** Med · **Est:** 2 · **Risk:** Med (build) · **Priority:** P1.
- **Steps:** convert to `kotlin("jvm")`; strip android/material; move Hilt bindings to data/app.
- **Acceptance:** `:domain` has zero android imports; builds; unit-testable on JVM. **Rollback:** revert
  gradle. **Regression:** DI graph breaks (compile-time caught). **Testing:** full build + existing tests.

### TASK-13 — Decompose top god files (incremental) — ✅ Implemented (top-3 screens; VMs deferred)
- **Status:** ✅ Implemented for the three god *screens* (reviewed 2026-07-21):
  `SendConfirmScreen` (commit 8c5b433) and `SendScreen` (7e3012d) split into focused components;
  `MainScreen` 1178→723 (c08216c) by extracting `MainHeader`/`MainBottomNavigation`/`MorphingFabMenu`
  into their own files (verbatim, stateless, immutable params, light/dark `@Preview` added, no dup
  defs, same-package call sites resolve); a compile-correctness follow-up qualified `AnimatedVisibility`
  in the send split (5109872). `MainDashboardContent` intentionally left intact (owns the morphing-bounds
  animation + ~15 interdependent overlay-nav state vars — splitting risks recomposition/behavior). The
  **>1000-line ViewModels** are the remaining deferred slice. Not built here — inspection-verified.
- **Problem:** `SendConfirmScreen` 1977, `MainScreen` 1152, `SendScreen` 1472, VMs >1000. **TD:** 01,
  CU-2. **Files:** those. **Modules:** app. **Deps:** TASK-08/09 ease this.
- **Difficulty:** High · **Est:** 5 (spread) · **Risk:** Med · **Priority:** P2.
- **Steps:** extract stable sub-composables with immutable state slices; per-file, behind previews.
- **Acceptance:** no UI/VM file > ~400 LOC for the top 3. **Rollback:** per-file revert. **Regression:**
  UI behavior/recomposition. **Testing:** screenshot + manual flow per screen.

### TASK-14 — Move formatting/business logic into use cases — 🟢 Core done (call-site cleanup deferred)
- **Status:** 🟢 Architectural core implemented (commit 1ede891): extracted the ~25 pure display-formatting
  methods out of the 1140-line `TransactionHistoryViewModel` into a new injected, stateless
  `TransactionDisplayFormatter` (app/viewmodel/history). The VM keeps only state + orchestration and
  delegates each `format*`/`get*` call, so the public API — and every composable call site — is unchanged
  (behavior-preserving; zero UI risk). The formatter is a pure function of its inputs + the injected
  read-only catalogs; VM-owned runtime context (on-demand fee detail, USD prices, address book) is passed
  in explicitly, so it's unit-testable and callers can `remember(...)` off the relevant keys. New
  `TransactionDisplayFormatterTest` (JVM/MockK) covers the fee placeholder-vs-genuine-zero split, +sign on
  incoming, status/type/primary labels, address-book counterparty resolution, pending-duration bucketing,
  explorer URL, asset-title symbol fallback, tron-energy fallback. Dropped the dead `resolveAssetIconUrl`.
  Not built here (Gradle unavailable) — inspection-verified.
- **Deferred:** the literal acceptance ("no `viewModel.format*/get*` in composition bodies") — i.e. have
  composables read a precomputed immutable display model instead of the VM delegators. Low value / higher
  churn, and the perf concern (CU-4) is already handled by `remember()` on the list rows + the single-tx
  detail sheet; best done with a build. Send-stack + other-VM formatting also remain for a later pass.
- **Problem:** formatting/tier-mapping/normalization in VMs/composables (31 in-composition calls). **TD:**
  04, CU-4. **Files:** `TransactionHistoryViewModel`, send stack, `domain/usecase/**`. **Deps:** TASK-12.
- **Difficulty:** Med–High · **Est:** 3 · **Risk:** Med · **Priority:** P2.
- **Steps:** introduce use cases; VMs emit precomputed immutable UI-state; `remember(key)` at minimum.
- **Acceptance:** no `viewModel.format*/get*` in composition bodies. **Rollback:** per unit. **Regression:**
  display strings. **Testing:** use-case unit tests + UI spot check.

---

## Sprint 3 — Compose / Runtime UX

### TASK-15 — Process-death restoration (state-based nav) — ✅ Implemented
- **Status:** ✅ Implemented — the state-based nav now survives a low-memory kill:
  - **`MainScreenViewModel`** mirrors the two pieces of navigation state — `selectedTab` (as
    `MainTab.name`) and `selectedAssetId` — into `SavedStateHandle`. The `MutableStateFlow`s stay the
    reactive source of truth but are **seeded from the handle on recreate**, and every setter
    (`selectTab`, `onAssetClicked`, `onNavigateBack`) writes the value back through. Restoring an
    unknown tab name falls back to `MainTab.WALLET` (`runCatching`), so a stale/renamed enum can't crash
    cold restore.
  - **`MainScreen`** promotes the five full-screen overlays (`showSendScreen`, `showReceiveScreen`,
    `showMultiWalletScreen`, `showCreateWalletScreen`, `showImportWalletScreen`) + `sendInitialAssetId`
    from `remember` to **`rememberSaveable`**, so a kill/restore lands back on the open overlay, not the
    dashboard. Purely visual affordances (header/FAB expansion) intentionally stay in plain `remember`.
  - Mid-flow scratch data (`pendingImportData`, `pendingCloudRestore`) left transient — they're
    re-entered on the restored screen and aren't trivially Saveable; the overlay itself restoring is the
    correctness win.
  - **Verify on-device:** Developer Options → *Don't keep activities* (and `adb shell am kill`), navigate
    to History / an asset detail / the Send overlay, background+return → lands on the same screen, no crash.
- **Problem:** low-memory kill loses nav state + in-flight flows. **Root cause:** state-based nav, thin
  `rememberSaveable`/`SavedStateHandle`. **TD:** 41 (PR-1). **Files:** `MainScreen`, `MainScreenViewModel`,
  send/import VMs. **Modules:** app. **Deps:** none.
- **Difficulty:** Med · **Est:** 2 · **Risk:** Med · **Priority:** P1.
- **Steps:** persist tab/selectedAsset + critical flow state via `SavedStateHandle`; `rememberSaveable`
  for transient UI; restore on recreate.
- **Acceptance:** "Don't keep activities" + kill/restore lands on the same screen with intact critical
  state; no crash on cold restore. **Rollback:** revert. **Regression:** nav/back behavior. **Testing:**
  Developer Options → Don't keep activities; `adb shell am kill`; deep-link into killed flow.

### TASK-16 — Correctness cluster (code-review defects)
- **Status:** 🟡 Partially implemented (2 of 6 — the safe, non-money, unambiguous fixes):
  - ✅ **Chart lexicographic sort** — `AssetDetailScreen` computed y-axis min/max with
    `maxBy/minBy { it.second }` on the price *String* (e.g. "9.5" > "10.5"), distorting the chart.
    Now `maxOf/minOf { it.second.toDouble() }`.
  - ✅ **Multi-asset price param** — `MarketDataRepositoryImpl.fetchLatestPricesFromCoinDesk` multi-asset
    branch called `getPrices(idsString)` positionally, binding ids to `search` and leaving `ids` null →
    wrong/empty prices. Now always `getPrices(ids = idsString)`; dead `symbolString` removed.
  - ✅ **Tron PROXY feeLevel** (2026-07-17, Tron-priority pass) — `ProxyChainDataSource` sent the **raw
    Persian tier label** («عادی»/«کند»/«سریع») to the prepare endpoint on the **TRON native** path
    (`sendTron`) and the **EVM contract-call** path, while only the EVM token path translated it to the
    backend's machine keys (`slow`/`standard`/`fast`). So TRON sends silently fell back to the server
    default tier. Centralized the mapping in `toBackendFeeLevel(feeLevel)` (Persian→machine, machine
    pass-through, unknown→`standard`) and applied it at all three prepare sites. **Verify on-device:**
    a TRON PROXY send with each tier selected reaches the server as slow/standard/fast (not «عادی»).
  - ⏳ **EVM L1 fee units** — money-math; still **deferred** pending an on-device Base L1-fee check
    (though `getFeeOptions` already prefers the context-aware `totalFee` per tier, which addresses the
    L2 underestimate) so the fix can be validated, not changed blind.
  - ✅ **nullable-fee "0"** — `formatTransactionFee` fell through to the `else` branch on a `null` fee
    (`null == ZERO` is false) and formatted `null ?: ZERO`, so an **unknown** fee rendered as "0",
    indistinguishable from a genuine zero-fee tx. Now a `when` shows a neutral placeholder ("—") for
    `null`, "0 SYMBOL" only for a real zero, and the formatted value otherwise. Display-only; the
    placeholder glyph is a trivial visual tweak if a different marker is preferred.
  - ↔ **socket reconnect guard** — overlaps TASK-22 (realtime robustness); handled there to avoid double work.
- **Problem:** multi-asset price param, chart lexicographic sort, Tron PROXY feeLevel, EVM L1 fee units,
  socket reconnect guard, nullable-fee "0". **Root cause:** various. **TD:** code-review set.
- **Files:** `MarketDataRepositoryImpl`, `AssetDetailScreen`, `ProxyChainDataSource`, `EvmDataSource`,
  `NotificationSocketManager`, `TransactionHistoryViewModel`. **Modules:** data, app. **Deps:** none.
- **Difficulty:** Med · **Est:** 2 · **Risk:** Med (money/price) · **Priority:** P1.
- **Steps:** fix each per code-review notes; add regression tests.
- **Acceptance:** each defect has a failing→passing test. **Rollback:** per fix. **Regression:** price/fee/
  chart display. **Testing:** unit tests + manual send on TRON PROXY + Base fee check.

### TASK-17 — Accessibility, dark-mode colors, RTL, previews — 🟡 a11y interactive-labels done; rest deferred
- **Status:** 🟡 Partially implemented (commit bb3769e — the safe, semantics-only slice): labeled the
  **icon-only interactive controls** that carried `contentDescription = null` (MultiWalletScreen add /
  settings / new-wallet-sheet close, WalletCard "more options", AppUnlockScreen keypad backspace,
  SecretRevealOverlay + ChooseBalanceBottomSheet close). Audited all 70 `contentDescription = null` sites:
  the majority are **decorative** (asset/network badges, status glyphs, icons beside their own visible
  text) where `null` is the correct TalkBack behavior and were intentionally left as-is — labeling them
  would announce redundant content. Zero visual change. (One eligible control in `AnimatedBottomSheetCard`
  was skipped because that file holds uncommitted font WIP — do it when that lands.)
- **Deferred (need on-device screenshot / a11y-scanner / tablet verification, unavailable here):** the
  135 hardcoded `Color(...)` → `colorScheme` migration (changes dark-mode rendering), the RTL policy pass,
  large-font (scale 2.0) + tablet/foldable checks, and broad `@Preview` coverage (TASK-13 already added
  previews for the extracted MainScreen leaves).
- **Problem:** 70 `contentDescription=null`; 135 hardcoded colors; inconsistent RTL; ~8% previews. **TD:**
  CU-5, CU-6, CU-8, CU-11, TD-45 (large font/tablet). **Files:** `ui/**`, `common_ui/theme`. **Deps:** TASK-08/13.
- **Difficulty:** Med · **Est:** 3 · **Risk:** Low · **Priority:** P2.
- **Steps:** label meaningful icons; move colors to `colorScheme`; RTL policy; add light/dark/RTL/large-font
  previews; validate on tablet/foldable emulator.
- **Acceptance:** a11y scanner clean on key screens; dark/large-font/tablet render without clipping.
  **Rollback:** revert. **Regression:** visual. **Testing:** Accessibility Scanner; font-scale 2.0; tablet+fold emulators.

---

## Sprint 4 — Security (post-gate hardening)

### TASK-11 — Relayer TLS + certificate pinning
- **Problem:** plaintext relayer; no pinning. **TD:** 30, 29 (N-3, N-2/S-5). **Files:** `:data` BuildConfig,
  `NetworkModule`, NSC. **Modules:** data, app. **Deps:** TASK-01, backend TLS.
- **Difficulty:** Med · **Est:** 1.5 · **Risk:** Med (lockout on bad pin) · **Priority:** P1.
- **Steps:** switch to `https/wss`; add `CertificatePinner` with backup pins + rotation doc.
- **Acceptance:** release is HTTPS-only + pinned; MITM proxy fails. **Rollback:** remove pinner (keep TLS).
  **Regression:** all network calls. **Testing:** proxy-intercept test; cert-rotation dry run.

### TASK-19 — Auth-bind the Keystore master key (deep lock fix)
- **Problem:** master key not bound to user auth; lock is UI-only. **TD:** 35 (S-2). **Files:**
  `KeyStoreManager`, `SecureStorage`, biometric flow. **Modules:** core, app. **Deps:** TASK-06.
- **Difficulty:** Med–High · **Est:** 3 · **Risk:** High (lockout/keystore invalidation) · **Priority:** P2.
- **Steps:** `setUserAuthenticationRequired(true)` + bounded validity (or per-use) on a seed-access key;
  StrongBox where available; re-auth to decrypt; migration for existing installs.
- **Acceptance:** secret decrypt requires recent auth; existing wallets migrate without data loss.
  **Rollback:** feature-flag; fall back to current key. **Regression:** unlock flow, biometric enroll change
  invalidates key (handle gracefully). **Testing:** enroll/remove biometric; device without StrongBox; upgrade path.

### TASK-20 — Cache hygiene (TTL/eviction/disposal)
- **Status:** 🟢 Mostly implemented:
  - ✅ **`ASSETS_TTL` bug** — `IAppCacheStore.ASSETS_TTL` was `… * 1000000L` → ≈13,700 years (assets
    effectively never expired). Fixed to `5L * 24 * 3600 * 1000` (5 days). Removed the **duplicate**
    (also-buggy, unused) copy in `CacheManager` — the domain interface is now the single source of truth.
  - ✅ **Memory-cache size cap** — `CacheManager.memoryCache` was an unbounded `ConcurrentHashMap`; added
    `MAX_MEMORY_ENTRIES = 256` + `evictIfOverCapacity()` (purge expired, then evict soonest-to-expire).
  - ✅ **Data-source client-cache thread-safety** — `EvmDataSource`/`TronDataSource` `Web3jFactory` used a
    non-thread-safe `mutableMapOf` (siblings use `ConcurrentHashMap`); switched both to `ConcurrentHashMap`.
  - ⏹ **Web3j `shutdown()` on eviction** — **intentionally not done.** The Web3j caches are keyed by
    rpcUrl (bounded to a few chains), reuse the shared `OkHttpClient`, and `Web3j.build` uses web3j's
    shared default scheduled executor — i.e. bounded long-lived singletons, not a real leak. Adding
    speculative disposal to these hot caches without a demonstrated leak would add risk for no benefit.
  - ↔ **Verify-coordinator scope / idempotent `start()`** — covered by `WalletSessionAuthCoordinator`
    (already `@Singleton` + `@Synchronized start()`); no change needed.
  - **Note:** disk-cache growth is still unbounded but low-risk (small JSON, bounded keys); left as-is.
- **Problem:** `ASSETS_TTL` ≈13,700yr; no size cap; Web3j clients never `shutdown()`; verify coordinator
  scope. **TD:** 24, 25, 26, 27. **Files:** `CacheManager`, data-source client caches,
  `WalletSessionAuthCoordinator`. **Modules:** core, data, app. **Deps:** none.
- **Difficulty:** Low · **Est:** 1 · **Risk:** Low · **Priority:** P2.
- **Steps:** fix TTL constant; LRU/size cap; dispose clients on eviction; confirm `@Singleton` + idempotent `start()`.
- **Acceptance:** asset cache honors a sane TTL; bounded caches; no duplicate auth/WS jobs on recreation.
  **Rollback:** per file. **Regression:** cache hits/staleness. **Testing:** TTL unit test; rotate Activity; memory profiler.

---

## Sprint 5 — Cleanup / Reliability

### TASK-18 — Consolidate DIRECT/PROXY routing + parity
- **Problem:** transport decision duplicated (factory + coordinator); parity bugs. **TD:** 05 (M-1) + N-5.
- **Files:** `ChainDataSourceFactory`, `UnifiedTransferCoordinator`, `ProxyChainDataSource`. **Deps:** TASK-16.
- **Difficulty:** Med · **Est:** 2 · **Risk:** Med · **Priority:** P2.
- **Steps:** emit one canonical request; let the mode-selected data source own prepare-vs-direct; remove
  per-network `if(PROXY)` branches. **Acceptance:** single routing point; DIRECT/PROXY behaviorally equal.
  **Rollback:** revert. **Regression:** all sends. **Testing:** send matrix DIRECT×PROXY × EVM/TRON/UTXO.

### TASK-36 — Smart transport failover (PROXY ⇄ DIRECT auto-switch on error) — ✅ v1 implemented (reads-only)
- **Done (2026-07-18):** added `TransportFailover.kt` (data/datasource) with three pieces:
  `TransportErrorClassifier.isFailoverWorthy()` (transport/server → yes: IOException, HTTP 408/425/429/5xx,
  `ApiError.UpstreamUnavailable/InternalError/ServiceUnavailable/RateLimited/NetworkFamilyUnsupported/UnsupportedOperation/Unknown`;
  business → no: validation, insufficient-credit, revert, broadcast-rejected, …), a best-effort
  `TransportHealthTracker` circuit breaker (N consecutive failures → open for a cooldown → try alternate
  first), and `TransportFailoverChainDataSource` — an `IChainDataSource` decorator that retries **reads** on
  the other transport when the preferred one hits a failover-worthy error, surfacing the preferred error when
  both fail. **Broadcast is never failed over** (a re-sign on the other transport could double-spend) —
  `sendTransaction` goes to preferred only. Wired in `ChainDataSourceFactory.create()` behind
  `FAILOVER_ENABLED` (kill switch); the alternate source is built lazily. The user's saved mode stays the
  *preferred* transport. Unit test `TransportFailoverTest` (:data, MockK) covers the classifier + decorator
  (success passthrough, transient failover, business no-failover, both-fail, no broadcast failover, open-circuit
  order). **Not built here** — inspection-verified. **Verify on-device:** force PROXY to fail (bad base URL) →
  balances/history still load via DIRECT; a genuine validation error is not masked; sends never double-fire.
- **Deferred to v2:** effective-vs-preferred mode surfaced to the UI (badge), broadcast failover gated on a
  stable idempotency key, and TASK-18's full single-routing-point consolidation (the decorator sidesteps it).
- **Original spec below.**
- **Problem (user request):** the transport mode is a single persisted user preference
  (`DefaultBlockchainConnectionModeProvider`, read synchronously via `IBlockchainConnectionModeProvider.currentMode()`).
  If the selected transport is unhealthy — the centralized Mobile Blockchain Proxy (`/api/mobile/v1`) is
  down/5xx/timeouts, **or** the direct public RPCs are rate-limited/unreachable — every read/broadcast on
  that mode just fails, even though the *other* transport would succeed. There is no automatic fallback.
- **Goal:** when calls on the active transport fail (transient/transport-level, not business errors), fall
  back to the other transport for that operation, and remember the healthy one for a short window; recover
  to the user's preferred mode when it's healthy again. Direction is symmetric: PROXY→DIRECT **and**
  DIRECT→PROXY.
- **Root cause:** `ChainDataSourceFactory` picks exactly one `IChainDataSource` per call from
  `currentMode()`; there's no health signal or retry-on-alternate wrapper. Both sources already return the
  **same domain types**, so a fallback is transport-transparent to ViewModels (same invariant the mode
  toggle relies on).
- **Design sketch:** introduce a `FailoverChainDataSource` decorator (or a policy in the factory) that,
  on a **classified transport failure** (IOException/timeout/HTTP 5xx/429 — NOT a valid on-chain revert,
  insufficient-funds, nonce, or a signed-tx rejection, which must NOT be retried on another transport),
  transparently retries the same operation on the alternate `IChainDataSource`. Add a lightweight
  per-transport health tracker (circuit-breaker: N consecutive failures → mark unhealthy for a cooldown,
  prefer the healthy one) so we don't pay the failed-primary latency on every call. **Broadcast is the
  danger zone** — a send that failed *after* the node accepted it must not be re-broadcast blindly on the
  other transport (double-spend/duplicate risk); gate broadcast failover on idempotency
  (`IdempotencyInterceptor` / gasless session-stable key already give byte-identical retries a stable key)
  or restrict failover to **reads** first and treat broadcast conservatively.
- **Files:** `ChainDataSourceFactory`, new `FailoverChainDataSource` + a `TransportHealthTracker`,
  `IBlockchainConnectionModeProvider` (expose an *effective* vs *preferred* mode), error-classification
  helper. **Modules:** data (+ domain interface if a health signal is surfaced to UI). **Deps:** pairs with
  **TASK-18** (consolidate DIRECT/PROXY routing) — do TASK-18 first so there's a single routing point to
  wrap; **TASK-16** (fee/parity) so both paths are behaviorally equal before auto-switching between them.
- **Difficulty:** Med–High · **Est:** 2.5 · **Risk:** Med–High (broadcast dup / masking real errors) · **Priority:** P2.
- **Steps:** (1) classify errors into transient-transport vs business/final; (2) wrap reads with
  retry-on-alternate + circuit-breaker health tracking; (3) decide broadcast policy (idempotent-only, or
  reads-only in v1); (4) surface effective-mode to the UI (optional badge) without changing the user's saved
  preference; (5) auto-recover to preferred mode after cooldown/health-check.
- **Acceptance:** with PROXY forced to fail (e.g. bad base URL), balances/history still load via DIRECT and
  vice-versa; a genuine on-chain error is **not** masked by a pointless alternate retry; no duplicate
  broadcast. **Rollback:** feature-flag the failover decorator off → falls back to today's single-mode
  behavior. **Regression:** all reads/sends on both modes; idempotency. **Testing:** unit-test error
  classification + circuit-breaker; MockWebServer 5xx/timeout on one transport; send matrix
  DIRECT×PROXY × EVM/TRON/UTXO with forced primary failure.
- **Note:** keep the user's explicit mode as the *preferred* setting — failover is a temporary,
  self-healing override, never a silent permanent switch.
- **Precedent to reuse (not the same thing):** DIRECT already has *intra-transport* failover —
  `EvmDataSource.executeWithFailover` rotates through `network.RpcUrlsEvm` on error (15s per-RPC timeout),
  and `getTransactionHistory` walks `network.explorers`. That's RPC-endpoint rotation *within* DIRECT,
  **not** PROXY⇄DIRECT transport switching — TASK-36 is the missing cross-transport layer above it. Model
  the error-classification + timeout after `executeWithFailover` for consistency.

### TASK-22 — Realtime robustness (buffer + reconnect re-sync) — ✅ Implemented
- **Status:** ✅ Implemented:
  - ✅ **Foreground/background socket lifecycle** (user-reported: socket stayed open while backgrounded /
    phone locked). New `RealtimeLifecycleCoordinator` observes `ProcessLifecycleOwner` and enforces
    "socket connected **iff** foreground AND session exists": disconnects on app-background, reconnects
    on foreground (only when `ITokenStore.getTokenDevice() != null`, so it never pre-empts the auth-driven
    first connect). Uses `ProcessLifecycleOwner` (not `Activity.onStop`) so a rotation/config-change
    doesn't churn the socket. Background delivery is still covered by FCM (WS+FCM de-duped). Added
    `androidx.lifecycle:lifecycle-process`. Unit-tested.
  - ✅ **Burst-safe event flow** — `NotificationSocketManager._events` went from bare
    `MutableSharedFlow(replay=1)` + a `scope.launch { emit }` per frame (which piled up coroutines and
    could reorder/suspend under a burst) to `replay=1, extraBufferCapacity=64,
    onBufferOverflow=DROP_OLDEST` emitted via non-suspending `tryEmit`. A burst can now never block the
    OkHttp read callback; only stale realtime events are dropped, never the socket.
  - ✅ **Re-sync on reconnect/online** (TD-46) — `onOpen` posts `AppEvent.WalletNeedsRefresh` on every
    (re)connect **except the first** (guarded by `hasConnectedBefore`; cold start already loads). So a
    dropped/backgrounded/offline socket coming back — including the foreground reconnect above — fans out
    a balance/history re-sync through the existing `IAppEventBus` (consumed by `HomeViewModel` /
    `TransactionHistoryViewModel`). Required injecting `IAppEventBus` into the socket manager.
  - ✅ **`connect()` re-arm** — dropped the `if (shouldBeConnected) return` short-circuit so the
    auth-flow's post-JWT `connect()` actually opens a socket that an earlier (token-less) `connect()` had
    deferred. `attemptConnection()` stays a no-op when a socket already exists, so repeat calls can't
    produce duplicate sockets.
  - ✅ **Live events now refresh the UI** — previously a live frame refreshed *nothing* (only a system
    notification; a wallet re-read only on reconnect). `dispatchRefreshFor` now fans meaningful events to
    the refresh bus: `BalanceUpdated`/`GrowthFeeShareAccrued` → `WalletNeedsRefresh`; `TxStatusChanged` →
    `WalletNeedsRefresh` + `TransactionHistoryNeedsRefresh`. This matches the **server's documented intent**
    (§8 of `ANDROID_SERVER_INTEGRATION.md`, server repo `docs/`): the WS pushes *thin invalidation signals*,
    never data — the client refreshes the relevant repository and never renders the payload. So the
    refresh-on-signal shape is correct; the thin-signal targeting is the re-alignment below.
  - ✅ **SocketEvent contract re-aligned to the live server (2026-07-16):**
    - Added the three thin signals to `SocketEvent` + `parseEnvelope`: **`tx.new`**
      `{eventId,txHash,networkId,addressIdentityId,cursor}`, **`balance.invalidated`**
      `{eventId,walletId,networkId,assetId,cursor}`, **`tx.status.updated`**
      `{eventId,txHash,networkId,status,cursor}` — they no longer fall through to `Unknown`/get dropped.
      Legacy `tx.status.changed`/`balance.updated`/`growth.fee_share.accrued` remain accepted.
    - **Targeted fan-out** — extracted a pure, Android-free `SocketRefreshMapper`: `balance.invalidated`
      → `WalletAssetNeedsRefresh(assetId, networkId)` (networkId is the bundle id verbatim, used directly
      by `HomeViewModel.refreshSingleAssetBalance`), falling back to `WalletNeedsRefresh` when `assetId`
      is absent; `tx.new`/`tx.status.updated` → `TransactionHistoryNeedsRefresh(networkName)` where
      `networkId` is reverse-mapped to a local `NetworkName` via `INetworkCatalog.getNetworkInfoById`
      (unknown network ⇒ unscoped refresh). Legacy events stay coarse (`WalletNeedsRefresh` [+ history]).
    - **Dedup now keys on `payload.eventId`** (falls back to envelope `id` for legacy/welcome) — the 5s
      `EventDeduplicationCache` window was already correct.
    - **Adaptive heartbeat** — adopts `connection.ready.payload.heartbeatMs` (bounded 5s–300s) and
      restarts the ping loop on it, instead of the hardcoded 30s (still the pre-welcome default).
    - **Tests:** `SocketRefreshMapperTest` (:data, pure) covers asset-targeting, missing-assetId
      fallback, network-scoped vs unscoped history refresh, and legacy-coarse mapping.
    - **Gating:** `REALTIME_THIN_EVENTS_ENABLED` is **currently OFF** server-side (only `connection.ready`
      + pong arrive), so live signal delivery can't be verified on-device until the flag flips. The parser
      + mapper are built and unit-tested now; they activate automatically when the server turns it on.
  - **Verify on-device:** burst of tx events (no drops beyond buffer), airplane-mode toggle →
    reconnect fires a refresh, and a token minted after a premature `connect()` still opens the socket.
  - **⏳ Follow-up (server doc §8.2 refinement, not yet done):** the contract now specifies the *exact*
    repository action per signal — `tx.new → historyRepository.syncSince(cursor, networkId)`,
    `balance.invalidated → balanceRepository.refresh(walletId, networkId, assetId)`,
    `tx.status.updated → transactionRepository.refresh(txHash, networkId)`. Our `SocketRefreshMapper`
    currently maps `tx.new`/`tx.status.updated` to a **coarse** `TransactionHistoryNeedsRefresh(networkName)`
    and **ignores the opaque `cursor`**. This is functionally safe (the doc: "a missed/duplicated signal only
    ever causes a redundant re-fetch"), but the cursor-based `syncSince` + the `since` (epoch-ms) incremental
    `/history` param would make the refresh *incremental* instead of a full network reload. Low priority while
    `REALTIME_THIN_EVENTS_ENABLED` is OFF; do it when wiring an incremental history repo. Pass `cursor`
    through untouched — never parse it.
- **Problem:** `SharedFlow(replay=1)` event loss; no confirmed reconnect re-sync; socket reconnect guard;
  backoff dup. **TD:** 42, 46, N-6. **Files:** `NotificationSocketManager`, `GlobalEventBus`, realtime gateway.
- **Difficulty:** Med · **Est:** 2 · **Risk:** Med · **Priority:** P2.
- **Steps:** give the event flow adequate `extraBufferCapacity` + explicit overflow policy; re-arm
  `attemptConnection()` when token arrives; reuse `ExponentialBackoff`; trigger balances/history re-sync on
  reconnect/online. **Acceptance:** burst events not dropped; reconnect re-syncs; socket connects post-auth.
  **Rollback:** revert. **Regression:** notification delivery, dedup. **Testing:** simulate burst + slow
  collector; toggle airplane mode; kill/restore socket.

### TASK-23 — Notifications runtime permission (A13+) — ✅ Implemented
- **Status:** ✅ Implemented — `POST_NOTIFICATIONS` was declared in the manifest but never requested at
  runtime, so on Android 13+ the `checkSelfPermission` guards in `NotificationService` /
  `NotificationSocketManager` silently no-op'd and notifications never showed. `MainActivityCompose` now
  requests it via `rememberLauncherForActivityResult(RequestPermission())`, fired from a `LaunchedEffect`
  **only when unlocked** (`lockUiState.isInitialized && !isLocked`) so the prompt lands post-onboarding /
  past app-lock, guarded by `SDK_INT >= TIRAMISU` + a not-already-granted check. Denial is non-fatal (the
  existing guards handle it); `rememberSaveable` asks at most once per process (survives rotation), a fresh
  launch retries until the OS stops re-prompting. **Verify on-device:** A13+ shows the prompt after first
  unlock; grant → tx notifications deliver; deny → app works, no crash.
- **Problem:** `POST_NOTIFICATIONS` not requested. **TD:** 43 (PR-3). **Files:** `NotificationService`,
  an activity/onboarding step. **Modules:** core, app. **Deps:** none.
- **Difficulty:** Low · **Est:** 0.5 · **Risk:** Low · **Priority:** P2.
- **Steps:** request the permission at an appropriate moment (post-onboarding), handle denial gracefully.
- **Acceptance:** on A13+, prompt shown; notifications deliver after grant. **Rollback:** revert. **Regression:**
  none. **Testing:** A13/14/15 emulator grant+deny paths.

### TASK-32 — Batch monitoring enrollment for ALL wallets (`/monitoring/subscribe`) — ✅ Done (build-verified; delivery gated by server flag)
- **Problem:** realtime `tx.new`/`balance.invalidated` signals + deposit FCM only fire for addresses the
  server has in its **monitored set**, and today an address is only enrolled as a *side-effect* of a
  `/history` request for the **currently-selected** wallet (`TransactionHistoryViewModel.buildHistoryPairs`
  → `ProxyChainDataSource.history`). So non-active wallets get no realtime/deposit signals until the user
  opens their history, and enrollment is re-triggered on every wallet switch. **Source:** server
  `ANDROID_SERVER_INTEGRATION.md` (2026-07-13) §5 added a purpose-built endpoint.
- **Endpoint:** `POST /api/mobile/v1/monitoring/subscribe`
  `{ "addresses":[ {"address","networkId"}, … ] }` → `{ ok, subscribed, total, results:[{address,networkId,ok,error?}] }`.
  Durable + idempotent (one call per wallet lifetime); per-pair failures reported in `results[]` without
  failing the batch; **max 25 pairs/call — chunk beyond that** (same bound as `/history`). `/history`
  auto-enroll remains only a safety net — do NOT rely on the history screen.
- **Files:** new `MonitoringSubscribeRequestDto`/response DTO, `MobileProxyApiService.monitoringSubscribe`,
  a repo/use case (`SubscribeMonitoringUseCase`), and a call site. **Modules:** data, domain, app. **Deps:** none.
- **Difficulty:** Low–Med · **Est:** 1 · **Risk:** Low · **Priority:** P1 (gates realtime/deposit coverage).
- **Steps:** (1) add DTO + service method + repo/use case; (2) gather **all** wallets' `(address, networkId)`
  pairs across every `WalletKey` (not just the active wallet); (3) call once after **login / wallet create /
  wallet import / add-network** (and whenever the local wallet/network set changes), chunked to 25;
  (4) run off-main, fire-and-forget with per-chunk error logging (idempotent, so a failed chunk just retries
  next trigger). Auth: needs the Bearer JWT (`proxy:write`-ish) — sequence after `WalletSessionAuthCoordinator`.
- **Acceptance:** after adding/importing a wallet, all its `(address, networkId)` pairs appear enrolled
  (`subscribed`/`results`), and a deposit to a **non-active** wallet surfaces via FCM/`balance.invalidated`
  without opening its history. **Rollback:** remove the call site (behavior falls back to `/history`
  safety-net enroll). **Regression:** none (additive; idempotent). **Testing:** unit-test the pair-gathering
  + chunking; on-device, import a wallet and confirm a deposit notification without visiting history.
- **Note:** signal *delivery* is still gated by the server's `REALTIME_THIN_EVENTS_ENABLED` (OFF); enrollment
  itself works now, and FCM deposit push is not gated. Pairs with the socket-contract re-alignment under TASK-22.
- **Done (2026-07-16):** added `MonitoringSubscription`/`MonitoringSubscribeResult` domain models,
  `IMonitoringRepository` + `SubscribeMonitoringUseCase` (gathers all `WalletKey`s across every wallet,
  maps `NetworkName`→bundle `networkId` via `INetworkCatalog`, dedups, chunks to 25), `MonitoringDto`
  (request/response, un-enveloped), `MobileProxyApiService.monitoringSubscribe`, `MonitoringRepositoryImpl`
  (logs per-pair `results[]` failures without failing the batch), and a DI binding. Call site:
  `WalletSessionAuthCoordinator.handleWalletChange` fires `enrollMonitoring()` fire-and-forget after each
  successful `ensureAuthenticated` (covers app-start/unlock/switch/create/import — keys are derived at
  create/import so there's no separate add-network trigger). Unit tests:
  `SubscribeMonitoringUseCaseTest` (:data, MockK) covers gather+dedup, 25-chunking, unknown-network drop,
  empty-wallets no-op, and wallet-read-failure propagation. **Not run here** (Gradle unavailable in env) —
  inspection-verified only.
- **Reworked (2026-07-16, user review):** the original gathered pairs from `getAllWallets()`, which
  returns metadata-only wallets with `keys=emptyList()` → it enrolled **zero** addresses. Now:
  `SubscribeMonitoringUseCase` uses the **active** wallet's real derived keys; enrollment is **once per
  wallet** (gated by a persisted `IUserPreferencesRepository.getMonitoringSubscribedWalletIds` set), so a
  plain wallet switch is a no-op and only a newly created/imported wallet (which becomes active) hits the
  network; `DeleteWalletUseCase` prunes the id so a re-import re-enrolls; partial-chunk failures aren't
  recorded (retry next activation). Call site unchanged (`WalletSessionAuthCoordinator`, now a cheap
  no-op on switch). See [[refresh-and-monitoring-policy]].

### TASK-33 — Lazy, wallet-scoped history load (user review) — ✅ Done
- **Problem (item 1):** `TransactionHistoryViewModel` is created eagerly by `MainScreen` (`hiltViewModel()`),
  and its `init → observeActiveWallet` called `loadHistory()` immediately → history services fired on app
  open, before the History tab was ever shown. **Problem (item 6):** on wallet switch the screen could show
  the previous wallet's data; opening History didn't guarantee the selected wallet's data.
- **Fix:** removed the eager `init` load. `observeActiveWallet` now only rebuilds network options and, on a
  wallet **change**, resets history state (clears data, resets filter to "all", invalidates the load key) —
  fetching only if the screen is currently visible. New `onScreenShown()`/`onScreenHidden()` (wired from
  `MainScreen` via `LaunchedEffect(selectedTab)`): `onScreenShown` is the sole initial-fetch trigger, so
  history services are never called until the tab is opened, and it always loads the **active** wallet.
  Socket/FCM `TransactionHistoryNeedsRefresh` while hidden just invalidates (defers to next open) instead of
  fetching. **Modules:** app. **Files:** `TransactionHistoryViewModel`, `MainScreen`. **Deps:** none.
- **Not built here** (Gradle unavailable) — inspection-verified. **Verify on-device:** open app on Wallet
  tab → no `/history` call; tap History → loads active wallet; switch wallet then open History → new wallet's
  data; switch while on History → reloads new wallet.

### TASK-34 — Wire FCM push end-to-end (device token + refresh + display + dispatch) — ✅ Done
- **Problem (user review, item 10):** the whole `/api/notifications` stack existed (`INotificationRepository`,
  `NotificationApiService`, DTOs, `NotificationRepositoryImpl`) but nothing called it — no FCM token was ever
  sent to the server, no `FirebaseMessagingService` existed to receive tokens/messages, no token refresh, and
  data pushes weren't displayed or routed. (User had only added the `firebase-messaging` lib + `google-services.json`.)
- **Fix:**
  - `MegaFirebaseMessagingService` (`@AndroidEntryPoint`) declared in the manifest with the
    `com.google.firebase.MESSAGING_EVENT` filter + a `default_notification_channel_id` meta-data pointing at
    the existing `trade_notifications` channel. **(Corrected by TASK-59 — that default was wrong: the trade
    channel has the system default sound, so a Firebase-rendered push could never play the deposit sound.
    It now resolves to the versioned deposit channel.)**
  - `FcmTokenRegistrar` (app) — registers the token via `INotificationRepository.registerDevice` **after auth**
    (`WalletSessionAuthCoordinator.syncToken()`, needs the JWT for identity) and on Firebase `onNewToken`;
    persists the last-registered token (`IUserPreferencesRepository.getRegisteredFcmToken`) to skip unchanged
    re-registers; `unregister()` on logout (before the JWT is cleared).
  - `PushMessageHandler` (:data) — routes a data-only push through the SAME pipeline as the socket: dedup on
    `eventId` via the shared singleton `EventDeduplicationCache` (cross-transport WS↔FCM), refresh-on-signal
    via the shared `SocketRefreshMapper` → `IAppEventBus`, and shows the server-provided (localized) title/body
    via the existing `NotificationService`. Parses the flat FCM `data` map into the same `SocketEvent` types.
  - **Modules:** app, data, domain. **Files:** new `MegaFirebaseMessagingService`, `FcmTokenRegistrar`,
    `PushMessageHandler`; edited `WalletSessionAuthCoordinator`, `IUserPreferencesRepository`(+impl),
    `AndroidManifest`. **Deps:** `firebase-messaging` (already added).
- **Not built here** (Gradle unavailable) — inspection-verified. **Verify on-device:** after unlock, a
  `POST /api/notifications/devices` fires once (token in logs); a server test push refreshes the balance/history
  and shows a notification; background push shows a tray notification; logout sends `DELETE …/devices/:token`.
- **Note:** the socket path (`NotificationSocketManager`) is untouched; both transports share
  `EventDeduplicationCache` + `SocketRefreshMapper`, so a WS+FCM duplicate is handled once. See
  [[refresh-and-monitoring-policy]].

### TASK-35 — 24h price-change % not displayed (user review, item 11) — ⏳ Client ready; blocked on server
- **Root cause:** NOT a client rendering bug. The chain is correctly wired end-to-end
  (`RelayerPriceEntryDto.change24h` → `AssetPriceDto.priceChanges24h` → `AssetItem.priceChange24h` →
  `WalletScreen`/`AssetDetailScreen.formatPriceChange`). The **primary price source
  `GET /api/v1/prices` does not return a 24h-change field at all** — per the server contract
  (`MEGAWALLET_ANDROID_API_CONTRACT.md` §1.6) each entry is only `{ usd, irr, source, fetchedAt }`. So
  `change24h` parses null → `RelayerPriceDataSource` maps it to `ZERO` → every asset shows ~0.00%. The
  change-capable path (`CoinDetailApiService`, CoinCap `changePercent24Hr`) is only the **fallback**, used
  when the relayer path fails, and is a **direct third-party** call — so in normal operation it never runs.
- **Decision (user):** fix **server-side** — add the change field to `/api/v1/prices` (relayer stays the
  single source; no extra client calls, no direct-CoinCap dependency).
- **Server contract to implement:** add `change24h` to each `prices[symbol]` entry as a **percent number**
  (e.g. `2.84` = +2.84%, `-1.5` = −1.5%), sourced from the existing feed (CoinGecko
  `price_change_percentage_24h` / CoinCap `changePercent24Hr`). Optional per symbol (omit for `{error}`
  symbols). Example: `"ETH": { "usd": 3500.12, "irr": …, "change24h": 2.84, "source": "coingecko", … }`.
- **Client status:** already consumes a field named `change24h` (primary `@SerializedName` on
  `RelayerPriceEntryDto`, + `changePercent24h`/`changePercent24Hr` alternates). **No client code change is
  required** once the server sends it — the badge will render real values automatically. Tidied the DTO
  comment to state the agreed contract (removed the old "unverified/guessed" hedging).
- **Verify (once server ships it):** wallet list + asset detail show real ±% badges (green up / red down),
  not a flat 0.00%. **Modules:** data (client), + server. **Deps:** server change.

### TASK-37 — On-demand transaction detail (fee/energy completeness on tx open) — ✅ Data layer done (verify on-device)
- **Done (2026-07-17):** the fetch-on-open plumbing already existed end-to-end
  (`TransactionHistoryViewModel:715` → `GetTransactionFeeDetailsUseCase` →
  `IWalletRepository.getTransactionFeeDetails` → `IChainDataSource.getTransactionFeeDetails`), and the
  **DIRECT** path (`TronDataSource.getTransactionFeeDetails` → native `gettransactioninfobyid`) already
  returned real fee/energy. The gap was **PROXY**: `ProxyChainDataSource.getTransactionFeeDetails` hit the
  `…/status` endpoint (no fee) and always mapped to `fee=0`. Now it calls the new
  `GET …/:txId/detail` (`MobileProxyApiService.transactionDetail` → `ProxyEnvelope<TxDetailDto>`,
  DTOs `TxDetailDto`/`TxDetailTronDto`/`TxDetailEvmDto`/`TxFeeBreakdownDto` in `MobileProxyDto.kt`) and
  maps `feeRaw` + the TRON `feeBreakdown`/energy/bandwidth into `TransactionFeeDetails`
  (PENDING → `feeRaw:null` falls back to the breakdown, then ZERO). So opening a TRON **token** transfer
  (incoming or gasless) in PROXY mode now shows the real fee/energy instead of `0`.
- **Verify on-device (PROXY mode):** open an incoming TRON MST transfer → real energy + fee (not 0);
  open a pending tx → fills on confirmation. **Assumed** the `…/detail` response is BM-33-enveloped like
  the sibling `…/status` route — confirm against the live server; if it's bare, drop the `ProxyEnvelope<>`.
- **Original problem/spec kept below for reference.**
- **Problem (server doc §5, 2026-07-XX addition):** the unified `/history` feed is **intentionally partial
  for fee/energy** — for a **TRON token** transfer the fee/energy live on the *carrier* tx which is usually
  NOT in the queried account's list (incoming transfer, or a relayer-funded gasless send), so the list
  returns `tron.energyUsed`/`bandwidthUsed = null` and `feeRaw = "0"`. EVM token rows can be similarly thin.
  Today `TransactionDetailsBottomSheet` renders only what the list row carries, so an opened TRON/token tx
  shows a **wrong `0` fee / blank energy**.
- **Endpoint:** `GET /api/mobile/v1/networks/:networkId/transactions/:txHash/detail` — the server-side proxy
  of `gettransactioninfobyid` (TRON) / `eth_getTransactionReceipt` (EVM), so filtered users get the same
  complete data DIRECT gives. Returns `{ txId, type, status, confirmations, blockNumber, timestamp, feeRaw,
  <evm|tron> block }`. A `PENDING` result has `feeRaw:null` → poll `…/status` or wait for the WS
  `tx.status.updated` signal. Settled results are cached server-side (re-opens are free).
- **Rule:** call it **lazily, only when the user OPENS a transaction** (the detail sheet) — never per row in
  the list. Merge `feeRaw` + the per-family block into the item already in hand; show a spinner/placeholder
  for the fee/energy fields until it resolves.
- **Files:** `MobileProxyApiService` (+ a `transactionDetail` method + DTO), a repo/use case
  (`GetTransactionDetailUseCase`), `TransactionDetailsBottomSheet` + its VM/state to fetch-on-open and merge.
  **Modules:** data, domain, app. **Deps:** none (works today; realtime-flag independent).
- **Difficulty:** Low–Med · **Est:** 1 · **Risk:** Low · **Priority:** P2 (correctness of displayed fees).
- **Steps:** (1) DTO + `GET …/detail` service method; (2) use case returning a merged
  `TransactionFeeDetails`; (3) fetch-on-open in the detail sheet, placeholder→value, handle `PENDING`
  (`feeRaw:null`) by polling `…/status` or awaiting `tx.status.updated`; (4) cache the merged result in-VM
  so a re-open doesn't refetch. **Acceptance:** opening a TRON token transfer (incoming or gasless) shows the
  **real** fee + energy, not `0`/blank; opening a pending tx shows "pending" then fills on confirmation.
  **Rollback:** hide the detail fetch (falls back to today's list-only render). **Regression:** detail-sheet
  render for native/EVM/BTC rows unchanged. **Testing:** MockWebServer detail fixtures (TRON token, EVM,
  pending); on-device open an incoming TRON MST transfer and confirm real energy/fee.

### TASK-39 — PROXY unified-history nested-shape mapping (token rows + energy/gas dropped) — ✅ Fixed (verify on-device)
- **Problem (found 2026-07-17 from a live `/history` capture):** the unified `/history` item is **nested**
  — token info under `tokenTransfer{ symbol, decimals, amountRaw, contractAddress }`, per-family fee/energy
  under `tron{ energyUsed, bandwidthUsed, feeBreakdown }` / `evm{ gasPriceRaw, gasUsedRaw, nonce }`, asset
  under `display{ isNative, symbol, decimals }`. But `HistoryItemDtoDeserializer` used a plain
  `context.deserialize(json, …FlatDto)` that reads **top-level** `tokenSymbol`/`energyUsed`/`gasPriceRaw`
  — all absent → null. Effect: **token transfers (e.g. TRON MST, and any EVM ERC-20) were mis-mapped as
  native** (`tokenDetails()` returns null when symbol/contract are null), so they didn't render under their
  token/asset filter; energy/bandwidth/gas were lost. **DIRECT was unaffected** (its own parsers already
  read the real shape) — hence "only PROXY".
- **Fix:** rewrote `HistoryItemDtoDeserializer` to read the nested JSON explicitly (`tokenTransfer`, `tron`,
  `evm`, `bitcoin` blocks) and flatten into the DTOs the mapper consumes; `contractAddress` falls back
  tokenTransfer→family-block. Null-safe primitive/bigint/object accessors added. `HistoryItemMapper.toDomain`
  unchanged (it already prefers the token amount over `valueRaw`). **Modules:** data. **Files:**
  `HistoryItemDto.kt`. Pairs with **TASK-37** (the tx-detail sheet's fee/energy, PROXY `getTransactionFeeDetails`
  → `/detail`). **Not built here** (Gradle unavailable) — inspection-verified against the live capture.
- **Verify on-device (PROXY):** history now lists TRON **MST** token transfers (and EVM token transfers) with
  the right symbol/amount, and a row's energy/bandwidth/gas populate; native rows unchanged.

### TASK-40 — Pin web3j to the Android artifact (6.x is Java-21/Jackson-3 → crashes on Android) — ✅ Fixed (build+verify)
- **Problem (two on-device FATALs, same root):** the catalog had `web3j core/crypto/utils = 6.0.0`. web3j
  **5.x/6.x are Java-21-only** and use **Jackson 3 (`tools.jackson`)**, which needs runtime APIs absent on
  Android: (1) `Collectors.toUnmodifiableMap` (Java 10) → `NoSuchMethodError` from the Jackson java-time
  module; (2) `java.lang.Class.isRecord()` (Java 16, Android API 33+) → `NoSuchMethodError` in
  `tools.jackson.databind.JavaType.isRecordType` during JSON-RPC request serialization
  (`org.web3j.protocol.Service.send`). So **any** web3j RPC (EVM/TRON reads, gasless allowance) crashed on
  sub-33 devices. Core library desugaring backports #1 but **cannot** backport #2 (`isRecord()` is a VM
  method on `java.lang.Class`, not a rewritable library API).
- **Root cause:** web3j 6.x dropped Android; the Java binaries are compiled with Java 21. The **only**
  Android-supported build is the `-android` classifier line (LFDT-web3j `android` branch, Jackson 2).
- **Fix:** pinned `webCore`/`webCoreUtils` → **`4.12.3-android`** (verified latest `-android` on Maven
  Central for `core`/`crypto`/`utils`/`abi`). Every web3j API the app uses (`Credentials`, `Sign`, `Keys`,
  `Bip32ECKeyPair`, `MnemonicUtils`, `RawTransaction`, `TransactionEncoder`, `FunctionEncoder`,
  `StructuredDataEncoder`/EIP-712, `Web3j`, `HttpService`, `Numeric`, abi datatypes) exists in 4.12.3, so
  the API drift is minimal. Core library desugaring stays (harmless; now optional for web3j).
- **Build + verify (Gradle unavailable here — user builds):** clean build should compile; if it fails on
  **BouncyCastle** (the root `resolutionStrategy` force-pins `bcprov-jdk18on:1.73` for the web3j/bitcoin
  dup-class conflict), the `-android` variant may pull a different BC — adjust the pin then. On-device: a
  TRON/EVM gasless send + a DIRECT RPC read no longer crash. **Never bump web3j past `4.12.3-android`.**

### TASK-41 — Tron DIRECT-mode reliability/correctness (explorer failover + fee amount) — ✅ Fixed
- **Problem (found in the Tron audit):** `TronDataSource` (DIRECT mode) had two latent issues:
  - **history single-explorer** — `getTransactionHistory` built its explorer client from
    `network.explorers[0]` only. Unlike the RPC path (which fails over `RpcUrls`), a single dead/rate-limited
    explorer blanked the **entire** Tron history (native + token).
  - **fee estimate used the wrong amount** — the TRC-20 branch of `getFeeOptions` estimated energy against
    `asset.balance.toBigInteger()` (the **display** balance, whole coins) instead of the raw send `amount`
    param — wrong units and semantics for the `triggerConstantContract` transfer param.
- **Fix:** added `executeExplorerWithFailover { … }` (mirrors `executeNativeApiWithFailover`) and routed the
  native+token history fetch through it (trailing-slash-normalized base URL). Fee estimate now uses
  `amount ?: BigInteger.ONE` (raw). **Modules:** data. **Files:** `TronDataSource.kt`. **Not built here** —
  inspection-verified. **Verify on-device (DIRECT):** history still loads if the primary explorer is down;
  TRC-20 fee preview is sane for the actual amount.

### TASK-42 — Optimistic pending tx survives lazy-history reset (regression from TASK-33) — ✅ Fixed
- **Problem (user, item 2):** a just-sent tx no longer appeared as PENDING in history. TASK-33 gated the
  optimistic-pending insert behind `isScreenVisible`, but a send happens from the **Send** screen (History
  hidden) → the `TransactionHistoryNeedsRefresh(pendingTransaction=…)` event hit the `else` branch and the
  pending was dropped; and even if inserted, `loadHistory` clears + reloads from the backend (which hasn't
  indexed it yet) on `onScreenShown`.
- **Fix:** `TransactionHistoryViewModel` now keeps optimistic pendings in a SEPARATE `_localPending`
  StateFlow, and the public `transactions` is `combine(_transactions, _localPending)` → merged for display.
  Pendings are captured on the event regardless of visibility, survive every reload, are filtered out once
  the backend returns the same hash, respect the current network filter, and age out after 30 min. Never
  cached/paginated. **Files:** `TransactionHistoryViewModel.kt`. **Verify:** send a tx (any screen) → open
  History → it shows PENDING immediately; on confirmation it flips to the real row (no dupe).

### TASK-43 — Transaction sound in-app AND when closed (custom sound both paths) — ✅ Fixed
- **Problem (user, item 3):** the deposit sound wasn't reliably heard in-app (foreground), and the FCM
  (closed) path used the **trade** channel (default sound), not the custom deposit sound.
- **Fix:** new `TransactionSoundPlayer` (core) plays `res/raw/deposit_alert` via a self-releasing
  `MediaPlayer` (USAGE_NOTIFICATION_EVENT). Foreground WS path (`NotificationSocketManager`, socket is
  foreground-only) plays it explicitly BEFORE the POST_NOTIFICATIONS gate and posts a **silent**
  notification (no double sound, audible even if notifications were declined). Background/closed FCM path
  (`PushMessageHandler`) posts on the deposit channel with `silent = false` so the channel plays the custom
  sound. `NotificationService.showDepositNotification` → `showTransactionNotification(title, msg, silent)`.
  **Files:** `TransactionSoundPlayer.kt` (new), `NotificationService.kt`, `NotificationSocketManager.kt`,
  `PushMessageHandler.kt`. **Verify:** foreground incoming tx → sound in-app; app closed → FCM push plays
  the custom sound.

### TASK-44 — Generalize deferred-refresh-on-entry to all tab screens (item 4) — ✅ Fixed → ⚠️ wallet-gating REVERTED (see TASK-45)
- **Problem (user, item 4):** a refresh signal (socket/FCM) that arrives while a screen isn't the visible
  tab should be applied when the user enters that screen — for **all** screens. Only History did this
  (TASK-33); `HomeViewModel` (wallet) refreshed eagerly in the background.
- **Original fix:** `HomeViewModel` tracked `isScreenVisible` + a coalesced `pendingRefreshOnShow`; while
  hidden, `WalletNeedsRefresh`/`WalletAssetNeedsRefresh` were deferred and applied in `onScreenShown()`.
- **⚠️ Reverted for the wallet screen (2026-07-19, commit 2f6f538 — see TASK-45):** the wallet screen is the
  SINGLE writer of the shared `asset_balance_*` cache every OTHER screen reads on open, so deferring its
  refresh left freshly-opened screens showing the pre-transaction balance. Wallet balance signals are now
  processed LIVE (item-4 intent still met via its StateFlow on entry). **History keeps the gating** (it writes
  no shared cache), so item 4 stands where it's correct. **Files:** `HomeViewModel.kt`, `MainScreen.kt`.

### TASK-38 — Signed config bundle bootstrap (network/asset catalog from server) — ✅ Wired (2026-08-01, via TASK-53 batch 5)
- **Status (2026-08-01):** the gap recorded below is **closed**. `ConfigCatalogBootstrapper`
  (`data/config/`) is now the single consumer of `ConfigManager.getValidatedConfig()` and applies the
  result to `BlockchainRegistry.applyConfig` / `AssetRegistry.applyConfig`. `MegaWalletApplication`
  calls the bootstrapper instead of the old fire-and-forget warm-up. The local `networks.json` /
  `assets.json` are now the **seed**: they populate the registries at DI time so identity lookups never
  wait on the network, and the verified bundle replaces the catalog atomically when it arrives.
  Kill switch: `BuildConfig.CONFIG_BUNDLE_APPLY_ENABLED` (`:data`) → local-seed-only behaviour.
  See TASK-53 for the full record. **Still needs an on-device run** (first launch, tampered signature,
  version bump).
- **Server doc §3:** drive the network/asset catalog + `networkId`s from a **signed** server bundle instead
  of hardcoding: `GET /config/public-key` (pin), `GET /config/bundle` (`{version,networks,assets,signature}`,
  secp256k1-verify before trusting), `GET /config/version` (cheap re-fetch poll), `GET /capabilities`
  (feature flags).
- **Status:** ✅ Present in code — `ConfigApiService`, `ConfigBundleDto`, `Secp256k1ConfigSignatureVerifier`
  (+ `ConfigSignatureVerifier` iface), `ConfigManager` (offline-first, with `ConfigManagerOfflineFirstTest`),
  `LocalConfigAssetProvider` (bundled `networks.json`/`assets.json` as the fallback/seed), `ConfigCacheStore`,
  `CapabilityApiService` + `CapabilityManager` + `CapabilityDto`, wired in `DataModule` and consumed by
  `WalletRepositoryImpl`. So §3 is satisfied: the local JSON is now the **seed/offline fallback**, not the
  source of truth. **Verify on-device:** first launch fetches + signature-verifies the server bundle (pinned
  key), a `config/version` bump triggers a re-fetch, and an invalid signature is rejected (falls back to the
  last-good/local seed, never trusts an unverified bundle).
- **⚠️ Correction (2026-07-30) — §3 is NOT satisfied.** All the components above exist and are unit-tested,
  but **nothing consumes them**: `ConfigManager.getValidatedConfig()` has a single caller (a fire-and-forget
  warm-up in `MegaWalletApplication.kt:74`) and `BlockchainRegistry.registerNetwork` / `AssetRegistry.registerAsset`
  have **no callers outside the registries**. The registries are seeded at DI time from the **local**
  `networks.json`/`assets.json` (`core/di/CryptoModule.kt`), which is therefore still the source of truth —
  the server bundle is fetched, verified, cached, and then **ignored**. Two further blockers (testnet-only
  filter, closed `NetworkName` enum) are documented with the fix plan in **TASK-53**. Work tracked there.

### TASK-45 — Balance stays stale on every screen after a send (shared-cache + signal targeting) — ✅ Fixed
- **Problem (user, 2026-07-19):** after a withdrawal the balance was correct on-chain but **every** screen —
  including freshly-opened ones — kept showing the pre-tx amount. Three compounding causes:
  1. `SocketRefreshMapper` mapped `tx.new` → **history only**; the balance refresh relied solely on
     `balance.invalidated`, so a delivered `tx.new` alone left the shared `asset_balance_*` cache stale.
  2. TASK-44's visibility gating deferred the wallet screen's refresh while another tab was in front, but the
     wallet screen is the **single writer** of the shared balance cache all screens read → starved them.
  3. `HomeViewModel.refreshSingleAssetBalance` matched `balance.invalidated` only by `config.id == assetId`
     and otherwise fell through to `contractAddress == null`, so a token invalidation (server `"usdt"` vs
     local composite id `"USDT-SEPOLIA"`) silently refreshed the **native** asset — the token never updated.
- **Fix (commits 2f6f538, 2291664):** (1) `tx.new` → history refresh **+ `WalletNeedsRefresh`** (coarse,
  redundant with `balance.invalidated` but safe — a new tx moves the balance); (2) wallet balance signals
  processed **live**, gating reverted (see TASK-44); (3) match by **id OR symbol** (case-insensitive) OR
  contract, and refresh the whole network on no-match instead of guessing native. **Files:**
  `NotificationSocketManager.kt` (mapper), `HomeViewModel.kt`. Test: `SocketRefreshMapperTest` updated.
- **Verify on-device:** send/receive → balance updates on wallet AND on freshly-opened asset-detail/multi-wallet.

### TASK-46 — Rich tx notifications from the realtime display descriptor — ✅ Fixed
- **Problem:** `realtime-event-contract.md` §2 adds a display hint to `tx.new`
  (`direction/assetKind/asset/amountRaw/tokenSymbol/tokenDecimal`), and FCM thin signals are now **DATA-ONLY**
  (no server title/body). We parsed none of it, so the closed-app notification was always generic
  ("تراکنش جدید").
- **Fix (commit b9ae94c):** new `TxDescriptor` parsed on both WS (`parseEnvelope`) and FCM (`parseFcmData`);
  a shared pure `TransactionNotificationText` builds identical wording for both paths ("مبلغ ۱.۵ USDT دریافت
  شد." / "… ارسال شد."), generic fallback when absent. **Files:** `NotificationSocketManager.kt`,
  `PushMessageHandler.kt`. Test: `TransactionNotificationTextTest`. **Verify:** deposit with app open (sound +
  amount) and closed (FCM shows the real amount). **Deferred:** in-app toast preview separate from the notif.

### TASK-47 — MAX EVM send rejected: PROXY under-reserves the gas ceiling — ✅ Fixed
- **Problem (user, 2026-07-18 log):** a MAX native send on Sepolia PROXY was rejected at broadcast with
  `insufficient funds for intrinsic transaction cost`. The MAX deduction used the proxy fee mapper's
  `feeInSmallestUnit` = the **gasPrice-based** `estimatedCost`/`totalFee` (`gasLimit × gasPrice + l1DataFee`),
  but an EIP-1559 node reserves `gasLimit × maxFeePerGas (+ l1DataFee)` up-front. `value = balance − fee`
  therefore left `value + reserved` a few ×10¹⁰ wei **over** the balance → rejected. **PROXY-only**; DIRECT
  (`EvmDataSource.getFeeOptions`) already reserved the maxFeePerGas ceiling.
- **Fix (commit 4de78c2):** `ProxyChainDataSource.getFeeOptions` computes the EVM reserve as
  `tier.maxFeePerGas × gasLimit + (tier.l1DataFee ?: 0)` (mirrors DIRECT; ceiling ≥ totalFee so the L2 fix is
  preserved); non-EVM / older backends keep the context-aware total. **Files:** `ProxyChainDataSource.kt`.
  Test: `ProxyChainDataSourceTest` (ceiling assertion). **Verify on-device:** MAX native send in PROXY mode
  succeeds. **Known residual:** if gas rises between GET `/fees/options` and POST `/prepare`, the server
  reserves more at prepare and MAX can still fail (drift) — add a small buffer to the MAX deduction if seen.

### TASK-48 — Multi-wallet monitoring: enroll all wallets under the device-owner sub — ⏳ Spec ready, impl deferred
- **Problem:** deposits to a wallet other than the FCM device-owner wallet never surface (no FCM/socket).
  Confirmed by server code (2026-07-21): **routing is STRICTLY by the subscribing JWT `sub`** — `fcmService`
  token lookup is `{active:true, $or:[{userId},{walletAddress}]}` (`fcmService.js` `_identityFilter`), WS
  `socketsFor` the same; **no `deviceId` fan-out**. Today each wallet enrolls only its own addresses under
  its **own** `sub` (`SubscribeMonitoringUseCase`), and the device token stays under the first wallet's `sub`
  → other wallets' addresses are owned by other subs → no delivery.
- **Resolution (no auth change):** `POST /monitoring/subscribe` is **trust-the-caller** (server answer
  Q2=(a) — binds any submitted `{address,networkId}` to `req.auth.sub`, no ownership proof). So the
  **device-owner wallet's JWT can enroll EVERY wallet's addresses** and bind them all to the one anchor
  `sub` == the device's `sub`. No non-active-wallet JWT minting, no auth-layer change (the deferred
  auth refactor is NOT needed).
- **Change:** rework `SubscribeMonitoringUseCase` from "each wallet enrolls its own addresses under its own
  sub" to "the **device-owner** wallet enrolls **all** wallets' `(address, networkId)` under the anchor sub."
  **Modules:** data (+ app call site). **Files:** `SubscribeMonitoringUseCase`, `MonitoringRepositoryImpl`,
  call site in `WalletSessionAuthCoordinator`. Test: update `SubscribeMonitoringUseCaseTest` (:data).
- **Constraints (all three or it silently breaks):**
  1. **One sub per address** — replace the per-wallet-own-sub enrollment, don't add on top; an address under
     two subs → **multi-owner ambiguous → address-only fallback → no cross-wallet delivery**.
  2. **`MOBILE_PROXY_REQUIRE_AUTH=true`** on the target env, else `req.auth` empty → `sub=""` → every address
     bound to an empty owner. `true` in testnet template since `22fb530`; **mainnet default OFF** — confirm.
  3. **Address gathering** — non-active wallets are metadata-only (`getAllWallets()` → `keys=emptyList()`);
     collecting all addresses needs per-wallet key derivation, and enrollment must run while the
     **device-owner** wallet is the active session (caller sub == anchor).
- **Deps/gate:** needs a build + on-device verification + the env flag. **Priority:** P1 (gates multi-wallet
  realtime/deposit coverage). See memory `monitoring-sub-routing-open-question` (resolved),
  [[refresh-and-monitoring-policy]], [[server-integration-doc]]. Pairs with TASK-32/TASK-34.

### TASK-49 — Defer animation reads to the draw phase (recomposition reduction) — 🟢 In progress
- **Problem (user, 2026-07-21):** several continuously-running animations read their animated state
  (`rememberInfiniteTransition().animateFloat` / `animateFloatAsState`) **inside the composable body**,
  so the surrounding subtree recomposes on **every animation frame**. The Compose audit (Phase 2)
  quantified the allocation/recomposition debt but never did the Phase-3 animation pass.
- **Fix pattern (behavior-identical, low-risk):** move the per-frame state read out of composition into
  the **draw phase** via a lambda modifier (`Modifier.graphicsLayer { … }`, `drawBehind { … }`), so the
  node **redraws** per frame but never **recomposes**.
- **Done:**
  - ✅ **Shimmers** (commit becaa93) — `ShimmerWalletScreen` + `TransactionHistoryShimmer` built the
    gradient `Brush` from `translateAnim.value` in composition (`remember(translateAnim.value){…}`),
    recomposing the whole placeholder tree (every block + the history `LazyColumn`) each frame while
    loading. New `Modifier.shimmerBackground(() -> Brush)` paints via `drawBehind`; each shimmer keeps
    its gradient geometry in the lambda so the animated read is now draw-phase only.
  - ✅ **ConfirmSliderButton** (commit 4b5633e) — the idle slider's 3 pulsing chevrons built a tinted
    `Color` from `chevronAlpha` in composition, and applied `.alpha(textAlpha)` / `.scale(checkScale)`
    (all animated). Moved to `graphicsLayer { alpha/scaleX/scaleY = … }`.
- **Not built here** (Gradle unavailable) — inspection-verified; visuals identical.
- **Candidates left (assess later):** `GeneratingAnimation` (onboarding, one-shot — low priority),
  `FloatingShapesBackground` / `SendConfirmFeeSections` (appear to already draw via `Canvas`/drawscope —
  verify), and `MutableInteractionSource()` without `remember` in `AnimatedBottomSheetCard` (CU-10) —
  **blocked**: that file holds uncommitted font WIP; do it when that lands.
- **Priority:** P2 · **Risk:** Low · **Modules:** app.

---

## Sprint 6 — Optional / Hygiene

### TASK-21 — Build & dev-infra hardening
- **Problem:** `jcenter()` + insecure repos; no StrictMode; debug body-logging of signed payloads; PBKDF2
  iters low. **TD:** 09/S-10, 44 (PR-4), 33 (N-8), 39 (S-9). **Files:** `settings.gradle.kts`, app init,
  `NetworkModule` logging, `PasswordBasedCipher`.
- **Difficulty:** Low · **Est:** 1 · **Risk:** Low · **Priority:** P3.
- **Steps:** remove `jcenter()`/insecure protocol; add StrictMode (debug thread+VM policy); stop logging
  bodies; raise PBKDF2 iterations (or Argon2) + zero derived key. **Acceptance:** StrictMode clean on core
  flows; no signed payloads in logs; repos HTTPS. **Rollback:** per change. **Regression:** build/log. **Testing:**
  debug run under StrictMode; decompile check for secret.

### TASK-24 — Reuse/simplification cleanups — 🟡 Partially implemented
- **Status:** 🟡 In progress:
  - ✅ **`relayPrefixFor` dup** — the identical `routeResolver.resolve(networkId)?.relayPrefix ?: <family>`
    rule was copy-pasted in `EvmGaslessCoordinator` and `TronGaslessCoordinator`. Extracted a shared
    `IGaslessRouteResolver.relayPrefixFor(networkId, familyDefault)` extension (in `GaslessRouteResolver.kt`);
    each coordinator keeps a one-line wrapper carrying its own family default (`"evm"`/`"tron"`), so **all
    call sites are unchanged and behavior is identical**.
  - ✅ **socket reconnect backoff dup** (2026-07-19) — `NotificationSocketManager.scheduleReconnect` inlined
    `(RECONNECT_BASE_MS * 2.0.pow(reconnectAttempts - 1)).coerceAtMost(RECONNECT_MAX_MS)`, duplicating the
    schedule that `ExponentialBackoff` already owns. Replaced with the pure companion
    `ExponentialBackoff.delayForAttempt(reconnectAttempts - 1, RECONNECT_BASE_MS.toLong(), RECONNECT_MAX_MS)`
    — **behavior-identical** (same 2s→4s→…→60s curve, same overflow saturation), removed the now-unused
    `kotlin.math.pow` import. Isolated to reconnect scheduling (NOT the send path). Covered by
    `ExponentialBackoffTest`.
  - ⏳ **Deferred (crypto / send path — need build + on-device verification):** hex-parse helpers
    (`AbstractUtxoNetwork.hexToBytes/toHexString` vs `TronUtils.bytesToHex` — tiny, in two **different**
    crypto domains: BTC tx serialization vs TRON address encoding, so merging risks address/signing
    regressions for a cosmetic win), prepare→broadcast boilerplate ×5, and SmartFee/CreditFee copy-paste.
    These stay deferred until a build/device is available to validate.
- **Problem:** duplicated backoff/hex-parse, prepare→broadcast boilerplate ×5, SmartFee/CreditFee copy-paste,
  `relayPrefixFor` dup. **TD:** code-review cleanup set. **Priority:** P3 · **Est:** 1 · **Risk:** Low.
- **Testing:** existing tests green; behavior unchanged.

---

## Sprint 7 — User-requested batch (2026-07-30)

Nine items handed over by the owner. Each was grounded against the code before being written up; where
the investigation already produced an answer (TASK-53) it is recorded here rather than left open.

### TASK-2 — Swap, end to end (domain → ViewModel → UI → execution) — ✅ Implemented (needs build + on-device verify)
- **Problem:** the swap contract was fully wired to the network edge — `SwapModels.kt`, `ISwapRepository`,
  `SwapApiService`, `SwapApiDto`, `SwapRepositoryImpl`, bound in `DataModule` — and **nothing consumed it**.
  There were no use cases, no ViewModel, no screen, and `MorphingFabMenu.onSwapClick` was declared but never
  passed by `MainScreen` (the swap menu item just closed the FAB).
- **Root cause:** Phase 4 landed the transport and stopped there.
- **Done (2026-08-01):**
  - **Domain.** `domain/usecase/swap/` — `GetSwapProvidersUseCase`, `GetSwapQuoteUseCase`, `PrepareSwapUseCase`
    (thin over `ISwapRepository`) and **`ExecuteSwapBundleUseCase`**, which owns the ordered
    sign→submit→**await** loop over the existing `IUnifiedTransferCoordinator.sendPreparedTransaction`.
    No new send path: the swap/approve calldata rides `TransactionParams.Evm.data`, which DIRECT and PROXY
    both already honour, and signing stays local.
  - **Ordering is a correctness constraint, not a nicety.** The approve leg is awaited to a terminal status
    via `ITransactionStatusRepository.observeStatus` before the swap leg is submitted; firing both would run
    the swap against a zero allowance and revert. Poll exhaustion maps to a distinct
    `SwapExecutionOutcome.Stalled` (tx is on-chain, may yet confirm) rather than `Failed`, so the user is
    never told a live transaction failed and never re-sends it.
  - **TTL is enforced, not decorative.** A quote is dead after `SwapQuote.ttlMs` (default 15s). The
    ViewModel schedules a silent re-quote on expiry, and `confirm()` **re-checks the deadline against
    `SystemClock.elapsedRealtime()` at tap time** — the state flag alone loses the race on the boundary.
    The countdown itself lives in a `produceState` ticker in the UI, deliberately **not** in `SwapUiState`:
    a per-second field would re-emit the whole state and recompose every section.
  - **Money.** `BigInteger` raw base units end to end (`SwapUiState.amountRaw`, `SwapExecutionLeg.value`,
    `toAmount.min`). `Double`/`BigDecimal` appear only at the display edge (`SwapFormat`) and for the
    `slippage` field the server contract already types as `Double`. `SwapTx.value` is a **hex quantity**;
    `parseHexQuantityOrNull` returns `null` rather than 0 on a bad parse and the whole bundle is refused —
    silently zeroing a native-in swap would produce an on-chain revert.
  - **Cross-network is refused, visibly.** `SwapQuoteRequest` carries `fromNetwork`/`toNetwork` separately
    but the execution rail (`TransactionParams.Evm`) has one `networkId`. Tokens on other networks stay
    **in** the receive list (dimmed, labelled "شبکهٔ دیگر"); selecting one blocks the quote and prints the
    reason on the receive card. Hiding them would have read as "this coin doesn't exist".
  - **Errors.** 422 ⇒ `SimulationReverted`/`SwapNoRoutes`, which already have curated Persian copy in
    `ApiErrorMessageMapper`; the reason is written into `SwapQuoteState.Failed`/`SwapPrepareState.Failed`
    and rendered inline. Per TASK-57 nothing that is already visible in state also raises a snackbar, so
    every swap branch reports `ErrorSurface.SILENT` with the severity matching the stake.
  - **UI.** `screens/swap/` — one scrolling column whose `SwapMorphSection`s expand/collapse between phases
    (pay-token → amount → confirm → executing → result) while each token icon is a **single shared element**
    that FLIP-flies between slots (`SwapMorphIcon`/`flyTo`, measured via `onGloballyPositioned`), with
    `segment()`-windowed staggering off one intro value per phase. Slippage, `platformFeeBps` and
    **`toAmount.min` (the guaranteed number)** are all on screen before confirm.
  - **Motion** is centralized in `SwapMotion` and spring-based/interruptible; `ANIMATOR_DURATION_SCALE = 0`
    degrades every spec to `snap()` and the FLIP flight to an instant move, so the flow is fully usable with
    animations off (no content is gated behind an animation completing).
  - **Conventions.** Icons come from config `iconUrl` via Coil (no drawable map, no `when (networkId)`);
    fiat goes through `FiatConversion`/`BalanceFormatter` so the USD⇄تومان toggle applies; UI is Persian/RTL;
    hosted as an `AnimatedVisibility` overlay in `MainScreen` at `ZLayer.SWAP` with `MorphingFabMenu.onSwapClick`
    finally wired. Use cases are plain `@Inject` classes — no new Hilt module needed.
- **Server-contract ambiguities (flagged, single-constant so they are cheap to correct):**
  1. **Native-token identifier** for `fromToken`/`toToken` is unspecified. Uses the de-facto aggregator
     sentinel `0xEeee…EEeE` via `SWAP_NATIVE_TOKEN_SENTINEL` — the only place it is written.
  2. **`slippage` unit** is unspecified (`Double`). Sent as **percent** (50 bps ⇒ `0.5`).
  3. **No `gasLimit` in the contract.** `estimatedGas.native` is a *cost*, not a limit; the limit is
     reconstructed as `native / gasPrice × 1.2` with a 250k floor (500k when the route says nothing) and a
     120k floor for approve. `gasPrice` comes from the existing `EstimateSendFeesUseCase`.
  4. **No swap-capable token list endpoint** — the receive list is the app's data-driven asset catalog
     filtered to EVM.
  5. **Which chain a cross-network `prepare` bundle executes on** is unstated; hence the refusal above.
- **Scope:** EVM only (the `prepare` bundle is EVM-shaped: `to`/`data`/`value`), and pay tokens are the
  user's real balances with a non-zero amount.
- **Files:** new — `domain/model/swap/SwapFlowModels.kt`, `domain/usecase/swap/{SwapUseCases,
  ExecuteSwapBundleUseCase}.kt`, `app/viewmodel/swap/{SwapUiState,SwapViewModel}.kt`,
  `app/ui/compose/screens/swap/{SwapMotion,SwapComponents,SwapFormat,SwapPaySection,SwapAmountSections,
  SwapReceiveTokenSheet,SwapConfirmSection,SwapExecutionSection,SwapFlowScreen}.kt`; changed —
  `screens/main/MainScreen.kt` (host + FAB wiring + back chain), `screens/main/MorphingFabMenu.kt`
  (one `onClick`), `animations/constants/MainScreenConstants.kt` (`ZLayer.SWAP`), `.gitignore` (`/swap_test`).
  **Modules:** domain, app. **Deps:** pairs with **TASK-50** (swap rows in history).
- **Difficulty:** High · **Est:** 4 · **Risk:** Med (new money path; execution reuses the audited send rail) ·
  **Priority:** P1.
- **Not built here** (Gradle unavailable) — inspection-verified only.
- **Verify on-device:** ERC20→ERC20 needing approve (two legs, approve awaited before swap); native→ERC20
  (non-zero `tx.value`); let a quote expire on the confirm screen (confirm blocked + auto re-quote); a
  no-route pair (real Persian reason, not a generic toast); cross-network selection (explicit refusal);
  USD⇄تومان toggle across the flow; developer-options **animation scale 0** (flow fully usable).

### TASK-50 — Swap/convert transactions as a row type in history
- **Problem (user, item 1):** the history feed has no representation for a **swap/convert** transaction.
  A swap shows up (at best) as one or two unrelated transfer rows, so the user can't see "X → Y".
- **Root cause:** `TransactionRecord` (`domain/model/TransactionRecord.kt`) is a sealed class with
  **per-network** variants only (`EvmTransaction`/`TronTransaction`/`BitcoinTransaction`) and no notion of
  transaction *kind* — direction is a single `isOutgoing: Boolean`. There is no swap variant, no
  `SwapDetails`, and `HistoryItemDto`/`HistoryItemMapper` never emit one.
- **Design:** add an optional `swapDetails: SwapDetails?` (from/to symbol, from/to raw amount + decimals,
  provider, rate) to the existing variants — **do not** add a 4th sealed variant (every `when` over the
  sealed class in data/app would have to change; the per-network split is a *transport* axis, kind is
  orthogonal). `TransactionDisplayFormatter` gains `historySwapLabel(...)` + a swap branch in
  `historyPrimaryLabel`/`listAmount`; the row and `TransactionDetailsBottomSheet` render "1.5 ETH → 4,200 USDT".
- **Gate:** the unified `/history` feed must mark swap txs. Confirm the server contract first — if
  `/history` has no swap marker, the only local signal is our own outgoing swap (from TASK-2's Swap flow),
  which we can persist optimistically like `_localPending` in `TransactionHistoryViewModel`.
- **Files:** `domain/model/TransactionRecord.kt`, `data/dto/HistoryItemDto.kt` (nested-shape deserializer —
  see TASK-39), `HistoryItemMapper`, `data/formatter/TransactionDisplayFormatter.kt`,
  `screens/history/components/TransactionDetailsBottomSheet.kt` + the row composable.
  **Modules:** domain, data, app. **Deps:** pairs with **TASK-2** (Swap UI); server marker.
- **Difficulty:** Med · **Est:** 1.5 · **Risk:** Low–Med (touches the shared history model) · **Priority:** P2.
- **Acceptance:** a swap tx renders as one row with both legs and a swap glyph; non-swap rows unchanged.
  **Rollback:** null `swapDetails` ⇒ today's rendering. **Regression:** every history row type.
  **Testing:** extend `TransactionDisplayFormatterTest` (:app) with a swap fixture + a MockWebServer
  `/history` swap item.

### TASK-51 — Explorer link per network is built but never opened — ✅ Implemented (needs on-device verify)
- **Done (2026-07-30):**
  - **Wired the existing three-dot control.** The owner pointed out that `TransactionHeader` already had
    a `MoreHoriz` affordance reserved for this — it was a plain, non-interactive `Icon` with
    `contentDescription = null`. It is now an `IconButton` opening a `DropdownMenu` with **مشاهده در
    اکسپلورر** (hidden when no URL can be built, so there's never a dead entry) and **کپی هش تراکنش**.
    Opens via `LocalUriHandler`, wrapped in `runCatching` so a device with no browser can't crash the sheet.
  - **Found the actual per-network defect.** `explorers` in `networks.json` holds the explorer **API**
    base URLs (consumed by `BitcoinDataSource`/`EvmDataSource`/`TronDataSource`), and `buildExplorerUrl`
    was guessing the *web* page URL by substring-matching those API hosts. Measured against the real
    config: **BSC (both), TRON, Shasta and DOGE (both) produced no link at all** — their API hosts are
    nodereal/trongrid/blockcypher, which match no branch — and **Solana devnet produced a malformed URL**
    (`https://solscan.io/?cluster=devnet` + `/tx/…`, a path appended after a query string).
  - **Fix:** new `explorerTxUrl` template field (`{hash}` placeholder) on `NetworkConfig` → threaded
    through `BlockchainNetwork` (default `null` getter), `GenericEvmNetwork`/`TronNetwork`/
    `AbstractUtxoNetwork`, `BlockchainRegistry.toNetworkInfo()`, and `NetworkInfo`. Populated for **21 of
    22** networks in `networks.json` (purely additive: 21 insertions, 0 deletions). This keeps chains
    data-driven per the CLAUDE.md rule rather than hardcoding hosts in Kotlin.
  - `doge_testnet` deliberately has **no** template — there's no explorer we can point at with
    confidence, and it correctly falls through to producing no link rather than a dead one.
  - The old guess-from-API-base path is kept as the fallback for networks without a template (and for
    anything arriving via the server config bundle), but now iterates **all** `explorers` instead of only
    the first, so one unrecognised entry no longer costs the link.
  - **Tests:** four new cases in `TransactionDisplayFormatterTest` — template wins over the guess, the
    TRON api-host regression (null without a template, real URL with one), fall-through to a later
    explorer, and null when there's no usable explorer.
- **Not built here** (Gradle unavailable) — inspection-verified.
- **Verify on-device:** open a transaction on EVM / TRON / BTC and tap the three-dot → the correct
  explorer page opens for each. **Please sanity-check these templates against a real tx**, since a few
  were not verifiable from here: `shasta.tronscan.org`, `testnet.xrpscan.com`, `testnet.tonscan.org`, and
  `blockchair.com/litecoin/testnet` (the last three follow the hosts already in `explorers`).
- **Original spec below.**

- **Problem (user, item 2):** the user can't open a transaction on its block explorer from history.
- **Root cause:** the URL builder **exists and is exposed but has zero UI call sites.**
  `TransactionDisplayFormatter.buildExplorerUrl` (`data/formatter/TransactionDisplayFormatter.kt:265`)
  is delegated by `TransactionHistoryViewModel:846` — and nothing in `ui/**` ever calls it. There is no
  `LocalUriHandler`/`ACTION_VIEW` anywhere in the Compose tree (grep over `app/.../ui`: 0 hits).
  Two further defects in the builder itself:
  1. it only reads `network.explorers.firstOrNull()` — same single-explorer weakness fixed for Tron reads
     in TASK-41;
  2. the family match is a **substring `when` over the base URL** with a hard `else -> null`, so any
     explorer host not in the hardcoded list (blockscout/tronscan/mempool.space/blockchair/xrpscan/
     solscan/tonscan/basescan/etherscan) silently yields **no link** — including any explorer that arrives
     via the server config bundle (see TASK-53).
- **Do:** (1) add an "مشاهده در اکسپلورر" action to `TransactionDetailsBottomSheet` that opens the URL via
  `LocalUriHandler.current.openUri(...)`, hidden when the builder returns null; (2) drive the path template
  from the network config instead of substring-sniffing the host — add an optional `explorerTxPath`
  (e.g. `/tx/{hash}`, `/#/transaction/{hash}`) to `NetworkConfig`/`networks.json`, keeping the current
  `when` only as the fallback for configs that don't carry it; (3) fall back across `explorers` in order.
- **Files:** `data/formatter/TransactionDisplayFormatter.kt`,
  `screens/history/components/TransactionDetailsBottomSheet.kt`, `core/src/main/assets/networks.json`,
  `NetworkConfig`. **Modules:** data, core, app. **Deps:** none.
- **Difficulty:** Low · **Est:** 0.75 · **Risk:** Low · **Priority:** P1 (user-visible gap, cheap).
- **Acceptance:** opening a tx on EVM/TRON/BTC each opens the correct explorer URL in the browser; an
  unknown/unset explorer shows no dead button. **Rollback:** hide the action. **Regression:** detail-sheet
  layout. **Testing:** extend `TransactionDisplayFormatterTest` per family + a config-driven template case;
  on-device tap per network.

### TASK-52 — Copy addresses (and hash) from the transaction detail — ✅ Implemented (needs on-device verify)
- **Done (2026-07-30):**
  - New shared `rememberClipboardCopier()` (`ui/compose/components/ClipboardCopy.kt`) — one copy path, so
    a third variant doesn't appear next to the existing two. Uses `setPrimaryClip` rather than the
    deprecated `ClipboardManager.text` setter, and ignores blank input so a not-yet-loaded value can't
    silently wipe the user's clipboard.
  - `DetailItem`/`DetailRow` gained `copyValue` — the **full** text, kept separate from the shortened
    `value` that's displayed, so copying doesn't hand back a truncated (useless) address. A copyable row
    is clickable (clickable applied before padding, so the padding is part of the touch target) and shows
    a small `ic_copy` glyph with a `کپی <label>` content description.
  - Copyable now: tx hash, contract address (EVM + TRON), and the token-transfer to/from.
  - **Native transfers had no address rendered at all** — `TokenTransfersCard` only exists for token
    transfers, so for a plain send/receive there was literally nothing to copy. Added ارسال از / ارسال به
    rows to `buildGeneralRows`, gated on `transactionTokenTransfer(transaction) == null` so token
    transfers don't show the same two rows twice.
  - `ic_copy.xml` was unused and sat in `:app` while its siblings (`ic_hash`, `ic_wallet`, `ic_gas`) live
    in `:common_ui`; moved it there so this file keeps a single `R` import. No references to update.
  - **Confirmation:** Android 13+ draws its own copy confirmation, so the toast only fires below API 33.
    A styled in-app success snackbar would be better, but the only snackbar the app has today is
    error-styled (red, `colorError`) — building a success variant belongs to **TASK-57**.
- **Not built here** (Gradle unavailable) — inspection-verified.
- **Verify on-device:** tap each address row and the hash → paste gives the **full** value; check a native
  transfer (new rows) and a token transfer (no duplicate rows); confirm a single confirmation on A13+.
- **Original spec below.**

- **Problem (user, item 3):** addresses in a transaction can't be copied.
- **Root cause:** `TransactionDetailsBottomSheet` renders `DetailRow`/`SummaryRow` as plain `Text` — no
  clipboard action anywhere in `screens/history/**` (grep: 0 clipboard hits). The pattern already exists
  elsewhere: `ReceiveScreen.kt:79,134` and `SecretRevealOverlay.kt:85,161`.
- **Note (do this consistently):** the two existing call sites use **different APIs** — `LocalClipboard`
  (+ `nativeClipboard`) in `ReceiveScreen`/`SendScreen` vs the deprecated `LocalClipboardManager` in
  `SecretRevealOverlay`. Pick `LocalClipboard` and add a small shared `copyToClipboard(...)` helper in
  `ui/compose/components/` rather than a third variant.
- **Do:** make the from/to address rows + the tx hash copyable (tap or a trailing copy icon), with the
  existing top-snackbar confirmation ("کپی شد") and a `contentDescription` on the icon (TASK-17 policy).
  On Android 13+ the OS shows its own copy toast — don't double-toast.
- **Files:** `screens/history/components/TransactionDetailsBottomSheet.kt` (`DetailRow`/`SummaryRow`/
  `buildGeneralRows`), new shared clipboard helper. **Modules:** app. **Deps:** none.
- **Difficulty:** Low · **Est:** 0.5 · **Risk:** Low · **Priority:** P1 (cheap, pairs with TASK-51).
- **Acceptance:** long-press/tap copies the full (un-truncated) address; snackbar confirms.
  **Rollback:** revert. **Regression:** sheet layout/scroll. **Testing:** on-device paste-check per row.

### TASK-53 — Data-driven networks: a bundle-added EVM chain works with no app update — ✅ Implemented (2026-08-01, needs build + on-device verify)

**Delivered in five batches. Nothing below was compiled or run in the authoring environment —
every item is "verified by inspection" unless marked otherwise.**

| Batch | Commit | What |
|---|---|---|
| 1 | `a73cd56` | Family checks key on `NetworkType`, not `NetworkName` lists |
| 2a | `1526bd7` | `explorerApi` + `hasL1DataFee` become `NetworkConfig` data |
| 3 | `eb54ded` | `networkId` is the identity; `NetworkName` a nullable alias |
| 4 | `22648cf` | Register every chain; testnets gated on a user toggle |
| 2b | `77a1ca0` | Network icons from config, not a drawable map |
| — | `8d3766d` | `doge_testnet` chainId collision fix |
| 5 | (this) | The verified bundle is applied to the registries |

- **Verified by inspection:** the five `NetworkName.valueOf` throw-sites are gone; the only remaining
  name-keyed code is genuine per-chain UTXO behaviour (`UtxoNetworkParametersResolver`,
  `BitcoinDataSource`, `BitcoinjUtxoTxBuilder`, `ProxyChainDataSource:177`); `getAllNetworks()`
  (identity/derivation) is never filtered while `getAllNetworkInfos()` (UI listing) is; registry
  writes are atomic snapshot swaps.
- **Covered by JVM tests:** unknown-name config registers instead of throwing, address derivation is
  byte-identical to a known chain, listing tracks the toggle without re-registering, chainId
  collisions keep the first entry, `applyConfig` replaces rather than merges, identity overrides
  (chainId / derivationPath / addressRegex / asset contractAddress) are rejected, a tampered
  signature applies nothing, an empty or offline bundle keeps the seed.
- **Still needs an on-device run:** first launch fetch + verify + apply; a `config/version` bump
  re-fetch; a genuinely bundle-only chain deriving an address, reading balances, showing history and
  **sending**; DIRECT vs PROXY equivalence; and the testnet toggle in a release build.
- **Known limitation (by design):** only **EVM** is fully data-driven. A new UTXO chain still needs
  code, because bitcoinj `NetworkParameters` are per-chain; `BitcoinNetworkFactory`/`UtxoNetworkFactory`
  throw a descriptive error for an aliasless UTXO chain and `registerFromConfig` skips it.
- **Known data issue:** `xrp_mainnet` collides with `bitcoin_mainnet` on chainId 0 and `xrp_testnet`
  with `ethereum_mainnet` on 1. Harmless today (XRP has no factory, so neither registers) but must be
  fixed before an XRP family is added. The `registerNetwork` guard will log it loudly.

#### Original investigation (2026-07-30) — kept for the record

- **Question (user, item 4):** if a network exists in the server config bundle but not in the device's
  local file, does the app show it?
- **Answer: no — and there are three independent blockers.** Verified 2026-07-30:
  1. **The verified bundle is never applied.** `ConfigManager.getValidatedConfig()` has exactly **one**
     consumer: a fire-and-forget warm-up in `MegaWalletApplication.kt:74`. `BlockchainRegistry.registerNetwork`
     / `AssetRegistry.registerAsset` have **no callers outside the registries themselves**. The registries are
     built in `core/di/CryptoModule.kt` (`provideBlockchainRegistry` → `loadNetworksFromAssets(context)`,
     `provideAssetRegistry` → `loadAssetsFromAssets(context)`) — i.e. **local `networks.json`/`assets.json` only**.
  2. **A testnet-only filter.** `BlockchainRegistry.loadNetworksFromAssets` (`:279`) does
     `configs.filter { it.isTestnet == true }` — mainnet configs are dropped even from the local file.
  3. **`NetworkName` is a closed enum** (`domain/model/core/NetworkType.kt:14`, 23 constants). Even with
     1 and 2 fixed, a server network whose name isn't a compile-time constant can't be represented — the
     whole domain keys on `NetworkName`, and `INetworkCatalog.getNetworkInfoByName` would never match.
- **⚠️ This corrects TASK-38**, which is marked "✅ Already implemented (verify)". The *components*
  (`ConfigApiService`, `Secp256k1ConfigSignatureVerifier`, `ConfigManager`, `ConfigCacheStore`,
  `LocalConfigAssetProvider`) all exist and are unit-tested — but they are **not wired into the registries**,
  so the local JSON is still the source of truth, not a seed. TASK-38 status downgraded to 🟡 below.
- **Do (staged, blockers in order):** (1) feed the validated bundle into the registries — a
  `registry.applyConfig(bundle)` that re-registers networks/assets from the verified bundle, ordered before
  first wallet read (`MegaWalletApplication` warm-up already awaits it; make the registries observe it
  rather than snapshot at DI time); (2) replace the `isTestnet == true` filter with the build-flavour /
  env-appropriate predicate; (3) decide the `NetworkName` question — either keep the enum and **skip
  unknown networks with a logged warning** (safe, small, preserves type-safety) or migrate the domain to a
  string `networkId` key (large, ripples through every `when(NetworkName)`). **Recommend (3a) for now**
  and raise the migration as its own task if the server really needs to add chains without an app update.
- **Files:** `core/di/CryptoModule.kt`, `core/registry/BlockchainRegistry.kt`, `core/registry/AssetRegistry.kt`,
  `data/config/ConfigManager.kt`, `MegaWalletApplication.kt`, `domain/model/core/NetworkType.kt`.
  **Modules:** core, data, app, (domain if the key migrates). **Deps:** TASK-38.
- **Difficulty:** Med–High · **Est:** 2 (3 if the enum migrates) · **Risk:** **High** — the registries seed
  key derivation and address validation; a bad network entering the registry is a correctness/funds risk.
  Signature verification must gate registration (never register an unverified bundle). · **Priority:** P1.
- **Acceptance:** a network present only in the signed bundle appears in the app (or is *deliberately and
  visibly* skipped with a log); an unverified/tampered bundle registers nothing and falls back to the local
  seed. **Rollback:** feature-flag `applyConfig` off ⇒ today's local-only behavior. **Regression:** all key
  derivation, address validation, balance reads. **Testing:** `ConfigManagerOfflineFirstTest` extended with
  an apply step; a bundle-only network fixture; on-device first-launch + tampered-signature run.

### TASK-54 — USDT→Toman (Wallex) rate is fetched but the UI never updates — ✅ Implemented (needs build + on-device verify)
- **Done (2026-07-30):**
  - New `IUsdToIrrRateProvider` (domain) + `UsdToIrrRateProvider` (data, `@Singleton`, bound in
    `DataModule`): `rate: StateFlow<CurrencyRate?>`, `ensureSeeded()` (local only, never network) and
    `refresh(force)`. Owns the 3-minute TTL, single-flights concurrent refreshes behind a `Mutex`, seeds
    from the existing `LAST_IRR_RATE` key on cold start and persists on success. `null` = unknown; a
    failed refresh keeps the last good value and never fabricates one.
  - `HomeViewModel`: private `cachedIrrRate`/`lastIrrRateUpdateTime`/`IRR_RATE_CACHE_DURATION_MS` and all
    four ad-hoc `LAST_IRR_RATE` cache reads/writes deleted; the five internal uses read
    `currentIrrRate` off the provider, and `usdToIrrRate` is exposed for the UI. The initial-load path
    uses `ensureSeeded()` so it stays as fast as the direct cache read it replaces.
  - **`suspend fun getUsdToIrrRate(): BigDecimal` replaced by `refreshUsdToIrrRate()` returning Unit.**
    Returning the rate is what invited callers to snapshot it into a field and stop noticing updates;
    there is now no way to take a private copy through the VM.
  - `SendViewModel`: the one-shot `init` fetch and the **hardcoded `BigDecimal("70000")` fallback** are
    gone (that number silently priced every Toman amount *and the MAX-send math* whenever the fetch
    failed, with the failure swallowed by `else -> {}`). Now tracks the provider; unknown is ZERO.
  - `AssetDetailScreen`: `collectAsStateWithLifecycle()` replaces
    `LaunchedEffect(homeViewModel) { irrRate = it.getUsdToIrrRate() }` — the effect key never changed,
    which is the precise reason the screen froze the rate it read on entry.
  - `GetUsdToIrrRateUseCase` **removed** — it was the one-shot accessor the bug was built on, and the
    provider is now the only production caller of `IMarketDataRepository.getUsdToIrrRate()`.
- **Tests:** `UsdToIrrRateProviderTest` (:data, MockK + `runTest`) — TTL reuse, forced refresh,
  single-flight under 8 concurrent callers, seed-without-network, seed-once, failure keeps the last good
  value, and failure before any value leaves it `null` rather than `0`.
  Run: `./gradlew :data:testDebugUnitTest --tests "*UsdToIrrRateProviderTest"`.
- **Not built here** (Gradle unavailable) — inspection-verified.
- **Verify on-device:** open asset detail and leave it open across a rate change → the Toman value
  updates **without reopening the screen**; wallet / asset detail / send agree at the same instant;
  kill the network → the last known value stays and no `70000` appears.
- **Original spec below.**
- **Problem (user, clarified 2026-07-30):** the **Toman** price of USDT — fetched from **Wallex** — is
  visibly retrieved in the log, yet the UI keeps showing the old value. It applies **everywhere the rate
  is used**, and different screens can disagree with each other.
- **⚠️ Scope correction.** An earlier version of this entry diagnosed the *USD* price and blamed
  id-vs-symbol keying (the TASK-45 class of bug). That was wrong. The USD path is not the subject; the
  defect is in the **USD→IRR/Toman rate** and the mechanism is **observability**, not keying.
- **Root cause — the rate is a value that gets *pulled*, never state that can be *observed*.**
  The fetch itself is fine: `MarketDataRepositoryImpl.getUsdToIrrRate()` calls Wallex
  (`USDTApiService` → `GET https://api.wallex.ir/v1/all-fairPrice`, field `result.uSDTTMN`) and returns a
  `CurrencyRate`. That call is what shows up in the log. Nothing downstream is reactive:
  1. **`HomeViewModel.getUsdToIrrRate()` (`:667`)** is a `suspend fun` returning `BigDecimal`, behind a
     private 3-minute cache (`cachedIrrRate` / `lastIrrRateUpdateTime`, `:75-81`). It is **not** a
     `StateFlow`, so when a fresh rate arrives **nothing is notified**. Six call sites read the private
     field directly (`:245, 291-294, 355, 412, 550`).
  2. **`AssetDetailScreen` (`:449-455`)** — the visible symptom:
     ```kotlin
     var irrRate by remember { mutableStateOf(BigDecimal("0")) }
     LaunchedEffect(homeViewModel) { irrRate = it.getUsdToIrrRate() }
     ```
     the effect key is `homeViewModel`, which **never changes for the life of the screen**, so the rate is
     read **once on open and then frozen** — even if the app stays open for an hour.
  3. **`SendViewModel` (`:152-163`)** calls `fetchIrrRate()` once in `init` into a plain
     `var currentIrrRate` (not state), used for the Toman display and MAX math (`:277, 1141, 1249, 1276`).
     Its default is a **hardcoded `BigDecimal("70000")`**, so a failed fetch silently prices Toman off a
     stale magic number. The failure is swallowed by `else -> {}`.
  4. Persistence is ad-hoc: `LAST_IRR_RATE` is read/written straight from `HomeViewModel` at four sites.
  Net effect: three independent copies of the rate, captured at three different moments, none observable —
  which is exactly "the log has the new price but the screen does not", on every screen that uses it.
- **Do:** give the rate a single observable owner.
  - `domain/interfaceRepository/IUsdToIrrRateProvider.kt` — `val rate: StateFlow<CurrencyRate?>` +
    `suspend fun refresh(force: Boolean = false)`. Mirrors the existing `IActiveWalletProvider` shape;
    `null` means **not yet known** and must render as a placeholder, never `0` and never a guess.
  - `data/repository/UsdToIrrRateProvider.kt` — `@Singleton`, owns the TTL, single-flights concurrent
    refreshes behind a `Mutex`, seeds from `LAST_IRR_RATE` on cold start and persists on success.
  - Consumers **collect** instead of pulling: `HomeViewModel` drops its private cache and the four
    `LAST_IRR_RATE` call sites; `SendViewModel` collects (and loses the `70000` fallback);
    `AssetDetailScreen` uses `collectAsStateWithLifecycle()` instead of `LaunchedEffect` + `var`.
- **Files:** new `IUsdToIrrRateProvider` + `UsdToIrrRateProvider` (+ `DataModule` binding),
  `viewmodel/HomeViewModel.kt`, `viewmodel/SendViewModel.kt`,
  `ui/compose/screens/wallet/AssetDetailScreen.kt`. **Modules:** domain, data, app.
  **Deps:** **TASK-56** (Toman toggle) multiplies by this rate on every fiat surface — it must land after.
- **Difficulty:** Med · **Est:** 1.5 · **Risk:** Med (money display + MAX-send math) · **Priority:** P1.
- **Acceptance:** the Toman value updates on screen as soon as a new Wallex rate arrives, without
  reopening the screen; wallet, asset detail and send agree at any instant; a failed fetch shows a
  placeholder rather than `0`, a frozen number, or `70000`.
  **Rollback:** per consumer. **Regression:** every Toman display + MAX-send math.
  **Testing:** JVM tests for the provider (TTL reuse, forced refresh, single-flight under concurrency,
  cold-start seed from cache, failure keeps last good value and never invents one).

### TASK-55 — Token list from server search (assets not in the bundle)
- **Problem (user, item 6):** tokens outside the config bundle should be findable via server search; the
  section doesn't work.
- **Current state:** it doesn't exist at all. There is **no search UI anywhere** in `ui/**` (0 hits for a
  search field), **no** search endpoint on `ConfigApiService`/`MobileProxyApiService`, and `ExploreScreen.kt`
  — the natural home — is a placeholder animation with no data. `AssetRegistry` is local-`assets.json`-only
  (see TASK-53).
- **Owner confirms the server exposes a search endpoint** — first step is to pull its exact path/params/
  response from the server contract doc (`ANDROID_SERVER_INTEGRATION.md`, server repo `docs/`).
- **Do:** (1) transcribe the endpoint contract into `docs/`; (2) DTO + service method + an
  `IAssetSearchRepository` (interface in `domain/interfaceRepository/`) + impl in `data`, routed like the
  other proxy calls; (3) a debounced search UI in `ExploreScreen` (reuse `SendTokenList`'s row) with
  empty/loading/error states; (4) decide what "adding" a found token means — display-only, or persisted
  into a user token list that the wallet screen merges with the registry (persist via
  `IUserPreferencesRepository`, mirroring the connection-mode pref).
- **⚠️ Security:** a server/user-supplied token is **untrusted input** — validate the contract address per
  network (`INetworkCatalog.isValidAddressForNetworkId` exists), never let a searched token override a
  bundled asset's identity, and show a "not in verified list" marker. A spoofed USDT is a funds risk.
- **Files:** new DTO + `AssetSearchApiService` + `AssetSearchRepositoryImpl` + DI (`DataModule`/`NetworkModule`),
  `domain/interfaceRepository/IAssetSearchRepository.kt`, `screens/explore/ExploreScreen.kt` + a new
  `ExploreViewModel : BaseViewModel`. **Modules:** domain, data, app. **Deps:** TASK-53 (shares the
  bundle-vs-server asset question).
- **Difficulty:** Med · **Est:** 2 · **Risk:** Med (untrusted token metadata) · **Priority:** P2.
- **Acceptance:** searching a known symbol returns server results; selecting one shows it correctly (right
  network/decimals/contract) and is clearly marked as unverified; an invalid contract is rejected.
  **Rollback:** hide the Explore entry. **Regression:** none (additive). **Testing:** MockWebServer search
  fixtures (hit/empty/error), address-validation unit tests.

### TASK-56 — USD ⇄ Toman toggle icon on MainScreen — ✅ Implemented (needs build + on-device verify)
- **Review follow-up (2026-07-31):** the wallet switcher was the one fiat surface the toggle could not
  reach. `ICachedWalletBalanceReader.getCachedTotalBalance()` returned an already-formatted `"$1,234"`
  built in the **data layer**, so the currency was baked in before any ViewModel saw the number, and
  `MultiWalletScreen` showed USD regardless of the selection. Renamed to `getCachedTotalUsd()` returning
  raw `BigDecimal`; `MultiWalletViewModel` keeps the raw total on `WalletUiItem.totalUsd` and re-formats
  on every change of **currency or rate** (the rate matters too — a تومان total is unrenderable until
  the first Wallex value lands). Fixed in passing: `WalletBalanceSynchronizerImpl` decided "is this
  wallet cached?" by comparing the formatted string against `"$0"`/`"0"`, which silently depended on how
  the formatter renders zero — a sub-cent balance formatted `"$0.00"` matched neither literal and was
  treated as cached. It is now a numeric `> BigDecimal.ZERO` test.
- **Deliberately NOT converted:** `CloudWalletBalanceCalculatorImpl` → `CloudBackupWalletListScreen:224`
  renders `"<amount> تتر"` — it is labelled in **USDT**, not in the fiat display unit, and it appears
  mid-restore when the rate may well be unknown (a `—` placeholder there would make the backup picker
  harder to use, not easier). Left as USDT on purpose.
- **Also closes TASK S §2.2-D's fiat-currency preference.** The pref and the toggle were built as one
  unit; building the pref twice was the main risk in splitting them.
- **Done (2026-07-31):**
  - **`FiatCurrency` (domain) replaces `HomeUiState.DisplayCurrency`.** A `DisplayCurrency {IRR, USDT}`
    already existed and `MainHeader` already had an `onCurrencyToggle` wired to
    `HomeViewModel.toggleDisplayCurrency()` — but it lived **inside `HomeUiState.Success`**, so it was
    invisible to every other screen and died with the process. Rather than add a second enum next to it,
    it was replaced (USDT→USD, IRR→TOMAN) across the ~7 files that referenced it.
  - **Persistence + observability, separately.** `getFiatCurrency`/`setFiatCurrency` on
    `IUserPreferencesRepository` (mirroring `get/setConnectionMode`) is the storage; new
    `IFiatCurrencyProvider` + `FiatCurrencyProvider` (`@Singleton`, bound in `DataModule`) is the
    **observable** owner every ViewModel collects — deliberately the same shape as
    `IUsdToIrrRateProvider`. A suspend getter alone would have reproduced TASK-54's defect: each screen
    snapshots the value and stops noticing changes. Seeded USD and hydrated by `ensurePrimed()` off the
    main thread (the TD-19 lesson from `DefaultBlockchainConnectionModeProvider`), and `set` takes the
    same lock as `ensurePrimed` so a cold-start read cannot revert a tap that lands mid-read.
  - **The IRR-vs-Toman decision, made once and written down** in `core/utils/FiatConversion`:
    **the value has always been تومان, and there is no `/10` in the production path.**
    `MarketDataRepositoryImpl` reads Wallex `result.uSDTTMN` — TMN — into a local literally named
    `latestPriceToman`, then labelled it `quoteCurrency = "IRR"` with a comment admitting the label was
    wrong. Adding a divisor would have made every تومان amount **ten times too small**. Instead the two
    producers now label it `"TMN"`, and `FiatConversion.tomanPerUsd` resolves the unit **from the label**
    — passing `IRR`/`RIAL` through the one and only `/10` in the codebase, and returning `null`
    (unknown) for a label it does not recognise rather than guessing. That keeps the relayer's genuine
    `irr` field adoptable later without touching a single call site.
  - **Unknown rate ⇒ placeholder (`—`), never `0`.** `BalanceFormatter.formatFiatValue` is the single
    fiat entry point and returns `FiatConversion.UNKNOWN_PLACEHOLDER` when تومان is selected and the rate
    is unknown. Four sites that rendered a confident zero were fixed: the asset-detail تومان price
    (`currentPriceUsd * (rate ?: ZERO)`), the history receipt (`?: "0.00"`), the fee card's تومان line,
    and the send confirm sheet. **Toman display scale is 0** (whole تومان, Persian `٬` separator).
  - **Wallet list now re-derives its fiat strings on every pass** (`createAggregatedListWithRates` →
    `withFiatBalances`, groups **and** singles). Previously only group headers were rebuilt, so a single
    asset carried whatever string was written when its balance last changed — a currency switch, or a
    fresh Wallex rate, left it in the old unit until a price refresh happened to run. `HomeViewModel`
    now also **collects the rate flow**, closing the last gap TASK-54 left: the rate became observable
    but the list's تومان strings were still baked at fetch time.
  - **One formatting policy.** `AssetItem.withFiatBalances` (`:core`) is the only place an asset's fiat
    strings are produced; `HomeViewModel` and `SendViewModel` both call it. `ISendAssetDataSource` lost
    its `irrRate` parameter and no longer formats at all — it was emitting a *different* shape
    (`"$12.34"` / `"1,234 تومان "` vs the list's bare numbers) with no idea which currency was selected.
  - **MAX-send math (money-critical) — three real defects fixed.**
    1. MAX was expressed only as **text**: the screen wrote the formatted balance into the amount box and
       the send path parsed it back. Through a 2-decimal USD string that never recovered the exact
       balance — and it also made the downstream `baseCrypto >= balanceRaw` max-detection fail, so a
       **native MAX skipped the fee subtraction** and produced an unsendable transaction. With تومان
       (0 decimals) the round-trip is coarser still. `useMax()` now sets an `isMaxAmount` flag and
       `getBaseCryptoAmount` returns `balanceRaw` **exactly**; the box keeps the pretty text.
    2. The fiat→crypto division rounded **HALF_UP**, which can round *up* to more crypto than the user
       holds. Now **DOWN**. `useMax` likewise rounds its displayed figure DOWN in both currencies.
    3. `isOverBalance` compared a typed **fiat** amount against `balanceRaw × priceUsdRaw` (i.e. against
       the balance in **USD**). With تومان selected that compared تومان to dollars and would have
       flagged nearly every valid amount as over-balance. Both sides are converted first.
  - `isUsdMode` → `isFiatMode` throughout the send flow, so no call site can keep assuming the fiat side
    is dollars. The dead `getUsdDisplay`/`getIrrDisplay` pair was removed.
  - **`AssetDetailViewModel` had a private `MutableStateFlow(USDT)` with a `setDisplayCurrency` setter
    that nothing ever called** — asset detail was pinned to USD regardless of the user's choice. It now
    delegates to the shared provider.
  - **Header control shows the ACTIVE currency** (`$` / `تومان`) rather than the one a tap switches to:
    it is the only on-screen indicator of what unit the balances are in. Driven by
    `MainScreenViewModel.toggleFiatCurrency()` (which owns the other header state), with a Persian
    content description.
- **Tests:** `FiatFormattingTest` (**:core**, 20 cases) — the ×10 unit normalisation both ways, TMN/IRR
  producing identical output, unrecognised label ⇒ unknown, تومان rounding half-up to whole units,
  USD 2-dp and sub-cent 6-dp, `withFiatBalances` populating both currencies, the unknown-rate placeholder
  and the assertion that **a genuine zero is still `0`, not the placeholder**.
  `FiatCurrencyProviderTest` (**:data**, 8 cases) — survives-process-death read-back, prime-once,
  8-way concurrent prime collapsing to one read, toggle both ways persisting each flip, toggle honouring
  the persisted value, and a late `ensurePrimed` unable to revert a fresh tap.
  `TransactionDisplayFormatterTest` (:app) gained 5 history-fiat cases.
  Run: `./gradlew :core:testDebugUnitTest :data:testDebugUnitTest`.
- **Not built here** (Gradle unavailable) — inspection-verified only. **No claim is made that this
  compiles or that the tests pass.**
- **Known remaining (deliberately out of scope, still USD):** `CachedWalletBalanceReaderImpl` (the
  multi-wallet switcher list) and `CloudWalletBalanceCalculatorImpl` (the cloud-restore list). Both are
  data-layer producers for lists of *other* wallets and would each need currency+rate threaded through
  their own interface; neither is one of the four surfaces this task names.
- **Verify on-device:** tap the header control → wallet total, per-asset rows, asset detail, the send
  picker, the send amount box + its equivalent line, the confirm sheet, and history rows all flip
  together; kill and relaunch the app → the choice is still تومان; turn off the network before the first
  rate arrives → every تومان surface shows `—`, none shows `0`; **send MAX in تومان on a native asset
  (ETH/BTC/TRX) and confirm it broadcasts** rather than failing for insufficient funds.
- **Original spec below.**

### TASK-56 — USD ⇄ Toman toggle icon on MainScreen (original spec)
- **Problem (user, item 7):** no way to see values in Toman; everything is USD.
- **Current state:** `domain/model/CurrencyRate.kt` already models `baseCurrency`/`quoteCurrency`/`rate` and
  `MarketDataRepositoryImpl` provides rates, and `RelayerPriceDto` already carries an `irr` field — but
  `BalanceFormatter.formatUsdValue` (`core/utils/`) and `data/formatter/TransactionDisplayFormatter` are
  **hardcoded USD**, so no display honours a currency choice.
- **Do:** add the toggle control to `screens/main/MainHeader.kt` (extracted in TASK-13) driven by
  `MainScreenViewModel`; the selected currency is the **same pref** as the Settings "fiat currency" item —
  build it once in the shared prefs/formatter layer, not twice. Toman vs Rial is a **display divisor of 10**
  on the server's IRR value — decide and centralize it (`IRR/10 = Toman`), never per call site.
- **Files:** `screens/main/MainHeader.kt`, `viewmodel/MainScreenViewModel.kt`,
  `core/utils/BalanceFormatter.kt`, `data/formatter/TransactionDisplayFormatter.kt`,
  `IUserPreferencesRepository` (+impl). **Modules:** core, data, app, domain.
  **Deps:** **TASK S §2.2-D** (fiat-currency pref) — **built here**, in the same change: the pref and the
  toggle are one unit and building the pref twice was the main risk in splitting them;
  **TASK-54** (one price source) should land first or the toggle will surface the inconsistency.
- **Difficulty:** Med · **Est:** 1 (0.5 on top of TASK S) · **Risk:** Med (money display) · **Priority:** P2.
- **Acceptance:** one tap switches **every** fiat display (wallet total, per-asset, detail, send, history) to
  Toman with Persian digit grouping and back; the choice survives process death.
  **Rollback:** hide the icon (pref defaults USD). **Regression:** all fiat formatting. **Testing:** JVM
  formatter tests for both currencies incl. rounding; on-device sweep of every fiat surface.

### TASK-57 — Custom error surface: show every error the user needs to know — ✅ Implemented (needs build + on-device verify)
- **Done (2026-07-30):**
  - **The real defect was worse than "coverage".** `BaseViewModel` mirrored `ErrorManager.errorEvents`
    into a **per-ViewModel** `uiEvents` channel, and the only two collectors in the whole app were
    `CreateWalletScreen` and `AddExistingWalletScreen`. Every error raised anywhere in the main app —
    including the failed-send path — was emitted into a channel nobody read. Fixed by making
    `ErrorManager` the single app-wide message bus (`uiMessages: SharedFlow<UiEvent>`) with exactly one
    `AppMessageHost` mounted per Activity (`MainActivityCompose`, `WelcomeActivityCompose`); the two
    per-screen mounts were removed so nothing double-renders. `ErrorSnackbarHandler.kt` is deleted and
    `ErrorEvent`/`ErrorAction`/`ErrorHandlingResult` (zero consumers outside `BaseViewModel`) collapsed
    into that one flow.
  - **Severity policy** — new `domain/model/error/ErrorSurface.kt`: `SILENT` (logs only — background
    price/balance/fee refresh, sponsor+gasless preview enrichment, address-book warm-up, cache misses),
    `SNACKBAR` (user-initiated read/write that failed and is retryable), `BLOCKING` (money, keys or data
    at stake — send/broadcast, wallet delete, cloud-backup upload/delete, wallet restore/import). Applied
    at every call site via `BaseViewModel.reportError(..., surface = …)`. `launchSafe` gained
    `connectivitySurface` so a background refresh on an offline device no longer raises a snackbar per tick.
  - **The confirmed bug** — `MultiWalletViewModel.deleteWallet` printed `result.toString()` (the raw
    `ResultResponse.Error` object) with a dead `?:` fallback. Now `reportError(..., BLOCKING)` plus a
    success snackbar on the happy path.
  - **PII (TASK-25)** — new `ErrorTextSanitizer` scrubs EVM addresses/hashes/signed payloads, bare hex
    blobs, Tron/BTC/DOGE base58, bech32, BIP-39 mnemonics and JWTs out of *all* technical text (dialog
    **and** log), and clamps it to 400 chars. The short message never comes from an exception at all.
  - **Success snackbar** — `UiEvent.ShowSuccessSnackbar` + `TopSnackbarStyle.ERROR/SUCCESS` in
    `CustomTopSnackbar` (green `primary`, auto-dismiss, no "جزئیات"). Unblocks TASK-52 and TASK-58.
    `deleteCloudBackup` was showing its **success** message in the red error snackbar — fixed.
  - **`ErrorMapper` gaps closed** — `ApiError.IdempotencyKeyConflict` had no copy at all in either
    language (the `else -> ""` branch produced an **empty** snackbar); the `else` is now removed so the
    compiler enforces coverage. `AppError.Network.Unknown` and `Business.General` also had no branch and
    fell through to "خطای ناشناخته". Added `userMessage(throwable, fallbackMessage)`: the taxonomy wins,
    and the call site's Persian description of the action is used only when it has nothing to say.
  - **Fixed in passing:** `SendViewModel.shouldRetryGasless` branched on **English substrings in
    `e.message`** (violating API invariant #3, "branch on the code, never the message") — it now reads the
    typed `ApiError` off the cause, with the substring pass kept only as a fallback. This mattered because
    the 21 `throw IllegalStateException("<Persian>")` sites became `sendFailure(...)`
    (`AppError.Business.General`, cause preserved) so their curated copy survives mapping. One English
    leak ("EVM approve amount was not returned by server") translated. `CancellationException` is now
    rethrown in the new catches rather than reported as a failure.
  - **Follow-up cleanup (review, 2026-07-30):** `BaseViewModel` still carried the old per-ViewModel
    `Channel<UiEvent>` (`_uiEvents`/`uiEvents`/`sendEvent`) after the app-wide bus replaced it. It had
    **zero senders and zero collectors** — dead, but exactly the trap that caused the original defect, so
    it is removed. `UiEvent` itself stays; it is the bus payload (`ErrorManager`, `AppMessageHost`,
    `TopSnackbarViewHelper`).
- **Tests:** `domain/src/test/.../error/ErrorMapperTest.kt` (24 `ApiError` variants — completeness is
  compiler-enforced by an `else`-less `when`; asserts curated copy per variant, no code/class-name/raw
  text leak, `reasonFa` only reachable for `Unknown`, technical-detail redaction, fallback precedence,
  surface plumbing) and `ErrorTextSanitizerTest.kt` (per address family + mnemonic + JWT + clamp +
  "leaves ordinary Persian intact"). Run: `./gradlew :domain:testDebugUnitTest`.
- **Not built here** (Gradle unavailable) — inspection-verified only.
- **Verify on-device:** force-fail a send (blocking dialog + "جزئیات" carries a code, no address/hash),
  a wallet delete, a cloud-backup upload; go offline and confirm background refreshes stay quiet while a
  pull-to-refresh still reports; confirm the success snackbar is green and self-dismissing; confirm no
  double snackbar in the onboarding flow now that the per-screen hosts are gone.
- **Original spec below.**

- **Problem (user, item 8):** errors that matter to the user are inconsistently surfaced.
- **Current state:** the machinery exists — `core/manager/ErrorManager` (+ `ErrorMapper`,
  `ApiErrorMessageMapper`, `AppError`/`ApiError` in `domain/model/error/`), `BaseViewModel` observes it
  (`:31`, `showErrorSnackbar` `:141`, `showSnackbarMessage` `:156`), and `TopSnackbarViewHelper` renders the
  custom top snackbar (`:58`) plus an `ErrorDetailDialog` (`:178`). The gap is **coverage and quality**, not
  plumbing. Confirmed leak: `MultiWalletViewModel.deleteWallet` (`:153`) does
  `errorManager.showSnackbar(result.toString() ?: "خطا در حذف کیف پول")` — it shows the **raw
  `ResultResponse.Error` object's `toString()`**, and the `?:` is dead (`toString()` is non-null), so the
  Persian fallback never fires.
- **Do:** (1) audit every `ResultResponse.Error` / `catch` site in `viewmodel/**` for swallowed or
  raw-dumped errors; (2) route all of them through `ErrorMapper` → a **user-facing Persian message** with the
  technical detail behind the existing "جزئیات" dialog; (3) define severity policy — silent-log vs snackbar
  vs blocking dialog — and apply it uniformly (a failed background price refresh must not nag; a failed send
  must block); (4) never surface a raw exception/DTO string, and never leak addresses, keys, or signed
  payloads into a message (TASK-25 PII rule).
- **Files:** `core/manager/ErrorManager.kt`, `domain/model/error/*`, `app/core/BaseViewModel.kt`,
  `app/core/TopSnackbarViewHelper.kt`, `components/ErrorDetailDialog.kt`, all `viewmodel/**` error branches.
  **Modules:** core, domain, app. **Deps:** none.
- **Difficulty:** Med · **Est:** 1.5 · **Risk:** Low · **Priority:** P1 (user-facing quality + a real defect).
- **Acceptance:** no user-visible raw `toString()`/stack text remains; every error branch either logs
  deliberately or shows a mapped Persian message; the detail dialog carries the technical text.
  **Rollback:** per call site. **Regression:** error UX everywhere. **Testing:** JVM tests on `ErrorMapper`
  coverage per `ApiError` variant; force-fail each major flow on-device.

### TASK-58 — Wallet deletion UI is incomplete (no confirmation, no auth, raw error)
- **Problem (user, item 9):** deleting a wallet is not properly built out.
- **Current state — this is a data-loss hazard.** `MultiWalletScreen.kt:350` and `:498` call
  `viewModel.deleteWallet(id)` **directly from the tap handler**: no confirmation dialog, no
  "have you backed up your seed?" gate, no app-lock/biometric re-auth (`AuthPurpose.SENSITIVE_ACTION`
  exists and is used elsewhere), no undo. For a **non-custodial** wallet, deleting an unbacked-up wallet is
  **irreversible loss of funds** — a single mis-tap in the expanded card destroys it. Additional gaps:
  the error path dumps a raw object (see TASK-57), and nothing handles deleting the **active** wallet or the
  **last** wallet (what becomes active? does the app return to onboarding?).
- **Do:** (1) a confirmation flow: destructive-styled dialog naming the wallet, an explicit backup-state
  warning when `manualBackup`/`cloudBackup` is false (the flags exist —
  `updateWalletBackupStatusUseCase`), and a type-to-confirm or hold-to-confirm for unbacked wallets
  (`ConfirmSliderButton` already exists); (2) require re-auth via the existing sensitive-action gate;
  (3) define + implement active-wallet and last-wallet semantics — `DeleteWalletUseCase`
  (`domain/usecase/wallet/WalletManagementUseCases.kt:48`) already prunes the monitoring-enrollment id
  (TASK-32), verify it also clears keys, cached balances, and session state; (4) mapped error message
  (TASK-57); (5) success feedback + list refresh (already done via `loadWallets(forceRefresh = true)`).
- **Files:** `screens/wallet/MultiWalletScreen.kt` (+ `WalletManagementPanel.kt`),
  `viewmodel/MultiWalletViewModel.kt:145`, `domain/usecase/wallet/WalletManagementUseCases.kt`,
  `data/repository/WalletRepositoryImpl.kt:348`. **Modules:** app, domain, data. **Deps:** TASK-57 (errors).
- **Difficulty:** Med · **Est:** 1.5 · **Risk:** **High (irreversible data/funds loss)** · **Priority:** **P0**
  — of this batch, ship this one first.
- **Acceptance:** deletion always requires an explicit confirmation + re-auth; an unbacked-up wallet warns
  distinctly; deleting the active/last wallet leaves the app in a coherent state (no orphaned session,
  keys, cache, or monitoring enrollment). **Rollback:** revert UI (leaves today's unguarded path — not
  acceptable to ship). **Regression:** wallet list, active-wallet switch, session/JWT, key cache.
  **Testing:** JVM tests for the use case's cleanup; on-device delete of active / non-active / last wallet,
  and cancel-at-each-step.

### TASK-59 — Deposit notification sound is OFF at the channel level and survives reinstall — ✅ Implemented (needs on-device verify)
- **Done (2026-07-30):**
  - **Channel ids moved to a resource** — new `core/src/main/res/values/notification_channels.xml` holds
    `notification_channel_id_trade`, `notification_channel_id_deposit` (= **`deposit_notifications_v2`**),
    and a `legacy_deposit_channel_ids` array. Both consumers now read the resource, so the Kotlin side and
    the manifest can no longer drift — which is exactly how the second defect below arose.
  - **Channel id bumped + old one deleted** — `NotificationService.createNotificationChannels()` deletes
    every id in `legacy_deposit_channel_ids` (currently `deposit_notifications`) before creating the v2
    channel, so the system builds it fresh **with** the custom sound and the dead silent entry disappears
    from the user's notification settings. `enableVibration(true)` added; importance stays `HIGH`.
  - **Manifest defect fixed** — `default_notification_channel_id` pointed at **`trade_notifications`**
    (default sound), so any Firebase-rendered `notification`-payload push could never play the deposit
    sound. Now `@string/notification_channel_id_deposit`.
  - **Repair path exposed** — `isDepositChannelSilenced()` (channel blocked or `sound == null`) and
    `depositChannelSettingsIntent()` (deep link to the system channel screen) added to `NotificationService`.
    An app cannot re-sound an existing channel programmatically, so this is the only remedy; **the Settings
    warning row that consumes them is still to be built with TASK S** (§2.2-E).
  - The `private companion object` with the two hardcoded id constants was removed.
  - **Resource shrinking checked:** `:app` release has `isShrinkResources = true`, but
    `res/raw/deposit_alert.mp3` is referenced through a static `R.raw.deposit_alert` in
    `NotificationService` and `TransactionSoundPlayer`, so the shrinker keeps it. No keep rule needed —
    but re-verify if either reference ever becomes a name lookup.
- **Not built here** (Gradle unavailable) — inspection-verified.
- **Verify on-device:** (1) on the phone that currently shows the deposit channel as silent, install this
  build → settings show a **new** "واریز و تراکنش جدید" channel with sound on and no stale duplicate;
  (2) deposit with the app **closed** → custom sound plays; (3) foreground → plays exactly once (no
  doubling with `TransactionSoundPlayer`); (4) a **release** build still plays it (resource shrinking).
- **Original spec below.**

- **Problem (user, 2026-07-30):** the transaction/deposit notification arrives **without sound**. The user
  checked the system notification settings and found the sound **disabled**, and — the key symptom —
  **uninstalling and reinstalling the app does not fix it**. So this is not a per-notification bug; the
  channel itself is in a bad state and the fix must be infrastructural.
- **Root cause (Android notification-channel semantics):**
  1. **A channel's sound and importance are immutable after creation.** `NotificationService.createNotificationChannels()`
     (`core/notification/NotificationService.kt:29-59`) builds `deposit_notifications` with the custom
     `res/raw/deposit_alert` + `USAGE_NOTIFICATION` attributes, but `createNotificationChannel` on an
     **already-existing** channel id only updates the name/description — the sound and importance are
     ignored by the OS. Whatever state that channel was first created in (or was later changed to) wins
     forever.
  2. **Reinstall does not clear it.** Channel settings are backed up/restored by the system, so a
     delete-and-reinstall restores the same (soundless) channel — exactly the behaviour observed. Clearing
     app data is not a shippable instruction for users.
  3. The remedy is **already documented in the code but never executed**: the comment at
     `NotificationService.kt:38-40` says "If the sound file ever changes, bump `CHANNEL_ID_DEPOSIT`
     (e.g. `_v2`) so the system recreates the channel with the new sound." The id has never been bumped.
- **Second, independent defect (same symptom, different path):** `AndroidManifest.xml:52-55` sets
  `com.google.firebase.messaging.default_notification_channel_id` = **`trade_notifications`** — the
  *default-sound* channel. Any FCM message carrying a `notification` payload (i.e. rendered by the Firebase
  SDK, not by our `PushMessageHandler`) therefore lands on the wrong channel and can never play the deposit
  sound. Our own data-only path is correct (`PushMessageHandler.kt:114` → deposit channel, `silent = false`),
  and the **foreground** path is unaffected because `TransactionSoundPlayer` plays the sound explicitly
  (`NotificationSocketManager.kt:365,385`, `silent = true`) — consistent with "the sound is missing when the
  app is closed."
- **Do:**
  1. **Version the channel id** — `deposit_notifications_v2`, and `deleteNotificationChannel("deposit_notifications")`
     on the old id so it disappears from system settings instead of lingering as a dead soundless entry.
     Keep the version bump as the standing migration mechanism (document it next to the constant).
  2. Recheck the channel spec on creation: `IMPORTANCE_HIGH`, custom sound + `AudioAttributes`
     (`CONTENT_TYPE_SONIFICATION` / `USAGE_NOTIFICATION` — already correct), `enableVibration(true)`,
     and `setShowBadge` as desired.
  3. Point the manifest `default_notification_channel_id` at the new deposit channel (or confirm the server
     never sends `notification`-payload pushes — the realtime contract says thin **data-only** signals, in
     which case document that and leave it on a deliberate default).
  4. **Add a diagnostic + repair path in Settings** (TASK S §2.2-E): read the live channel state via
     `NotificationManager.getNotificationChannel(id)` and, when `importance == IMPORTANCE_NONE` or
     `sound == null`, show a warning row that deep-links to the system channel screen
     (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` with `EXTRA_CHANNEL_ID`). **The app cannot re-enable
     a user-disabled channel programmatically** — the deep link is the only correct remedy, and without it
     an affected user has no way back.
  5. Verify `res/raw/deposit_alert` is not stripped by R8/resource shrinking in release
     (`app/proguard-rules.pro` / `shrinkResources`) — a missing raw resource degrades to silent.
- **Files:** `core/notification/NotificationService.kt`, `app/src/main/AndroidManifest.xml`,
  `data/socket/PushMessageHandler.kt` (verify), `core/notification/TransactionSoundPlayer.kt` (verify),
  Settings screen (TASK S). **Modules:** core, app, data. **Deps:** TASK-43 (custom sound both paths),
  TASK-34 (FCM wiring), TASK S (Settings home for the repair row).
- **Difficulty:** Low · **Est:** 0.5 (+0.25 for the Settings diagnostic row) · **Risk:** Low — but note the
  channel bump is a **one-way migration**: users who deliberately silenced the old channel will get a fresh
  channel at `IMPORTANCE_HIGH`. That is the intended repair here; don't bump the id casually in future.
  · **Priority:** **P1** — ship the id bump with the next release; existing installs cannot self-heal
  without it.
- **Acceptance:** on a device that currently shows the deposit channel as soundless, installing the new
  build produces a **new** channel with the custom sound enabled and the old entry gone; an incoming
  deposit with the app **closed** plays `deposit_alert`; foreground still plays exactly once (no doubling);
  a user who manually disables the channel sees the Settings warning row and can deep-link to fix it.
- **Rollback:** revert to the old channel id (restores today's behaviour — the old channel state is still
  in the system). **Regression:** all notification delivery, foreground/background sound doubling, badge.
- **Testing:** on-device matrix — app closed / background / foreground × deposit, on a device with the
  broken channel and on a clean install; verify `getNotificationChannel(...)` reports a non-null `sound`
  and `IMPORTANCE_HIGH`; verify the raw resource survives a **release** build.

### TASK-59a — Foreground deposit is silent & the alert text drops amount/token — ✅ Implemented (needs on-device verify)
- **Problem (user, 2026-07-30, after TASK-59 shipped):** with the app **open**, a deposit produced no
  sound and no vibration — only a mute status-bar entry — and the text didn't show the amount or token
  symbol even though "the socket data is complete."
- **Root cause 1 — the foreground path silenced itself by construction.**
  `NotificationSocketManager.handleNotification` posted `showTransactionNotification(..., silent = true)`,
  and `NotificationCompat.setSilent(true)` suppresses **vibration and heads-up as well as sound**. So an
  in-app deposit could *never* buzz, by design. The only intended alert was `TransactionSoundPlayer`, a
  hand-rolled `MediaPlayer` — when that produced nothing, the result was total silence. The comment
  justified this as "OEMs suppress channel sound in the foreground", which isn't how channels behave.
- **Root cause 2 — the display hint was read too rigidly.**
  - `parseTxDescriptor` (both WS and FCM) read a fixed set of **root-level** keys only. A hint nested
    under a container, or using a synonym, parsed as all-null.
  - `TransactionNotificationText.forTx` then returned **null** unless `direction` was exactly
    `in`/`out`/`self`, and dropped the amount entirely whenever `tokenDecimal` was absent (which is
    always the case for a **native** transfer). Either miss collapsed the whole alert to the generic
    "یک تراکنش جدید روی آدرس شما ثبت شد."
- **Root cause 3 — the notification was below wallet standard.** `NotificationService.show()` set **no
  `contentIntent`** at all, so tapping a notification did nothing; no `BigTextStyle` (long bodies
  truncated), no network context, no lock-screen privacy handling.
- **Fix:**
  - Foreground now alerts **through the channel** exactly like FCM (`silent = false`) → custom sound +
    vibration + heads-up. `TransactionSoundPlayer` is demoted to a **fallback for when POST_NOTIFICATIONS
    is denied** (sound needs no permission), so the two can't double up.
  - New shared `TxDescriptorParser` (data/socket) searches the payload **root and nested containers**
    (`display`/`descriptor`/`tx`/`transaction`/`meta`) and accepts key synonyms, for both JSON (WS) and
    the flat FCM map (incl. dotted `display.direction` keys). Deliberately permissive on *reading*: a
    wrong guess costs nothing, a right one restores the detail. `type` is excluded from the direction
    aliases — at the payload root that's the event name and would shadow a real nested direction.
  - `TransactionNotificationText` moved out of the socket god-file into its own file and now degrades
    **one field at a time**: unknown direction still shows the amount ("تراکنش 1.5 USDT ثبت شد.");
    a native transfer falls back to the **network's** symbol and decimals; title carries the symbol
    ("دریافت USDT"); the network's Persian name goes in the notification `subText`.
  - `NotificationService`: added `contentIntent` (launcher resolved via `PackageManager` so `:core`
    still doesn't depend on `:app`), `BigTextStyle`, `CATEGORY_EVENT`, `subText`, and
    `VISIBILITY_PRIVATE` + a redacted `publicVersion` so **balance amounts don't appear on the lock
    screen** — standard for a wallet.
  - A `Timber.w` now logs the raw descriptor whenever a `tx.new` still falls back to the generic text,
    so the next occurrence is diagnosable from logcat instead of guesswork.
  - **Tests:** new `TxDescriptorParserTest` (root/nested/synonyms/numeric-amount/type-shadowing/FCM map)
    and a rewritten `TransactionNotificationTextTest` (native fallback, direction synonyms, unknown
    direction still reporting the amount).
- **⚠️ Open — needs the real frame.** The exact server field names for the hint could not be confirmed
  here: the contract lives in the server repo (`realtime-event-contract.md` §2) and the captured logcat
  (`megawallet-logcat-20260729.txt`) contains only `pong` + `connection.ready`, no `tx.new`. The parser
  is tolerant enough to cover the plausible shapes, but **if the alert is still generic, grab the frame**
  — `NotificationSocketManager` logs every one as `[NotificationSocket] ⬇ {…}` — and add the real key
  names to `TxDescriptorParser`.
- **Not built here** (Gradle unavailable) — inspection-verified.
- **Verify on-device:** deposit with the app **open** → custom sound + vibration + heads-up, text shows
  amount and symbol; app **closed** → same; tapping opens the app; the lock screen shows the redacted
  line; with notifications **denied** → still audible in-app, exactly once.

**Suggested order for this batch:** TASK-58 (P0, data loss) → **TASK-59** (P1, needs to ride the next
release to self-heal) → TASK-51 + TASK-52 (cheap, user-visible) →
TASK-57 (unblocks clean errors everywhere) → TASK-54 (price truth) → TASK-53 (config wiring, high risk) →
~~TASK-56 (needs TASK S pref + TASK-54)~~ ✅ done, with TASK S §2.2-D's fiat pref folded in →
TASK-55 (needs server contract) → TASK-50 (needs Swap/TASK-2).

---

## Coverage check (every TD mapped)
TD-01→13 · TD-02→12 · TD-03→13 · TD-04→14 · TD-05→18 · TD-06→(tests folded per task) · TD-07→15 ·
TD-08→01/04 · TD-09→21 · TD-10→13 · TD-11→11 · TD-12→08 · TD-13→09 · TD-14→14 · TD-15→17 · TD-16→17 ·
TD-17→17 · TD-18→13 · TD-19→05 · TD-20/21→10 · TD-22→06/19 · TD-23→06 · TD-24/25/26/27→20 · TD-28→01 ·
TD-29/30→11 · TD-31→07 · TD-32/33→21 · TD-34→01 · TD-35→19 · TD-36→02 · TD-37→04 · TD-38→03 · TD-39→21 ·
TD-40→21 · TD-41→15 · TD-42/46→22 · TD-43→23 · TD-44→21 · TD-45→17. Code-review correctness→16; cleanup→24.
