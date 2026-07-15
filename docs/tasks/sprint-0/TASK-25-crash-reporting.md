# TASK-25 — Crash & error reporting (observability)

- **ID:** TASK-25
- **Category:** Crash & Stability Readiness / Observability
- **Severity:** 🟠 High (release blocker)
- **Why before release?** A non-custodial wallet must not ship blind. Without crash/exception
  reporting there is no way to detect or triage production crashes during send/sign/import —
  a single reflection/R8/edge-case crash in the money path would be invisible until store reviews.

## Problem
No crash-reporting SDK is wired. Search shows only `firebase-auth` (24.0.0) + the Firebase Gradle
plugin present; **no Crashlytics / Sentry / Bugsnag**. Uncaught exceptions and non-fatals are lost.

## Evidence
- `grep crashlytics|sentry|bugsnag` over `*.kts/*.toml/*.kt/*.xml` → 0 SDK usages.
- `gradle/libs.versions.toml`: `firebase_plugin`, `firebase-auth` present (Firebase already in the
  project, so Crashlytics is the low-friction choice).
- 3 existing `CoroutineExceptionHandler`/uncaught-handler sites (partial, not centralized, not reported).

## Files involved
- `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts` (plugin + deps)
- `app/.../MegaWalletApplication.kt` (init + a global uncaught-exception → non-fatal bridge)
- `app/google-services.json` (Firebase config — needs the Firebase project)

## Proposed solution
1. Add Firebase Crashlytics (reuse the existing Firebase project) **or** a privacy-reviewed
   alternative. Initialize in `MegaWalletApplication.onCreate`.
2. Route the existing `applicationScope` `SupervisorJob` + a `CoroutineExceptionHandler` and a
   `Thread.setDefaultUncaughtExceptionHandler` to log non-fatals/fatals to the reporter.
3. **Privacy:** disable collection in debug; scrub any PII/secret — never attach mnemonics, private
   keys, addresses, JWTs, or signed payloads to crash metadata. Add a custom-key allowlist.
4. Make collection opt-in/opt-out consistent with the app's privacy posture.

## Acceptance criteria
- [ ] A forced test crash in a debug/staging build appears in the dashboard.
- [ ] No mnemonic/private key/JWT/address/signed-payload in any crash record (verified on a captured report).
- [ ] Collection is disabled (or clearly gated) in debug builds.
- [ ] Uncaught coroutine exceptions on wallet flows are captured as non-fatals, not silent.

## Testing steps
- Trigger `throw RuntimeException("test-crash")` behind a debug switch → confirm dashboard entry.
- Force a coroutine failure in a send flow → confirm non-fatal captured.
- Inspect a report’s custom keys/logs for secret leakage.

## Dependencies
- Firebase project access (or chosen vendor). Privacy/legal sign-off on telemetry.

## Estimated effort
0.5–1 dev-day (SDK wiring) + PII-scrub review.
