# Performance / Animation Audit (Phase 3) — candidates to drive lag → ~0

**Goal (user, 2026-07-21):** enumerate *every* candidate that reduces app lag / jank — animations,
recomposition, list scrolling, cold start — so we can work them down toward zero.
**Method:** read-only static scan of `app/.../ui` + `common_ui` + build config; reuses the Phase-2
Compose audit (`compose-audit.md`) and cross-references the task register.
**Scope note:** Gradle can't run here, so everything is inspection-verified; the two items marked
**📈 needs device** are where real numbers (Macrobenchmark / Layout Inspector / compiler metrics)
should drive the work.

Priority: 🔴 highest lag lever · 🟠 high · 🟡 medium · 🟢 low. Each item lists concrete sites + the fix.

---

## 0. Do this FIRST — it tells you where the lag actually is

### PERF-01 🔴 Turn on the Compose compiler metrics/reports (diagnostic, 1 flag) — 📈 needs device/build
- **Why:** the single fastest way to find *all* non-skippable / unstable composables instead of
  guessing. Right now there is **no** `metricsDestination`/`reportsDestination` configured.
- **Do:** in the app module's Compose-compiler config, emit
  `-P plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=…` +
  `reportsDestination=…` (or the `composeCompiler { }` DSL). Build once, read
  `*-composables.txt` / `*-classes.txt`: it flags every `restartable skippable? unstable param`
  and every `unstable class`. Use it to prioritize PERF-02.
- **Risk:** none (build-time diagnostic only, debug variant). **Est:** 0.25.

---

## 1. Recomposition — the biggest structural lever

### PERF-02 🔴 Compose stability pass: make UI-state models skippable
- **Evidence:** across `domain` + `app` there are **only 2** `@Immutable/@Stable` annotations
  (`AnimatedFlipCard.kt`, `WordInputField.kt`), **no** `kotlinx.collections.immutable`, and no
  strong-skipping/stability config. So every composable that takes a domain model
  (`AssetItem`, `TransactionRecord`, `HistoryNetworkOption`, `HomeUiState`, …) or a bare `List<T>`
  is treated as receiving **unstable** params → Compose can't skip it → it recomposes whenever its
  parent does, even when its data is unchanged. This is almost certainly the dominant source of
  "general" lag on scroll/tab-switch.
- **Do (pick per compiler version, lowest-risk first):**
  1. Add `@Immutable` to the UI-facing data classes that are truly immutable
     (`domain/model/HomeUiState.kt` `AssetItem`, `TransactionRecord` subclasses,
     `HistoryNetworkOption`, `TransactionFeeDetails`, fee/tier models). `@Immutable` is a promise —
     only annotate classes whose public props never change after construction (these are `val`
     data classes, so they qualify).
  2. Replace `List<…>` **composable parameters** and `…UiState` list fields with
     `kotlinx.collections.immutable.ImmutableList` / `PersistentList` (add the dep to
     `libs.versions.toml`). A `List` is unstable to Compose; `ImmutableList` is stable.
  3. Alternatively/additionally, enable the Compose compiler **strong-skipping** mode and a
     **stability-configuration file** listing external types to treat as stable — one build flag,
     covers many classes without touching each. (Verify the compiler version supports it.)
- **Payoff:** turns non-skippable composables skippable → recomposition counts drop across lists,
  tabs, and the wallet/asset screens. Validate with PERF-01 before/after.
- **Risk:** Low–Med (annotate only genuinely-immutable types; wrong `@Immutable` on a mutating type
  causes stale UI). **Est:** 2–3. **Priority:** 🔴.

### PERF-03 🟠 Lazy-list keys + `contentType` on the real data lists
- **Evidence:** ~11 `items(...)` sites lack an explicit `key`; the ones that matter (dynamic,
  reorderable, or long) are:
  `WalletScreen.kt:219` (asset list), `TransactionHistoryScreen.kt:207` (verify — audit says history
  already keys on `row.stableKey`; confirm), `ReceiveScreen.kt:115` (`allItems`),
  `WalletComponents.kt:349`, `SendTokenList` asset list. Fixed-length pickers
  (`ColorSelectionPart`, `SecretPhraseGrid`, `WalletManagementPanel` colors, shimmer placeholders)
  are low-value.
- **Do:** add `key = { it.stableId }` (stable, unique) and `contentType = { … }` to the dynamic
  lists so Compose reuses/skips items on data change and scroll instead of re-emitting them.
- **Risk:** Low (wrong/duplicate keys cause item state bugs — use a genuinely unique id).
  **Est:** 0.5. **Priority:** 🟠.

### PERF-04 🟡 Finish "logic out of composition" for the send stack (TASK-14 remainder)
- **Evidence:** TASK-14 extracted the **history** formatting into `TransactionDisplayFormatter`
  (commit 1ede891) but the **send** stack still calls `viewModel.getBaseCryptoAmount(...)`,
  `formatCryptoFromRaw/UsdFromRaw/IrrFromRaw` in composition (`SendConfirmScreen.kt:91,227,240-242`,
  `SendScreen.kt:74,250`). Some are already in `remember(...)`; the unremembered ones recompute each
  recomposition.
- **Do:** wrap remaining in-composition `viewModel.*` formatting in `remember(key)` at minimum, or
  precompute into the send UI-state (mirrors the history extraction). **Risk:** Low. **Est:** 1.

---

## 2. Animations — draw-phase deferral (continues TASK-49)

Fix pattern (from TASK-49): move the per-frame animated read out of the composable body into the
**draw phase** (`Modifier.graphicsLayer { … }` / `drawBehind { … }`) so the node **redraws** each
frame but never **recomposes**. Already done: shimmers (becaa93), `ConfirmSliderButton` (4b5633e).

### PERF-05 🟠 `SendConfirmScreen` entrance fade recomposes 5 subtrees per frame
- **Evidence:** `contentAlpha by animateFloatAsState(...)` (`SendConfirmScreen.kt:161`) is read via
  `Modifier.alpha(contentAlpha)` at **5** sites (`339, 371, 410, 454, 502`). Since this is a
  ~1977-line god screen, each animation frame of the entrance fade recomposes those big subtrees.
- **Do:** replace `.alpha(contentAlpha)` → `.graphicsLayer { alpha = contentAlpha }` (identical
  visual; the read moves to the draw phase). **Risk:** Low. **Est:** 0.25. **Priority:** 🟠
  (god screen amplifies the win).

### PERF-06 🟡 `MainScreen` morph container alpha
- **Evidence:** `containerAlpha by animateFloatAsState` (`MainScreen.kt:467`) read via
  `.alpha(containerAlpha)` (`:489`) during the morphing-bounds animation.
- **Do:** `.graphicsLayer { alpha = containerAlpha }`. **Risk:** Low. **Est:** 0.1.

### PERF-07 🟡 `SendConfirmFeeTabs` — `.alpha(alphaAnim)` (`:149`) + `.rotate(rotation)` (`:255`)
- **Blocked:** this file currently holds **uncommitted WIP** — do it when that lands. Convert to
  `graphicsLayer { alpha = … }` / `graphicsLayer { rotationZ = … }`.

### PERF-08 🟢 `MutableInteractionSource()` without `remember` (CU-10)
- **Evidence:** `AnimatedBottomSheetCard.kt:74` allocates a new `MutableInteractionSource()` every
  recomposition (loses press state + allocates). **Blocked:** same file holds uncommitted font WIP.
- **Do (when unblocked):** `val interaction = remember { MutableInteractionSource() }`. **Est:** 0.05.

### PERF-09 🟢 Verify (probably already fine) — leave alone unless metrics say otherwise
- `FloatingShapesBackground` / `CryptoCoinOrbit` / `DiamondAnimation` / `WalletAnimation`:
  their `canvas.scale/rotate` calls are **inside `Canvas`/`DrawScope`** — already draw-phase, **not**
  a recomposition problem. `GeneratingAnimation` (onboarding) is one-shot and off the hot path.
  No action unless PERF-01 metrics flag them.

---

## 3. Cold start / first frame — 📈 needs device

### PERF-10 🔴 Baseline Profile + Macrobenchmark (this is TASK-28) — 📈 needs device
- **Why:** a Baseline Profile is usually the **single biggest** real-world jank/startup win (AOT-
  compiles the startup + scroll paths). TASK-28 (Sprint 0) is 🆕 Planned for exactly this.
- **Do:** add the `:macrobenchmark` module + baseline-profile generator; measure `startup` +
  `scroll wallet/history` before/after. **Risk:** Low. **Est:** 1.25.

### PERF-11 🟠 Cold-start work audit (Application / first Activity) — 📈 needs device
- **Do:** profile `MegaWalletApplication.onCreate` (dynamic-config warm-up, Coil `ImageLoader`
  build, Crashlytics, DI) and `MainActivityCompose.onCreate` for main-thread I/O or eager network;
  move non-critical work off the critical path (lazy/`Dispatchers.Default`, `androidx.startup`).
  StrictMode (TASK-26, done) already surfaces main-thread disk/net in debug — read its logs on a
  cold launch. **Note:** `MegaWalletApplication`/`MainActivityCompose` are currently **do-not-touch
  WIP** — audit only, hand findings back. **Risk:** Med (startup ordering). **Est:** 1.

---

## Sequencing (fastest lag reduction first)
1. **PERF-01** (compiler metrics on) → gives the real target list.
2. **PERF-02** (stability/skippability) → biggest structural recomposition drop.
3. **PERF-10** (Baseline Profile) → biggest startup/scroll win *(device)*.
4. **PERF-05/06** (graphicsLayer alpha) + **PERF-03** (list keys) → cheap, immediate.
5. **PERF-04** (send formatting), **PERF-11** (cold-start audit).
6. Unblock **PERF-07/08** once the WIP files land.

## Already landed (don't redo)
- Fonts hoisted (TASK-08), lifecycle-aware collection (TASK-09), parallel balances + O(n) history
  merge (TASK-10), history formatting → use case (TASK-14), shimmer + ConfirmSliderButton draw-phase
  (TASK-49 / becaa93 / 4b5633e), god-screen splits (TASK-13).
