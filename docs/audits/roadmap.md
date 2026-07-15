# Architecture & Audit Roadmap

Seeded from Phase 1. Sequenced to reduce risk before scaling features.

## Phase 0 — Pre-release security gate (days, not weeks) — BLOCKERS
These are low-effort, high-value, and must land before any non-debug release:
- **Fix the network-security config** (TD-34): cleartext + user-CA trust → `<debug-overrides>` only;
  HTTPS-only + system trust in release.
- **`FLAG_SECURE`** on seed/private-key/passcode screens (TD-36).
- **`allowBackup="false"`** or explicit sensitive-store exclusions (TD-38).
- **Move `APP_SECRET_KEY` out of VCS/BuildConfig**; move toward real attestation (TD-37).
- **Remove main-thread `runBlocking`** at cold start (TD-19).
- **Clear the in-memory key cache on lock/background** (TD-23, scoped — deep auth-bound-key fix is
  TD-35, scheduled below).
- **Stable per-operation idempotency key** for `/relay`, `/broadcast`, `/sponsor-approve` (TD-31,
  scoped — backend must confirm dedup window).

## Now (0–1 month) — stabilize foundations
1. **Purge Android/Hilt deps from `:domain`** (TD-02) so the layer is genuinely framework-free.
2. **Make the lock cryptographic** (TD-35 + TD-23): auth-bind the Keystore key; clear keys on lock.
3. **Relayer TLS + certificate pinning** (TD-30 → TD-29).
4. **Break up the top 3 god files** (TD-01) starting with `SendConfirmScreen` and `MainScreen`.
5. **Centralize fonts into `Typography`** (TD-12) and switch to `collectAsStateWithLifecycle` (TD-13).

## Next (1–3 months) — structure for scale
4. **Package-by-feature** (TD-03): colocate screen + viewmodel + state per feature; rename the
   `viewmodel/news` catch-all.
5. **Move business logic into use cases** (TD-04): formatting, fee-tier mapping, history
   normalization become domain use cases; ViewModels orchestrate only.
6. **Introduce a typed navigation abstraction** (TD-07) to replace ad-hoc state navigation.
7. **Raise test coverage** for `:app` ViewModels and `:domain` use cases (TD-06).

## Later (3–6 months) — modularization
8. **Extract feature modules** (`:feature_send`, `:feature_history`, `:feature_wallet`, …) on
   top of `:core`/`:domain`/`:data`, enabling parallel work and faster builds.
9. **Consolidate transport routing** (TD-05) so DIRECT/PROXY lives in one layer.
10. **ADRs** for the load-bearing decisions (navigation, transport, key handling).

## Success signals
- No file > ~400 LOC in UI/VM layers; `:domain` has zero Android imports.
- New chain/asset still added by JSON only (preserve the current data-driven strength).
- Feature work touches one feature module, not many layer folders.
