# Security Audit (Phase 6)

**Scope:** key lifecycle & storage, secret management, transport trust, on-device signing isolation,
cloud-backup safety, screen/data-exposure protections, logging hygiene, and dependency/supply-chain
surface — for a **non-custodial crypto wallet** (highest-stakes phase).
**Method:** read-only inspection of the crypto/keystore, backup, auth, manifest, and network-security
config. No code modified. Consolidates transport findings from Phase 5 and key-lifetime from Phase 4.

---

## Executive Summary

The **core cryptography is done correctly** — this is the most important thing to state up front.
Wallet secrets are encrypted at rest with a **hardware-backed AndroidKeystore AES-256-GCM key**
(`mega_wallet_master_key`, non-exportable), the GCM/IV handling is correct, and **cloud backups are
password-encrypted** with PBKDF2-HMAC-SHA256 + per-backup random salt/IV + AES-256-GCM (versioned
format, password `char[]` cleared). Signing is on-device (non-custodial), no mnemonic/private-key is
logged, and the JWT is host-scoped to the relayer. A weak-looking `APP_SECRET_KEY` turns out **not**
to be the wallet-encryption key — it is only an app-attestation HMAC secret, which limits blast
radius.

The gaps are in the **layers around** the good crypto:

- 🔴 **Transport trust is broken for release** (from Phase 5): the network-security config permits
  cleartext and **trusts user-installed CAs in release** builds — MITM of relayed signed
  transactions and the session JWT.
- 🟠 **The Keystore master key is not bound to user authentication** (`setUserAuthenticationRequired`
  never set), and (Phase 4) keys are **not cleared on lock** — so app-lock is a UI gate, not a
  cryptographic one.
- 🟠 **No `FLAG_SECURE`** anywhere — the **seed-phrase / private-key screens are screenshot-,
  screen-record-, and recents-thumbnail-capturable**.
- 🟠 **Hardcoded, trivially weak `APP_SECRET_KEY`** in `BuildConfig` (attestation bypass).
- 🟠 **No TLS certificate pinning**.

None of these defeat the at-rest encryption directly (the Keystore key stays in hardware), but
several materially widen the practical attack surface — especially transport MITM and seed-screen
capture. Fix the 🔴/🟠 items before any production release.

**Verdict:** 🟢 Strong at-rest & backup crypto, wrapped in 🔴/🟠 transport-trust, key-binding, and
screen-exposure gaps that must close before release.

---

## Strengths (verified)

- **At-rest encryption:** `KeyStoreManager` provisions a 256-bit AES-GCM key in **AndroidKeystore**
  (hardware-backed, non-exportable); `AESGCMCipher` uses `AES/GCM/NoPadding`, Keystore-generated IV
  prepended to ciphertext, 128-bit tag — textbook GCM.
- **Cloud backup:** `PasswordBasedCipher` = PBKDF2WithHmacSHA256 (65,536 iters), 16-byte random
  salt + 12-byte random IV per backup, AES-256-GCM, versioned envelope; `BackupRepositoryImpl`
  encrypts **before** `uploadBackup` — the seed is never uploaded in cleartext.
- **Non-custodial signing:** private keys are used on-device; only signed payloads are relayed.
- **No secret logging:** no `mnemonic`/`privateKey`/`seed` in `Timber`/`Log`; only 3 plain `Log`
  calls in the whole codebase.
- **Token boundary:** `AuthInterceptor` host-scopes the JWT to the relayer; log headers redacted.
- **Config integrity:** dynamic config verified with a pinned secp256k1 signature.
- **Blast-radius limit:** `APP_SECRET_KEY` is an HMAC attestation secret (`HmacUtils`), **not** the
  wallet-encryption key.

---

## Problems

Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low. Each: **Impact · Reason · Suggested Solution ·
Estimated Difficulty**.

### Critical 🔴

#### S-1 — Release builds permit cleartext and trust user CAs (transport MITM)
- **Severity:** 🔴 Critical
- **Impact:** Relayed **signed transactions** and the **session JWT** can be intercepted/altered by
  a MITM (user-installed cert, device-admin CA, or interception proxy). For a wallet this is the
  most dangerous gap.
- **Reason:** `network_security_config.xml` uses a plain `<base-config cleartextTrafficPermitted=
  "true">` with `<certificates src="user"/>` and **no `<debug-overrides>`**, so it applies to
  release (Phase 5 N-1). Compounded by plaintext relayer endpoints (N-3) and no pinning (S-5/N-2).
- **Suggested Solution:** Restrict cleartext + user-CA trust to `<debug-overrides>`; HTTPS-only +
  system trust in release; then move the relayer to TLS and pin.
- **Estimated Difficulty:** Low (config), gated on relayer TLS.

### High 🟠

#### S-2 — Keystore master key not bound to user authentication
- **Severity:** 🟠 High
- **Impact:** `SecureStorage` can decrypt wallet secrets **without any biometric/passcode challenge**,
  because the master key has no `setUserAuthenticationRequired(true)`. App-lock is therefore only a
  UI overlay; anyone able to run the app on an unlocked/compromised device (or invoke the Keystore
  key) can decrypt. Combined with S-7 (keys not cleared on lock), the unlocked window is wide.
- **Reason:** `KeyStoreManager.generateOrGetSecretKey()` sets block mode/padding/size but never
  `setUserAuthenticationRequired`/`setUserAuthenticationParameters`; no StrongBox request.
- **Suggested Solution:** Gate the wallet-secret key (or a dedicated seed-access key) on user
  authentication with a bounded validity window, and request StrongBox where available. Re-auth to
  decrypt on unlock.
- **Estimated Difficulty:** Medium (auth-bound keys interact with the biometric flow).

#### S-3 — No `FLAG_SECURE`: seed/private-key screens capturable
- **Severity:** 🟠 High
- **Impact:** Seed-phrase display, backup verification, and private-key/passcode screens can be
  **screenshotted, screen-recorded, and appear in the app-switcher thumbnail** — a classic seed
  exfiltration path (malware/screen recorders, shoulder-surf thumbnails).
- **Reason:** `FLAG_SECURE` is set **nowhere** in the app.
- **Suggested Solution:** Set `WindowManager.LayoutParams.FLAG_SECURE` on activities/windows that
  render seed/private-key/passcode (at minimum onboarding create/import + backup + receive-with-key).
- **Estimated Difficulty:** Low.

#### S-4 — Hardcoded, weak `APP_SECRET_KEY` in BuildConfig
- **Severity:** 🟠 High
- **Impact:** The app-attestation HMAC secret (`HMAC-SHA256("$nonce-$deviceId", APP_SECRET_KEY)`)
  is a literal (`"release_super_secret_key_456"`) compiled into the APK. Anyone can decompile,
  extract it, and **forge the attestation** — defeating the relayer's "genuine app" check and
  enabling abuse/impersonation of the relayer API.
- **Reason:** `data/build.gradle.kts` sets `APP_SECRET_KEY` per build type as a plain string; any
  secret shipped in a client is extractable in principle, and this one is also trivially guessable.
- **Suggested Solution:** Treat client attestation as non-secret; use real device/app attestation
  (Play Integrity / server-issued device challenge — the `DeviceChallenge*` DTOs suggest this is
  already partially in place) rather than a static shared secret. At minimum move it out of VCS and
  rotate.
- **Estimated Difficulty:** Medium.

#### S-5 — No TLS certificate pinning
- **Severity:** 🟠 High (carry Phase 5 N-2)
- **Impact:** Once on HTTPS, absent pinning any mis-issued/compromised CA (and, per S-1, user CAs)
  can MITM wallet↔relayer traffic.
- **Reason:** No `CertificatePinner`/pin-set anywhere.
- **Suggested Solution:** Pin the relayer cert/public key with backup pins + rotation.
- **Estimated Difficulty:** Medium.

### Medium 🟡

#### S-6 — `allowBackup="true"` with empty backup/extraction rules
- **Severity:** 🟡 Medium
- **Impact:** `backup_rules.xml` and `data_extraction_rules.xml` are empty templates, so default
  auto-backup/adb-backup includes **all** app storage. The Keystore-encrypted secrets stay safe
  (the key is non-exportable and not backed up), but any **non-Keystore** prefs/DataStore
  (addresses, cached balances, connection mode, lock state) are backed up in cleartext — a privacy
  leak and a confusing restore surface for a wallet.
- **Reason:** `allowBackup="true"` + no explicit exclusions.
- **Suggested Solution:** Set `allowBackup="false"` (recommended for wallets) or explicitly exclude
  `secure_prefs` and all sensitive stores in the rules.
- **Estimated Difficulty:** Low.

#### S-7 — Decrypted keys retained in memory across app-lock (carry Phase 4 M4-1)
- **Severity:** 🟡 Medium (memory + security)
- **Impact:** `KeyManager.credentialsCache` holds decrypted keys until wallet switch/delete or
  process death; lock does not clear them (widens the S-2 window; heap-dump exposure).
- **Suggested Solution:** Clear on lock/background; re-hydrate on authenticated unlock.
- **Estimated Difficulty:** Low–Medium.

#### S-8 — Idempotency keys regenerated per attempt (carry Phase 5 N-4)
- **Severity:** 🟡 Medium (fund safety)
- **Impact:** App-level retries of `/relay`, `/broadcast`, `/sponsor-approve` get fresh keys →
  double-submit/double-fund possible.
- **Suggested Solution:** Stable per-operation idempotency key.
- **Estimated Difficulty:** Medium.

### Low 🟢

#### S-9 — PBKDF2 iteration count below current guidance; derived key not zeroed
- **Severity:** 🟢 Low
- **Impact:** 65,536 iterations for PBKDF2-HMAC-SHA256 is under OWASP's current recommendation
  (~210,000), lowering brute-force cost against a stolen Drive backup; the derived AES key bytes are
  not wiped after use (only the password `char[]` is cleared).
- **Suggested Solution:** Raise iterations (or move to scrypt/Argon2), and zero the derived key
  material after `SecretKeySpec` construction.
- **Estimated Difficulty:** Low.

#### S-10 — Insecure Gradle repositories (supply chain)
- **Severity:** 🟢 Low
- **Impact:** `settings.gradle.kts` uses deprecated `jcenter()` and `isAllowInsecureProtocol=true`,
  widening dependency-tampering surface at build time.
- **Suggested Solution:** Remove `jcenter()`; require HTTPS for all repos.
- **Estimated Difficulty:** Low.

#### S-11 — Crypto dependency CVE hygiene (periodic)
- **Severity:** 🟢 Low
- **Impact:** BouncyCastle is intentionally pinned to `bcprov-jdk18on:1.73`; web3j/bitcoinj/
  bitcoin-kmp carry crypto surface. Pins are good for reproducibility but must be tracked against
  advisories.
- **Suggested Solution:** Add periodic dependency-CVE scanning; re-evaluate the BC pin against known
  advisories on a schedule.
- **Estimated Difficulty:** Low (process).

---

## Recommended Order (pre-release security gate)
1. **S-1** (release cleartext/user-CA) → **S-3** (`FLAG_SECURE`) → **S-6** (`allowBackup`) — low-effort,
   high-value, ship-blockers.
2. **S-2** (auth-bound key) + **S-7** (clear on lock) — make the lock cryptographic.
3. **S-4** (attestation) + **S-5** (pinning) + **S-8** (idempotency).
4. **S-9…S-11** — hardening/hygiene.

## Cross-references
- S-1/S-5 = Phase-5 N-1/N-2; S-7 = Phase-4 M4-1; S-8 = Phase-5 N-4.
- New debt appended to `technical-debt.md` as TD-34…TD-40.
- See `roadmap.md` for the consolidated remediation sequence.

_Phase 6 complete — all six audit phases finished. See `master-audit.md` for the roll-up._
