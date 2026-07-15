# Android Architecture Audit (Phase 1)

**Scope:** Clean Architecture · module structure · package organization · dependency direction ·
SOLID · scalability · maintainability · technical debt.
**Out of scope (separate phases):** performance, memory/CPU, Compose/UI internals, networking
internals, security internals. Where those intersect architecture, they are cross-referenced, not
detailed.
**Method:** read-only inspection of build files, module/package layout, DI, navigation, and
representative interfaces — reusing facts gathered this session. No production code modified.

---

## Executive Summary

MegaWallet is a **non-custodial multi-chain wallet** (EVM / Bitcoin-UTXO / Tron), ~47,200 LOC of
Kotlin across **5 Gradle modules**, 100% Jetpack Compose, Hilt DI. The **macro-architecture is
sound**: a genuine Clean-Architecture layering with a strictly enforced dependency direction,
34 domain interfaces, 21 use cases, and two standout design strengths — **data-driven chain/asset
configuration** (add a chain via JSON) and a **transparent DIRECT/PROXY transport abstraction**
that returns identical domain types.

The **micro-architecture is where debt concentrates.** The presentation layer has drifted from the
clean core: several **god files** (UI up to 1,977 LOC, ViewModels up to 1,137 LOC) mix rendering,
formatting, and business logic; **business rules leak into ViewModels/Composables** instead of use
cases; packaging is **layer-first** with a mis-named catch-all `viewmodel/news` package; and — most
consequentially for a "clean" claim — the **`:domain` module is not framework-free** (it depends on
`androidx.core.ktx`, `material`, and Hilt). Navigation is a **hand-rolled state machine** inside a
1,152-LOC `MainScreen`, and real **test coverage exists only in `:data`**.

None of these are emergencies; the foundation is good enough that they are addressable
incrementally. Priorities: restore domain purity, decompose the largest presentation files, push
business logic into use cases, and move from layer-first to feature-first packaging before the
feature count grows further.

**Verdict:** 🟢 Solid architecture with 🟠 significant, well-localized presentation-layer debt.

---

## Current Architecture

**Style:** Clean Architecture (Presentation → Domain ← Data), MVVM in the UI, unidirectional
state flow via Kotlin `StateFlow`. DI by Hilt throughout.

**Modules & dependency direction:**

```
app ─────► common_ui, core, data, domain      (presentation + composition root)
data ────► core, domain                        (repo impls, data sources, networking, DI)
core ────► domain                              (crypto/blockchain primitives, registries)
domain ──► (intended: none)                    (models, I* interfaces, use cases)
common_ui► (Compose theme/icons, Coil, QR)     (shared UI)
```

**Key mechanisms** (detail in `docs/architecture/overview.md`):
- **Registry + Strategy** for chains: `BlockchainRegistry` builds `BlockchainNetwork` via
  `NetworkFactory` per `NetworkType`; networks/assets load from `networks.json` / `assets.json`.
- **DIRECT vs PROXY** transport behind `IChainDataSource` (`ChainDataSourceFactory`).
- **`UnifiedTransferCoordinator`** as the single send entry point, delegating gasless flows.
- **State-based navigation** in `MainScreen` (no Navigation-Compose; `nav_graph.xml` deleted).
- **`BaseViewModel(ErrorManager)`** base class for all 13 ViewModels.

**Numbers:** 5 modules · 34 domain interfaces · 21 use cases · 16 repo impls · 13 ViewModels ·
42 Compose screen files.

---

## Strengths

- **Strictly enforced dependency direction.** `domain`/`core` never import upward; interfaces in
  `domain`, implementations in `data`/`core`. This is the hardest part of Clean Architecture to
  sustain and it is largely intact.
- **Data-driven extensibility.** New chains/tokens come from JSON, not `when(networkType)`
  branches — a genuinely scalable design decision that resists combinatorial growth.
- **Transport polymorphism.** DIRECT and PROXY sources return the same domain types, so the
  toggle is invisible to ViewModels — clean use of the dependency-inversion principle.
- **Interface segregation is taken seriously** (34 focused `I*` contracts, `IChainDataSource`,
  `INetworkCatalog`, etc.), enabling mockable tests (the `:data` suite exploits this well).
- **Single composition root** (`MegaWalletApplication` + Hilt) with clear module boundaries for DI.
- **Dependency hygiene at the build level:** version catalog + bundles, deliberate BouncyCastle
  pin, native-multidex via `minSdk 26`.

---

## Problems

Grouped by severity. Every issue carries **Severity · Impact · Reason · Suggested Solution ·
Estimated Difficulty**.

### Critical Issues 🔴

#### C-1 — God files in the presentation layer
- **Severity:** 🔴 Critical
- **Impact:** Low testability, high merge-conflict rate, slow comprehension, recompositions and
  bugs hard to localize (the recent history bottom-sheet z-index defect lived in the 1,152-LOC
  `MainScreen`).
- **Reason:** Several files carry many responsibilities at once:
  `SendConfirmScreen.kt` (1,977), `GeneratingAnimation.kt` (1,477), `SendScreen.kt` (1,472),
  `MainScreen.kt` (1,152), `SendViewModel.kt` (1,137), `AssetDetailScreen.kt` (1,087),
  `TransactionDetailsBottomSheet.kt` (1,035), `TransactionHistoryViewModel.kt` (1,017),
  `MultiWalletScreen.kt` (917). This is a direct **Single-Responsibility** violation.
- **Suggested Solution:** Decompose top-down: extract sub-Composables (per section) and pull
  formatting/mapping into use cases (see C-2/H-3). Target < ~400 LOC per UI/VM file. Start with
  `SendConfirmScreen` and `MainScreen`.
- **Estimated Difficulty:** High (mechanical but broad; do incrementally, file by file).

#### C-2 — Business logic embedded in ViewModels/Composables
- **Severity:** 🔴 Critical (architectural correctness)
- **Impact:** Domain rules are untested and duplicated across the UI; the same logic diverges
  between paths (e.g. fee-tier Persian→English mapping is done in one send path but not another —
  a real defect found in code review).
- **Reason:** `TransactionHistoryViewModel` and the send stack hold formatting, tier mapping,
  amount normalization, and fee computation — logic that belongs in `:domain` use cases. The
  `usecase/` package exists (21 use cases) but presentation still owns significant rules.
- **Suggested Solution:** Move formatting/mapping/normalization behind use cases (e.g.
  `FormatTransactionFeeUseCase`, `ResolveFeeTierUseCase`, `NormalizeHistoryPageUseCase`).
  ViewModels orchestrate; they don't compute.
- **Estimated Difficulty:** Medium–High.

### High Priority Issues 🟠

#### H-1 — `:domain` is not framework-free
- **Severity:** 🟠 High
- **Impact:** Undermines the central Clean-Architecture guarantee; couples pure business models to
  Android/Hilt, blocks pure-JVM unit testing of the domain, and risks Android types leaking into
  contracts.
- **Reason:** `domain/build.gradle.kts` declares `androidx.core.ktx`, `material`, and
  `dagger.hilt` (+ KSP). The module is an `android.library`, not a pure Kotlin/JVM module. This
  contradicts the project's own stated rule ("Never add Android or third-party SDK deps here").
- **Suggested Solution:** Convert `:domain` to a `java-library`/`kotlin("jvm")` module; remove
  `androidx`/`material`; replace Hilt annotations in domain with plain constructors (bind in
  `data`/`app`). Keep only Kotlin + coroutines (+ `java.math`).
- **Estimated Difficulty:** Medium (a few annotations + build change; compiler will surface leaks).

#### H-2 — Layer-first packaging + catch-all `viewmodel/news`
- **Severity:** 🟠 High
- **Impact:** A single feature (e.g. "send") is scattered across `ui/compose/screens/send`,
  `viewmodel/news`, `domain/usecase/send`, `data/...` — cross-cutting changes touch many folders;
  discoverability is poor and worsens as features grow.
- **Reason:** Packaging is by technical layer, and **all 11 `news`-package ViewModels** (Send,
  Home, Wallet, Receive, etc.) sit in a mis-named `viewmodel/news` folder unrelated to "news".
- **Suggested Solution:** Move to **package-by-feature** inside `:app` (colocate screen + VM +
  UI-state per feature); rename `news`. This is the stepping stone to feature modules (see roadmap).
- **Estimated Difficulty:** Medium (moves + import churn; no behavior change).

#### H-3 — Hand-rolled navigation concentrated in `MainScreen`
- **Severity:** 🟠 High
- **Impact:** No single source of truth for navigation; back-handling and overlay z-index layering
  are manual and error-prone (already produced a user-facing bug); adding screens inflates
  `MainScreen` further.
- **Reason:** Navigation is expressed as ad-hoc state + overlay layers in a 1,152-LOC composable,
  with `nav_graph.xml` removed and no typed abstraction replacing it.
- **Suggested Solution:** Introduce a thin, typed navigation abstraction (a sealed
  `Destination` model + a small host) — keep the state-driven philosophy but centralize
  transitions/back-stack/overlay ordering out of `MainScreen`.
- **Estimated Difficulty:** Medium–High.

#### H-4 — Test coverage limited to `:data`
- **Severity:** 🟠 High
- **Impact:** ViewModels and use cases (the layers with the most churn and recent defects) are
  effectively unverified; refactors above are riskier than they should be.
- **Reason:** `:app`, `:domain`, `:core` carry only placeholder `ExampleUnitTest`; meaningful
  tests live in `:data` (JUnit4 + MockK + MockWebServer).
- **Suggested Solution:** Add ViewModel tests (Turbine/coroutine-test) and pure use-case tests as
  logic migrates into `:domain` (synergizes with H-1/C-2 — a pure domain is trivially testable).
- **Estimated Difficulty:** Medium (ongoing).

### Medium Issues 🟡

#### M-1 — Duplicated transport-mode decision
- **Severity:** 🟡 Medium
- **Impact:** DIRECT/PROXY is decided both in `ChainDataSourceFactory` and again per-branch in
  `UnifiedTransferCoordinator`; the two can drift (wrong tx shape for the selected mode).
- **Reason:** The mode check (`currentMode() == PROXY`) is repeated in the coordinator instead of
  fully delegating to the already mode-selected data source.
- **Suggested Solution:** Emit one canonical request and let the mode-selected data source own
  prepare-vs-direct; remove per-network `if (PROXY)` branches from the coordinator.
- **Estimated Difficulty:** Medium.

#### M-2 — Overlapping `MainViewModel` vs `MainScreenViewModel`
- **Severity:** 🟡 Medium
- **Impact:** Unclear ownership; risk of state split across two VMs for one screen.
- **Reason:** Two similarly-named ViewModels coexist without a documented responsibility split.
- **Suggested Solution:** Clarify or merge; name by responsibility.
- **Estimated Difficulty:** Low–Medium.

#### M-3 — Large multi-responsibility data sources
- **Severity:** 🟡 Medium
- **Impact:** `BitcoinDataSource` (1,068 LOC) and `ProxyChainDataSource` mix RPC, tx-building, and
  parsing — harder to test in isolation.
- **Reason:** Data-source classes accumulate builder + transport + mapping concerns.
- **Suggested Solution:** Split tx-builders (already partly done: `BitcoinjUtxoTxBuilder`,
  `EvmTxSigner`) from transport/parsing; keep data sources thin.
- **Estimated Difficulty:** Medium.

#### M-4 — Insecure Gradle repositories
- **Severity:** 🟡 Medium (build/supply-chain; full treatment in security phase)
- **Impact:** Deprecated `jcenter()` and `isAllowInsecureProtocol=true` widen supply-chain risk.
- **Reason:** `settings.gradle.kts` still lists `jcenter()` and allows insecure `maven` protocol.
- **Suggested Solution:** Remove `jcenter()`; require HTTPS for all repos.
- **Estimated Difficulty:** Low.

### Low Priority Issues 🟢

- **L-1 — Mixed Persian/English comments** (accepted convention). Impact: onboarding friction for
  non-Persian contributors. Solution: keep, but write new public/API docs in English. Difficulty: Low.
- **L-2 — No README / ADRs.** Load-bearing decisions live only in inline comments. Solution: add a
  root README + ADRs under `docs/architecture/`. Difficulty: Low.
- **L-3 — `docs/mobile.zip`** opaque binary in the docs tree (see documentation audit). Difficulty: Low.
- **L-4 — `app/prod/` and `app/release/`** appear as untracked build-output-like dirs; confirm
  they are ignored, not stray artifacts. Difficulty: Low.

---

## Recommended Refactoring (sequenced)

1. **Restore domain purity** (H-1): convert `:domain` to pure Kotlin/JVM, strip Android/Hilt.
2. **Extract business logic into use cases** (C-2): formatting, fee-tier mapping, history
   normalization — this also shrinks the god ViewModels.
3. **Decompose god files** (C-1): `SendConfirmScreen`, `MainScreen`, `SendScreen`, then the rest.
4. **Package-by-feature + rename `news`** (H-2).
5. **Centralize navigation** (H-3) behind a typed abstraction.
6. **Consolidate transport routing** (M-1).
7. **Backfill tests** for ViewModels/use cases (H-4) as they become pure.

## Suggested Module Improvements

- Make `:domain` a `kotlin("jvm")` library (H-1).
- Keep `:core` as the crypto/registry hub; move any remaining tx-building out of data sources into
  dedicated builders/signers (already trending this way).
- Consider a `:common_domain`/`:common_test` for shared test fixtures once domain is pure.
- **Later:** extract per-feature modules (`:feature_send`, `:feature_history`, `:feature_wallet`,
  `:feature_onboarding`) on top of `core`/`domain`/`data` for parallel builds and clear ownership.

## Suggested Folder Structure (target, package-by-feature in `:app`)

```
app/…/megawallet/
  feature/
    send/         { SendScreen, SendConfirmScreen, SendViewModel, SendUiState }
    history/      { TransactionHistoryScreen, …ViewModel, components/ }
    wallet/       { WalletScreen, AssetDetailScreen, MultiWalletScreen, VMs }
    onboarding/   { Welcome, CreateWallet, ImportWallet + VMs }
    main/         { MainScreen (thin host), MainScreenViewModel }
  navigation/     { Destination (sealed), NavHost abstraction }
  core/           { BaseViewModel, ErrorManager glue }
domain/…/         { model, usecase/<feature>, interfaceRepository }  ← pure Kotlin
```

## Future Architecture Roadmap

See `docs/audits/roadmap.md` for the phased plan (Now / Next / Later) and success signals. In
brief: **stabilize foundations** (domain purity, secrets/transport, top god files) → **structure
for scale** (feature packaging, logic-in-use-cases, typed navigation, tests) → **modularize**
(feature modules, single-layer transport routing, ADRs).

---

## Cross-references
- Debt items rolled into `docs/audits/technical-debt.md` (TD-01…TD-11).
- UI-internal, performance, memory, networking, and security specifics are deferred to their
  respective phases (leads pre-seeded in each report).

_Phase 1 complete. Awaiting approval before starting Phase 2 (Compose/UI)._
