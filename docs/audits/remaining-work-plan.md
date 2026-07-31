# MegaWallet — Remaining Work Plan (self-contained handoff)

> **Purpose.** This document lets any fresh session (or a different developer) execute the
> outstanding production-hardening work **exactly as the author would**, with no prior chat
> context. Every task names real files, the existing code to reuse, the wiring points, and how to
> verify. Read the **Hard constraints** first — they override defaults.
>
> Last updated: 2026-07-23. Owner tasks tracked here: the 7-item punch list + a new **Settings**
> section + a deferred splash animation.

---

## 0. Hard constraints (read before touching anything)

1. **The build cannot be run in the planning environment.** Work in **batches**; after each batch,
   stop and have the human run the build. Never claim "compiles" without a real build. Build cmds:
   ```bash
   ./gradlew assembleDebug
   ./gradlew :app:compileDebugKotlin :data:compileDebugKotlin
   ./gradlew :data:testDebugUnitTest
   ```
2. **graphify hook is mandatory.** Before grepping/reading raw source, run
   `graphify query "<question>"` (or `path`/`explain`). After code changes, run `graphify update .`.
   The graph lives in `graphify-out/`; broad navigation via `graphify-out/wiki/index.md`.
3. **Copyright — Family (family.co) assets are OFF-LIMITS.** Do **not** reproduce, trace, or reuse
   Family's proprietary SVG/illustration assets, even "with minor changes." Original art in a
   similar flat/friendly style (own colors/placement/motion) is fine. This applies to the splash
   animation task below.
4. **Architecture is clean, strict deps:** `app → common_ui/core/data/domain`, `data → core/domain`,
   `core → domain`, `domain → (pure Kotlin)`. Repo **interfaces live in `domain/interfaceRepository/`**
   (`I*`), implementations in `data`/`core`. Never add Android/SDK deps to `domain`.
5. **Navigation is state-based (no NavController/NavHost).** Screens are composed by observing
   ViewModel state in `MainScreen` / activity-level state. Add screens by wiring state, not a graph.
   ViewModels extend `BaseViewModel(ErrorManager)` and live in `app/.../viewmodel/`.
6. **DI is Hilt.** Bind impls in the owning module's Hilt module (`data/.../di/DataModule.kt`,
   `NetworkModule.kt`, `SocketModule.kt`; `core/.../di/`). `@Inject constructor` classes are auto-provided.
7. **Commit only when asked.** Commit messages end with:
   `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Don't commit unrelated WIP.
8. **Data-driven chains/tokens:** add chains/tokens by editing `core/src/main/assets/networks.json` /
   `assets.json`, not `when(networkType)` branches.
9. Many code comments are Persian — expected. UI strings are Persian and currently **hardcoded**
   (no i18n resource layer yet — relevant to the Settings "language" item).

---

## 1. Status of the 7-item punch list

| # | Item | Status |
|---|------|--------|
| 1 | Wallet create/recovery path doesn't reset for subsequent uses | **TODO** — see §3 |
| 2 | Design + wire **Swap** section | **TODO** — backend exists, no UI — see §4 |
| 3 | Balance counter (odometer, Family-style, was laggy) | **DONE** — `AnimatedCounter.kt` rewritten to draw-phase per-digit `RollingCounter` |
| 4 | SendAmountPhase counter bug | **DONE** — migrated to shared `RollingCounter`; `VALUES.kt` deleted |
| 5 | **Settings** section (didn't exist) | **TODO** — full spec in §2 |
| 6 | QR/barcode address scan → Send | **DONE (code)** — `QrScannerScreen.kt` + CameraX/ML Kit; **needs device test** |
| 7 | NFT display (if feasible) | **TODO / gated** — see §5 |
| — | Family-style splash/onboarding animation | **DEFERRED** — see §6 (copyright constraint applies) |

Also outstanding verification the human must do: run the **Baseline Profile / Macrobenchmark** on a
rooted managed device — `./gradlew :baselineprofile:pixel6Api34BenchmarkAndroidTest` (the earlier
Mi 9T API-30 non-rooted run failed for that reason, not a code bug).

**New batch (2026-07-30) — 10 owner-requested items are specced as TASK-50…59 in
[`docs/tasks/master-task-register.md`](../tasks/master-task-register.md) § "Sprint 7".** Highlights:
**TASK-58 (wallet deletion has no confirmation/re-auth — P0 data-loss hazard)**, TASK-51/52 (explorer link
+ copy address in history — both cheap, the explorer URL builder already exists but has zero UI call sites),
TASK-57 (error surfacing), TASK-54 (USDT price inconsistent across three independent price paths),
**TASK-59** (deposit notification channel is soundless and survives reinstall — needs a channel-id bump in
the next release, since existing installs cannot self-heal), and
**TASK-53**, whose investigation showed the signed server config bundle is fetched/verified but **never
applied to the registries** — correcting TASK-38's earlier "already implemented" status.

---

## 2. TASK S — Settings section (new)

**Why.** Several capabilities are already persisted or fully built but have **no UI** (esp. the
DIRECT/PROXY connection mode). Settings gives them a home and consolidates security/backup.

**Entry point.** `MainActivityCompose.onCreate` → `MainScreen(onMoreOptionsClick = { … })`. Today
`onMoreOptionsClick` opens `SecuritySettingsSheet` directly (see
`app/.../ui/compose/MainActivityCompose.kt:189`). Repoint it to open the new **Settings** surface;
Security becomes one row inside Settings.

**Pattern to follow.** State-based, like the QR scanner overlay already added to `MainScreen`
(`var showScanner by remember`). Add `SettingsViewModel : BaseViewModel`, a `SettingsScreen`, and an
immutable `SettingsUiState`. Reuse existing sheets rather than rebuilding.

### 2.1 Persistence — extend the preferences layer
Add getters/setters to `domain/.../interfaceRepository/IUserPreferencesRepository.kt` and implement in
`data/.../repository/UserPreferencesRepositoryImpl.kt` (DataStore-backed; mirror the existing
`getConnectionMode/setConnectionMode` pattern). New prefs:
- `themeMode: ThemeMode` (SYSTEM / LIGHT / DARK) — new enum in `domain/model/` (e.g. `ThemeMode.kt`).
- `fiatCurrency: String` (e.g. "USD" / "IRR") — default "USD".
- `dateCalendar: DateCalendar` (JALALI / GREGORIAN) — new enum.
- `screenshotBlockEnabled: Boolean` — default false.
- `notificationsEnabled: Boolean` — default true.
- `requireAuthOnSensitive: Boolean` — default true (maps to `AuthPurpose.SENSITIVE_ACTION`).

### 2.2 Sections & per-item wiring (grounded in existing code)

**A. شبکه و اتصال (Network) — highest value, lowest risk**
- **Connection mode DIRECT/PROXY.** Pref already exists (`get/setConnectionMode`) and drives
  `ChainDataSourceFactory` via `IBlockchainConnectionModeProvider`
  (`data/.../datasource/DefaultBlockchainConnectionModeProvider.kt`). **Only a UI toggle is missing.**
  A segmented control that calls `setConnectionMode`. Note the mode is read via an in-memory cache —
  confirm a mode switch invalidates/refreshes reads (check `DefaultBlockchainConnectionModeProvider`).

**B. امنیت (Security) — mostly built; link, don't rebuild**
- Reuse `SecuritySettingsSheet` (`app/.../screens/security/SecuritySettingsSheet.kt`) — app lock,
  change passcode, biometric, auto-lock timeout. Driven by `AppLockViewModel`.
- **Screenshot block toggle** → reuse `components/SecureScreen.kt` (FLAG_SECURE). Apply globally at
  activity level based on `screenshotBlockEnabled`.
- **Require auth on sensitive actions** → `AuthPurpose.SENSITIVE_ACTION` already exists in the lock flow.

**C. کیف‌پول و پشتیبان‌گیری (Wallets & Backup) — link existing**
- Manage wallets → `screens/wallet/MultiWalletScreen.kt` (+ `WalletManagementPanel.kt`).
- Google Drive backup + reveal seed → `data/.../repository/BackupRepositoryImpl.kt`,
  `data/.../auth/GoogleAuthManager.kt`, `screens/wallet/components/SecretRevealOverlay.kt`,
  `screens/wallet/ManualBackupVerifier.kt`, cloud screens under `screens/addexistingwallet/`.

**D. نمایش (Appearance) — needs small plumbing**
- **Theme (System/Light/Dark + Material You).** `common_ui/.../theme/Theme.kt` already has
  `DarkColorScheme`, `LightColorScheme`, and a `dynamicColor` param; currently `MegaWalletTheme`
  defaults `darkTheme = isSystemInDarkTheme()`. Add a `themeMode` param and resolve dark/light from
  the pref. **Two call sites must both read the pref:** `MainActivityCompose.kt` and
  `WelcomeActivityCompose.kt` (the only two roots; other `MegaWalletTheme` refs are `@Preview`s).
- **Fiat currency (USD/تومان).** ✅ **DONE (2026-07-31) — built with TASK-56**, since the toggle is
  unusable without the pref and building the pref twice was the real risk. See TASK-56 in the task
  register for the full write-up. What exists now, for the Settings screen to reuse rather than rebuild:
  - `domain/model/FiatCurrency.kt` (USD / TOMAN, default USD) — replaced `HomeUiState.DisplayCurrency`.
  - `IUserPreferencesRepository.get/setFiatCurrency` for storage, and `IFiatCurrencyProvider`
    (impl `data/repository/FiatCurrencyProvider`, `@Singleton`) as the **observable** value every screen
    collects. **Settings must bind its radio/segmented control to the provider**, not to the suspend
    getter — a snapshot is exactly how the Toman rate went stale before TASK-54.
  - `core/utils/FiatConversion` is the one and only place that knows تومان vs rial. The Wallex value is
    **already تومان** (`uSDTTMN`), so there is no `/10` in the production path; the unit is resolved from
    `CurrencyRate.quoteCurrency`, which the producers now correctly label `"TMN"`.
  - `BalanceFormatter.formatFiatValue` + `AssetItem.withFiatBalances` are the only fiat formatters.
  - Still USD-only (not part of TASK-56's four surfaces): `CachedWalletBalanceReaderImpl` and
    `CloudWalletBalanceCalculatorImpl`.
- **Date calendar (Jalali/Gregorian).** `core/.../utils/JalaliCalendar.kt` +
  `core/.../utils/DateTimeUtils.kt` (`getDateHeader`) exist; make date formatting read the pref.
  Formatter already centralizes date display (`TransactionDisplayFormatter.historyDateHeader/…`).
- **Language.** Persian strings are hardcoded across Compose. A real language switch requires an i18n
  pass (extract to string resources). **Large — defer** or scope as its own task; do NOT half-do it.

**E. اعلان‌ها (Notifications)**
- Master toggle wired to FCM register/unregister. Reuse `app/.../notification/FcmTokenRegistrar.kt`
  and `getRegisteredFcmToken/setRegisteredFcmToken` in prefs; `core/.../notification/NotificationService.kt`
  already no-ops without permission. On disable → unregister token; on enable → re-register.

**F. درباره و توسعه‌دهنده (About / Developer)**
- App version (BuildConfig), terms (`screens/createwallet/TermsPart.kt`), links.
- Hidden **developer mode**: show current connection mode + relayer endpoints (the `RELAYER_*`
  `BuildConfig` fields in `:data`) for debugging. Read-only.

### 2.3 Suggested build order (batches, stop for build after each)
1. Skeleton: `SettingsViewModel` + `SettingsUiState` + `SettingsScreen` shell; repoint
   `onMoreOptionsClick`; add rows that **link** to existing Security + Wallets. (No new prefs yet.)
2. Connection mode DIRECT/PROXY toggle on the existing pref.
3. Theme pref + enum + plumb both roots + `MegaWalletTheme` param.
4. Date calendar pref + `JalaliCalendar`/`DateTimeUtils` wiring. (**Fiat currency: already done** —
   TASK-56 shipped the pref, the provider and the formatters; Settings only needs a control bound to
   `IFiatCurrencyProvider`.)
5. Screenshot block + notifications toggles.
6. About/Developer section.

### 2.4 Verification
- `./gradlew :app:compileDebugKotlin` after each batch.
- Manual: toggle connection mode → confirm reads still work in both modes (DIRECT and PROXY must stay
  behaviorally equivalent). Toggle theme → survives process death. Switch currency → all fiat displays
  update. Add a unit test for currency/date formatting in `:data` (JVM) mirroring
  `TransactionDisplayFormatterTest`.

---

## 3. TASK 1 — Wallet create/recovery does not reset for subsequent uses

**Symptom.** After creating/importing a wallet once, starting the create/recovery flow again shows
stale state instead of a clean slate.

**Where to look (investigate first, then fix):**
- `app/.../viewmodel/CreateWalletViewModel.kt`, `WalletImportViewModel.kt`, `WelcomeViewModel.kt`
  (note `setImportData/clearImportData/setModalActive` already exist in `WelcomeViewModel`).
- Onboarding entry: `WelcomeActivityCompose.kt` (`WelcomeNavGraph`, "onboarding" route →
  `OnboardingScreen` now at `screens/onboarding/`), and the create flow screens under
  `screens/createwallet/` (steps: Name, Color, SeedPhrase, Terms) + `CreateWalletStep` model.
- Likely cause: a ViewModel retained across the flow keeps step/seed/name/color state and isn't
  reset on re-entry. **Fix:** add an explicit `reset()` that clears step, generated seed, name,
  color, terms, and error, and call it on flow entry (or scope the VM to the flow so it's recreated).
- Watch for the seed being regenerated vs. reused — a non-custodial correctness point: never show a
  previously generated seed on a fresh create.

**Verify:** create a wallet, back out, start create again → all fields blank, a NEW seed generated.

---

## 4. TASK 2 — Swap section (backend exists, no UI)

**What exists (reuse):**
- `domain/.../interfaceRepository/ISwapRepository.kt`: `getProviders()`, `getQuote(SwapQuoteRequest)`,
  `prepare(...)` (and more — read the file).
- `domain/model/SwapModels.kt` (SwapProviders, SwapQuote, request/prepare models).
- `data/.../repository/swap/SwapRepositoryImpl.kt`, `data/.../service/SwapApiService.kt`,
  `data/.../dto/SwapApiDto.kt`. DI likely already bound in `DataModule`/`NetworkModule` — confirm.

**What's missing:** the entire UI + a `SwapViewModel`. Model it on the Send flow, which is the closest
existing pattern (amount entry with the shared `RollingCounter`, token pickers, confirm slider):
- `SwapViewModel : BaseViewModel` — from/to asset selection, amount, debounced `getQuote`, provider
  choice, `prepare` + sign locally + broadcast (signing must stay on-device; only signed payloads
  leave — same rule as sends).
- `SwapScreen` — reuse `screens/send/SendTokenList.kt` (token picker), `ChooseBalanceBottomSheet.kt`,
  `ConfirmSliderButton.kt`, `SendAmountPhase.kt`'s `RollingCounter` usage, and gasless banner
  components if swap supports sponsored routes.
- **Entry:** add a state flag in `MainScreen` (like `showScanner`) and a nav affordance (the
  `MorphingFabMenu` in `screens/main/` is the natural launch point).

**Gotchas:** honor DIRECT vs PROXY equivalence for any chain reads; quote TTL/debounce like the
gasless preview cache pattern in `SendViewModel` (30s TTL keyed on inputs). Confirm which chains swap
supports via `CapabilityManager` feature flags before showing the entry.

**Verify:** get a quote on a supported pair; ensure prepare→sign→broadcast path mirrors Send; add
JVM tests in `:data` for the quote/prepare mapping.

---

## 5. TASK 7 — NFT display (gated on backend support)

**Gate first.** There is currently **no NFT model/service/repository** in the codebase (no NFT DTO,
no interface). NFT display requires either a backend endpoint (via the Mobile Blockchain Proxy
`/api/mobile/v1`) or direct indexer calls. **Do not build UI until the data source exists.**

**If greenlit, the shape:**
- `domain`: `Nft` model + `INftRepository` (interfaceRepository) with `getNftsForWallet(address, chain)`.
- `data`: DTO + `NftApiService` + `NftRepositoryImpl` + DI; route through `IChainDataSource`/proxy for
  consistency with the DIRECT/PROXY toggle.
- `app`: an NFT grid tab/screen (Coil is already configured in `MegaWalletApplication` for images).

**Recommendation:** confirm backend/indexer availability with the server contract
(see the server-integration doc referenced in project memory) before committing effort.

---

## 6. DEFERRED — Family-style splash/onboarding animation

- Files already on disk (not wired): `app/.../screens/welcome/IntroDoodles.kt`,
  `WelcomeIntroScreen.kt`. Intended as a richer replacement for `OnboardingScreen`.
- **Copyright constraint (hard):** original crypto-themed elements only (shield, rocket, a popular
  crypto glyph, barcode, wallet, gear, lock, small stars/dots), in a Family-*inspired* flat style —
  NOT Family's actual SVG assets, even modified. Motion concept: tumbling-coin fall → fountain-bloom
  burst with `easeOutBack` overshoot → settle + text/buttons fade.
- Status: paused by user ("later"). Re-confirm scope/effort before resuming.

---

## 7. Structural note (already done this session)

Package/folder hygiene completed and graph updated:
- `domain`: `IUserPreferencesRepository` → `interfaceRepository/`; `model/gassless/FeeState.kt`
  (typo dir) → `model/FeeState.kt`.
- `app`: `viewmodel/news/*` → `viewmodel/*`; `viewmodel/assetDetail/` → `assetdetail/`;
  `screens/SplashScreen.kt` → `screens/splash/`; `screens/OnboardingScreen.kt` → `screens/onboarding/`.
- `data`: `GoogleAuthManager.kt` → `data/auth/`; `TransactionDisplayFormatter.kt` (+
  `WalletAddressReference`) moved app → `data/formatter/`.

No behavior changed — moves + `package`/import updates only. If a build surfaces a missed import,
grep the old FQN (excluding `.claude/worktrees`) and fix.
