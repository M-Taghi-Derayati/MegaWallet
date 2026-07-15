# Compose / UI Audit (Phase 2)

**Scope:** Jetpack Compose usage — recomposition & allocation hotspots, state hoisting &
collection, theming/dark-mode, accessibility, RTL/Persian text, list correctness, preview
coverage, and UI-layer structure.
**Method:** read-only. Whole-tree pattern quantification across
`app/.../ui` + `common_ui` (223 `@Composable` functions), plus spot-reads of the worst files.
No production code modified. Reuses Phase-1 facts (god files, navigation) rather than re-deriving.

---

## Executive Summary

Compose is used **exclusively and competently at the mechanism level** — `StateFlow` +
`collectAsState`, `LazyColumn` with stable keys, `derivedStateOf` (13 sites), `LaunchedEffect`
(74 sites), and `remember` (169 sites) all appear in idiomatic places. The problems are
**systemic and quantifiable**, not one-off:

- **185 inline `FontFamily(Font(...))` allocations** across the UI — a new `FontFamily` is built
  on **every recomposition of nearly every `Text`** — because the defined `Typography` still uses
  `FontFamily.Default` and the custom Persian fonts were never wired into the theme. This is the
  single highest-impact UI issue.
- **Zero lifecycle-aware collection**: 45 `collectAsState()` calls, **0** `collectAsStateWithLifecycle`
  — flows keep the UI collecting/recomposing while the app is backgrounded.
- **135 hardcoded `Color(0xFF…)` literals** bypassing `MaterialTheme.colorScheme`, plus scattered
  manual `isSystemInDarkTheme()` — fragile dark-mode behavior.
- **Business logic/formatting invoked directly in composition** (31 `viewModel.format*/get*`
  call sites, many unremembered) — recomputed each recomposition and duplicating domain rules
  (ties to Phase-1 finding C-2).
- **Accessibility gaps**: 70 `contentDescription = null`.
- **Low preview coverage**: 17 previews for 223 composables (~8%).
- **God-Composables** (Phase-1 C-1) make all of the above harder to localize.

Net: the UI *works*, but it does **more allocation and recomposition than necessary**, has
**inconsistent theming**, and is **under-tested/under-previewed**. All findings are broad but
mechanical to fix, and several share one root cause (unwired typography, missing lifecycle
collection helper).

**Verdict:** 🟠 Functionally solid Compose, with pervasive allocation/theming/accessibility debt.

---

## Metrics (whole UI tree)

| Signal | Count | Reading |
|--------|------:|---------|
| `@Composable` functions | 223 | baseline |
| Inline `FontFamily(Font(...))` | **185** | per-recomposition allocation |
| Hardcoded `Color(0xFF…)` | **135** | theme bypass |
| `collectAsState()` / `collectAsStateWithLifecycle` | 45 / **0** | no lifecycle-aware collection |
| `viewModel.format*/get*` in composition | 31 | logic in UI, often unremembered |
| `contentDescription = null` | 70 | a11y gaps |
| `@Preview` functions | 17 | ~8% preview coverage |
| `remember {` / `derivedStateOf` / `LaunchedEffect` | 169 / 13 / 74 | idiomatic usage present |
| `MutableInteractionSource()` without `remember` | 1 | interaction-state loss |

---

## Strengths

- **100% Compose**, no legacy View/XML UI to bridge (`nav_graph.xml` and RecyclerView paths gone).
- **Correct list virtualization**: `LazyColumn` uses **stable keys** (`row.stableKey`) and
  `animateItem` — history list avoids the classic key/animation bugs.
- **State is unidirectional**: ViewModels expose `StateFlow`; Composables read via `collectAsState`
  and hoist callbacks. `derivedStateOf` is used to derive grouped rows (good).
- **A shared theme exists** (`MegaWalletTheme`, `Typography`, color scheme) — the scaffolding for
  consistency is present; it is simply under-used.
- **Coil** wired through a single app-level `ImageLoader` (per-item loader was deliberately removed).

---

## Problems

Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low. Each: **Impact · Reason · Suggested Solution ·
Estimated Difficulty**.

### Critical 🔴

#### CU-1 — 185 inline `FontFamily(Font(...))` allocations; custom fonts not wired into `Typography`
- **Severity:** 🔴 Critical (performance + consistency)
- **Impact:** Every `Text` that builds its `FontFamily` inline re-allocates it on each
  recomposition; on scrolling lists and animated screens this is continuous GC pressure and
  wasted work. Font styling is also duplicated 185× and drifts.
- **Reason:** `common_ui/theme/Type.kt` defines `Typography` but with `FontFamily.Default`
  (comment: "We can replace FontFamily.Default with a custom font … later"). Because the real
  Persian fonts (`iransansmobile_fa_*`, `inter_*`) are never in the theme, every call site does
  `FontFamily(Font(R.font.…))` inline.
- **Suggested Solution:** Define font families **once** as top-level `val`s in `common_ui`
  (e.g. `val IranSans = FontFamily(...)`, `val Inter = FontFamily(...)`) and wire them into
  `Typography`. Replace inline constructions with `MaterialTheme.typography.*` or the shared
  `val`s. Removes ~185 allocations and centralizes typography.
- **Estimated Difficulty:** Medium (broad find-replace; low risk, high payoff).

### High 🟠

#### CU-2 — God-Composables enlarge recomposition scope
- **Severity:** 🟠 High
- **Impact:** Large composable bodies mean a single state change recomposes a big subtree; also
  the locus of the recent history z-index bug. Hard to reason about skippability/stability.
- **Reason:** `SendConfirmScreen` (1,977), `SendScreen` (1,472), `MainScreen` (1,152),
  `AssetDetailScreen` (1,087), `TransactionDetailsBottomSheet` (1,035), `MultiWalletScreen` (917),
  `GeneratingAnimation` (1,477). (Cross-ref Phase-1 C-1.)
- **Suggested Solution:** Extract stable, parameterized sub-Composables per section; pass
  immutable UI-state slices so Compose can skip unchanged subtrees. Prioritize `SendConfirmScreen`
  and `MainScreen`.
- **Estimated Difficulty:** High (broad, incremental).

#### CU-3 — No lifecycle-aware state collection (0 `collectAsStateWithLifecycle`)
- **Severity:** 🟠 High
- **Impact:** UI keeps collecting flows and can recompose while backgrounded — wasted CPU/battery
  and, for a wallet, needless price/balance/socket-driven churn off-screen.
- **Reason:** All 45 collection sites use `collectAsState()`; none use the lifecycle-aware variant.
- **Suggested Solution:** Replace `collectAsState()` with `collectAsStateWithLifecycle()`
  (`androidx.lifecycle:lifecycle-runtime-compose`, already available). Mechanical.
- **Estimated Difficulty:** Low.

#### CU-4 — Formatting/business logic executed inside composition
- **Severity:** 🟠 High
- **Impact:** `viewModel.format*/get*` called directly in composable bodies (31 sites; e.g.
  `TransactionDetailsBottomSheet` lines 174/251/274/281/357/369) run on every recomposition when
  not wrapped in `remember(key)`, and duplicate domain rules in the UI.
- **Reason:** Presentation formatting lives in ViewModels and is pulled during composition rather
  than being precomputed into an immutable UI-state model.
- **Suggested Solution:** Precompute display strings into a UI-state data class emitted by the
  ViewModel (or `remember(transaction) { … }` at minimum). Aligns with Phase-1 C-2 (logic → use
  cases).
- **Estimated Difficulty:** Medium.

#### CU-5 — Theming bypassed by hardcoded colors + manual dark-mode branches
- **Severity:** 🟠 High
- **Impact:** 135 `Color(0xFF…)` literals (e.g. `Color(0xFF8F8F96)`, `Color.Black`) don't adapt to
  light/dark or theme changes; combined with scattered `isSystemInDarkTheme()` checks, dark mode
  is inconsistent and hard to maintain.
- **Reason:** Palette not fully centralized in `colorScheme`; call sites hardcode hex.
- **Suggested Solution:** Move recurring literals into the theme (`colorScheme` + semantic custom
  colors) and reference `MaterialTheme.colorScheme.*`; delete ad-hoc `isSystemInDarkTheme()` where
  the scheme already differentiates.
- **Estimated Difficulty:** Medium.

### Medium 🟡

#### CU-6 — Accessibility: unlabeled icons/images
- **Severity:** 🟡 Medium
- **Impact:** 70 `contentDescription = null` — TalkBack users lose meaning of status/action icons;
  material for a finance app and for Play policy/accessibility scanning.
- **Reason:** Decorative default applied broadly, including to meaningful glyphs (send/receive
  direction, status).
- **Suggested Solution:** Provide `contentDescription` for meaningful icons; keep `null` only for
  truly decorative ones. Consider a lint rule.
- **Estimated Difficulty:** Low–Medium.

#### CU-7 — Hand-rolled navigation & overlay z-index in `MainScreen`
- **Severity:** 🟡 Medium (UI correctness; architecture side in Phase-1 H-3)
- **Impact:** Overlay layering is manual (`zIndex`, `MainTabLayer`), which already produced a
  user-facing bug (bottom sheet hidden behind a tab layer); each overlay adds recomposition
  surface.
- **Reason:** No navigation host; screens/overlays are stacked by hand in one composable.
- **Suggested Solution:** Centralize overlay/back-stack ordering behind the typed navigation
  abstraction proposed in Phase 1.
- **Estimated Difficulty:** Medium–High.

#### CU-8 — Low preview coverage
- **Severity:** 🟡 Medium
- **Impact:** 17 previews / 223 composables (~8%); UI is iterated in-app rather than in isolation,
  slowing development and hiding light/dark + RTL regressions.
- **Reason:** Previews not a standard per-component practice.
- **Suggested Solution:** Add `@Preview` (light/dark, RTL) for reusable components and key screens;
  leverage extracted sub-Composables from CU-2.
- **Estimated Difficulty:** Low (incremental).

#### CU-9 — `SendConfirmScreen` fee tabs bound to hardcoded placeholder data
- **Severity:** 🟡 Medium
- **Impact:** SMART/CREDIT fee tabs render fabricated literals (`"ETH 1"`, `SmartFeeInfo("12",…)`)
  with `isLoading` hardcoded — non-functional UI if shipped (also flagged in code review).
- **Reason:** WIP UI merged ahead of its ViewModel wiring; a banner block is commented out rather
  than removed.
- **Suggested Solution:** Gate tabs behind real ViewModel state or keep out of release builds;
  delete dead/commented code.
- **Estimated Difficulty:** Low.

### Low 🟢

#### CU-10 — `MutableInteractionSource()` without `remember`
- **Severity:** 🟢 Low
- **Impact:** `AnimatedBottomSheetCard.kt:73` creates a new `MutableInteractionSource` each
  recomposition, losing press/interaction state and allocating needlessly.
- **Reason:** Missing `remember { }`.
- **Suggested Solution:** `val interaction = remember { MutableInteractionSource() }`.
- **Estimated Difficulty:** Low (one line).

#### CU-11 — Inconsistent RTL/bidi strategy
- **Severity:** 🟢 Low
- **Impact:** RTL is handled with localized `CompositionLocalProvider(LocalLayoutDirection provides
  Ltr)` overrides (e.g. amount rows) rather than a consistent policy; risk of mixed-direction
  layout bugs in Persian.
- **Reason:** Per-site overrides instead of a documented bidi approach.
- **Suggested Solution:** Define a small set of direction-aware helpers/conventions for numeric vs
  text content; document once.
- **Estimated Difficulty:** Low–Medium.

---

## Recommended Refactoring (UI, sequenced)

1. **Centralize fonts into `Typography`/shared `FontFamily` vals** (CU-1) — biggest single win.
2. **Swap to `collectAsStateWithLifecycle`** everywhere (CU-3) — mechanical, immediate.
3. **Precompute display state / `remember` formatting** (CU-4) — reduces recomposition cost and
   duplication.
4. **Centralize colors into the theme** (CU-5).
5. **Decompose god-Composables** (CU-2) — unlocks previews (CU-8) and safer overlays (CU-7).
6. **Accessibility pass** (CU-6) and **fee-tab wiring / dead-code removal** (CU-9).

## Suggested Structure Improvements

- A `common_ui/theme/` that fully owns fonts, colors, and typography; UI code references
  `MaterialTheme.*` only.
- Per-feature `…UiState` immutable models so Composables receive precomputed, stable inputs.
- Extracted, previewable components (`components/`) with light/dark/RTL previews.

## Cross-references
- God files, navigation, logic-in-UI: Phase-1 C-1, C-2, H-3.
- Recomposition/allocation cost quantified here feeds the **Performance** (Phase 3) and
  **Memory/CPU** (Phase 4) audits.
- New debt appended to `technical-debt.md` as TD-12…TD-18.

_Phase 2 complete. Awaiting approval before starting Phase 3 (Performance)._
