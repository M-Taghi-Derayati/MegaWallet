# TASK-04 — Remove dead APP_SECRET_KEY; correct device attestation

- **Debt:** TD-37 · **Finding:** Security S-4 · **Severity:** 🟢 Low (was 🟠 High) · **Est.:** ~1 day
- **Status:** ✅ Security cleanup DONE · TASK-04a (attestation wiring) = follow-up, **not a blocker**

## ⚠️ UPDATE (2026-07 — backend confirmed; supersedes the original analysis below)

The backend owner confirmed the real picture, which **downgrades this from a High ship-blocker**:

1. **`APP_SECRET_KEY` was dead code.** Zero references anywhere on the server (no route/middleware/env),
   and — verified on the client — **no Kotlin code reads `BuildConfig.APP_SECRET_KEY`** and
   `HmacUtils` was never called. So there was **no forgeable global app-secret HMAC**; the original
   S-4 risk does not exist. → **Removed** the field from `data/build.gradle.kts`.
2. **The only attestation is `device-challenge`**, and it is **soft**: a client without attestation is
   "device-less" and **login + normal relay + history/balances/quote/swap all work**. Attestation is
   enforced (401 `requireDevice`) **only** on device-bound features: **gas-credit quote/relay** and
   **Mystery Box**.
3. **Real mechanism** (`services/deviceAttestation.js`, method `hmac`) is **two-level**:
   `deviceKey = HMAC-SHA256(masterSecret, deviceId)` (raw bytes), then
   `attestationSignature = HMAC-SHA256(deviceKey, "$nonce-$deviceId")` (hex). The client's old
   **single-level** `HMAC(APP_SECRET_KEY, "$nonce-$deviceId")` was wrong on both the algorithm and the
   secret. → **Fixed** `HmacUtils.generateDeviceAttestation(...)` to match; comment corrected in `AuthApiDto`.
4. **The secret** equivalent is `DEVICE_ATTEST_HMAC_SECRET` (the server value is fresh/random on the
   NL host). → **Externalized** as `BuildConfig.DEVICE_ATTEST_HMAC_SECRET` sourced from an untracked
   Gradle property (empty until set) — same pattern as the release signing secrets.

### Done in this task (security part)
- ✅ Removed dead `APP_SECRET_KEY` (debug + release) from `data/build.gradle.kts`.
- ✅ Added externalized `DEVICE_ATTEST_HMAC_SECRET` (Gradle property, not committed).
- ✅ Corrected `HmacUtils` to the server's two-level HMAC; fixed `AuthApiDto` doc.

### TASK-04a — wire device attestation (follow-up, NOT a release blocker)
Only needed to enable **gas-credit + Mystery Box**. Requires:
- The real `DEVICE_ATTEST_HMAC_SECRET` value (from backend) placed in an untracked `gradle.properties`.
- A product decision: are gas-credit / Mystery Box in **Beta** scope? If not, defer.
- Wire `AuthRepositoryImpl`: `POST /api/auth/device-challenge` → `HmacUtils.generateDeviceAttestation`
  → include `{deviceId, nonce, attestationSignature}` in `/verify` (fields already exist in `AuthApiDto`).
- On-device test: `deviceVerified=true` in the verify response; a gas-credit call returns non-401.

---

## Original analysis (pre-backend-confirmation — kept for history; now largely moot)

## Problem
`data/build.gradle.kts` compiles a plaintext, trivially-weak `APP_SECRET_KEY` into `BuildConfig`
(`"debug_super_secret_key_123"` / `"release_super_secret_key_456"`). It backs the app-attestation
HMAC `HMAC-SHA256("$nonce-$deviceId", APP_SECRET_KEY)` (`HmacUtils`, `AuthApiDto`). Anyone can
decompile the APK, extract the literal, and **forge the attestation** — defeating the relayer's
"genuine app" check and enabling API abuse/impersonation. Any secret shipped in a client is
extractable in principle; a static shared secret provides weak assurance.

## Files
- `data/build.gradle.kts` (lines ~35, ~39 — `buildConfigField "APP_SECRET_KEY"`)
- `core/src/main/java/com/mtd/core/crypto/HmacUtils.kt`
- `data/src/main/java/com/mtd/data/dto/AuthApiDto.kt`
- (related, already present) `data/.../dto/DeviceChallengeRequest.kt` / `DeviceChallengeResponse.kt`

## Proposed change (phased)
1. **Short term:** remove the secret from VCS/`build.gradle`; inject at build time from a
   secret store / `local.properties` / CI secret, and rotate the value. (Reduces exposure; does not
   fix the extractable-client-secret limitation.)
2. **Target:** replace the static shared secret with real attestation — **Play Integrity API** and/or
   the server-issued **device challenge** flow the `DeviceChallenge*` DTOs already hint at — so
   trust is server-verified rather than a compiled-in constant.

## Acceptance criteria
- [ ] No `APP_SECRET_KEY` literal in tracked source / `build.gradle`.
- [ ] Attestation path documented; a plan (or implementation) for Play Integrity / device-challenge
      exists and is agreed with the backend owner.
- [ ] Auth/attestation still succeeds against the relayer in debug.

## Notes
Backend coordination required (the relayer validates the HMAC). Treat step 2 as the real fix; step 1
is interim risk reduction.
