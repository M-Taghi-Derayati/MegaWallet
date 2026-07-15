# TASK-26 — StrictMode in debug builds

- **ID:** TASK-26 (implements **TD-44**; supersedes the StrictMode slice of TASK-21)
- **Category:** ANR & Main-Thread Safety / Dev-infra
- **Severity:** 🟡 Medium (release-gate enabler)
- **Why before release?** StrictMode is the automated tripwire that would have caught the
  main-thread `runBlocking` (TASK-05) before it shipped. Enabling it now prevents the *next*
  disk/network-on-main regression from reaching Beta and validates the ANR-safety of the wallet flows.

## Problem
No `StrictMode` anywhere → disk/network-on-main and leaked-closable violations go unflagged in dev.

## Evidence
- `grep StrictMode` over `*.kt` → 0 (Phase 7 / PR-4, TD-44).

## Files involved
- `app/.../MegaWalletApplication.kt` (install in `onCreate`, debug-only via `BuildConfig.DEBUG`).

## Proposed solution
- In `onCreate`, when `BuildConfig.DEBUG`:
  - `ThreadPolicy`: `detectDiskReads/detectDiskWrites/detectNetwork/detectCustomSlowCalls` →
    `penaltyLog()` (start with log; consider `penaltyDeath()` after the codebase is clean).
  - `VmPolicy`: `detectLeakedClosableObjects/detectActivityLeaks/detectLeakedRegistrationObjects` →
    `penaltyLog()`.
- Whitelist known-benign first-run SharedPreferences load if noisy (do not mask real violations).

## Acceptance criteria
- [ ] Debug build logs StrictMode policy on startup; release build is unaffected (no StrictMode).
- [ ] Opening create-wallet, unlock, send, and history shows **no** disk/network-on-main violations
      (after TASK-05).
- [ ] Any remaining violation is triaged (fixed or explicitly suppressed with a comment).

## Testing steps
- Run debug; filter Logcat for `StrictMode`. Walk the core flows; confirm clean (or file follow-ups).

## Dependencies
- Best run after TASK-05 (removes the known main-thread `runBlocking`).

## Estimated effort
0.25 dev-day.
