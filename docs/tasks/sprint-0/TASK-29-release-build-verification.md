# TASK-29 — Release build verification (R8 / secrets / logging)

- **ID:** TASK-29
- **Category:** Release Build Verification
- **Severity:** 🟠 High (release blocker)
- **Why before release?** The app uses reflection-heavy crypto libs (web3j, bitcoinj, bitcoin-kmp,
  BouncyCastle, Gson). R8 (`minifyEnabled=true`, `shrinkResources=true`) can strip/rename classes
  these libs resolve by name → **crashes that appear ONLY in release**, in the signing/tx path. This
  must be proven safe on a *signed release* build before Beta, not discovered in production.

## Problem
Release verification has not been performed end-to-end: no confirmation that a signed, minified
release build runs the wallet flows without R8-induced failures, that debug-only tooling is excluded,
and that no secrets are shippable in the APK.

## Evidence
- `app/build.gradle.kts`: release `isMinifyEnabled = true`, `isShrinkResources = true`,
  `proguard-rules.pro` referenced; signing config present (from Phase 1).
- `app/proguard-rules.pro`: 72 lines, 28 `keep` rules, **includes** web3j/bitcoin keeps (so rules
  exist — this task is to **verify**, not author from scratch).
- Library modules (`:core`, `:data`, `:domain`, `:common_ui`) have `isMinifyEnabled=true` but their
  `proguardFiles`/consumer rules are largely **commented out** (Phase 1) — verify keeps propagate.
- Logging: release Timber tree logs ≥INFO to Logcat; OkHttp release logging = BASIC (TD-33).
- Secrets: `APP_SECRET_KEY` compiled into `BuildConfig` (TD-37 / TASK-04).

## Files involved
- `app/proguard-rules.pro`, module `consumer-rules.pro` files
- `app/build.gradle.kts`, `data/build.gradle.kts` (BuildConfig fields, logging)
- `settings.gradle.kts` (repos — TD-09)

## Proposed solution
1. `./gradlew assembleRelease` with the real signing config; smoke-test on device:
   **create wallet → import → unlock → EVM send → Tron send → gasless → history**.
2. If any R8 crash: add targeted `-keep` rules (web3j/bitcoinj/bitcoin-kmp/BouncyCastle/Gson model
   classes, `@Keep` where needed); re-verify. Confirm module consumer rules propagate.
3. Confirm **debug-only deps are excluded** from release (LeakCanary/benchmark once added; the debug
   NSC overlay from TASK-01).
4. Strip/guard release logging so no request lines / payloads reach Logcat (folds TD-33).
5. `apkanalyzer`/`strings` the release APK for secrets (mnemonics never stored in code; confirm no
   `APP_SECRET_KEY`-class literals — depends on TASK-04).
6. Remove `jcenter()` / insecure repos (TD-09) as part of build hygiene.

## Acceptance criteria
- [ ] Signed release APK installs and completes all core wallet flows **without R8/reflection crashes**.
- [ ] Release APK contains **no** LeakCanary/benchmark/debug-NSC artifacts.
- [ ] No signed payloads / tokens / request URLs logged in a release build.
- [ ] `strings`/apkanalyzer shows no shippable app secret (post TASK-04).
- [ ] Mapping file archived for crash deobfuscation (ties to TASK-25).

## Testing steps
- Device smoke test of the 7 flows on the release build.
- Diff release vs debug dependency/resource reports.
- Retain `mapping.txt`; upload to Crashlytics (TASK-25).

## Dependencies
- Signing keystore access. TASK-04 (secret removal) for the no-secrets criterion. TASK-25 (mapping upload).

## Estimated effort
1 dev-day (verify) + variable if R8 keeps must be added.
