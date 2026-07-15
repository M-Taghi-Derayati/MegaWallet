# TASK-28 — Performance measurement foundation (Baseline Profile + Macrobenchmark)

- **ID:** TASK-28
- **Category:** Performance Baseline (measurement only — **not** optimization)
- **Severity:** 🟡 Medium
- **Why before release?** Phases 2–4 identified heavy recomposition/allocation (185 `FontFamily`
  allocs, no lifecycle collection) and cold-start cost. Before optimizing (Sprint 1) we need a
  **repeatable baseline** so improvements are measurable and regressions are caught — and a Baseline
  Profile itself improves cold-start/jank on release builds at effectively zero code risk.

## Problem
No performance-measurement infrastructure exists: no Baseline Profile, no Macrobenchmark module, no
startup/jank benchmark.

## Evidence
- `grep baselineprofile|macrobenchmark|androidx.benchmark` → 0. No `:benchmark` module in
  `settings.gradle.kts`.

## Files involved
- New module `:benchmark` (`com.android.test`) + `settings.gradle.kts`
- `gradle/libs.versions.toml` (androidx.benchmark.macro, baselineprofile plugin)
- `app/build.gradle.kts` (baselineprofile consumer + `baselineProfile` src set)

## Proposed solution
1. Add a Macrobenchmark module with:
   - **StartupBenchmark** (cold/warm/hot `StartupTimingMetric`).
   - **ScrollBenchmark** (history/wallet list `FrameTimingMetric` for jank).
2. Add the **Baseline Profile** generator (critical journey: launch → unlock → wallet list → open
   asset) and wire the `androidx.baselineprofile` plugin so release builds embed the profile.
3. Record the **baseline numbers** in `docs/performance/` as the reference for Sprint 1.

## Acceptance criteria
- [ ] `:benchmark` runs on a physical device and emits startup + frame-timing metrics.
- [ ] A Baseline Profile is generated and embedded in the release build.
- [ ] Baseline startup (cold/warm/hot) and list-jank numbers recorded in `docs/performance/`.
- [ ] No changes to production behavior (measurement + profile only).

## Testing steps
- `./gradlew :benchmark:connectedBenchmarkAndroidTest` on a real device; archive results.
- Generate profile; confirm `assembleRelease` includes it; re-run startup benchmark to show delta.

## Dependencies
- A physical device/emulator for benchmarking. Independent of code fixes.

## Estimated effort
1–1.5 dev-days (module + profile + recording baseline).
