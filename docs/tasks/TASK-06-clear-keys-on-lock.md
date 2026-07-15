# TASK-06 — Clear in-memory key cache on app-lock and on background

- **Debt:** TD-23 · **Finding:** Memory M4-1 / Security S-7 · **Severity:** 🟠 High
- **Type:** Ship-blocker (pre-release security gate — **scoped: minimal fix only**)
- **Est. difficulty:** Low · **Status:** ✅ IMPLEMENTED (inspection-verified; on-device check pending)

## Review outcome (why elevated)
Verified: the security/session layer has **zero** references to `KeyManager`/`clearCache`/
`lockWallet`. App-lock is a pure `isLocked: StateFlow<Boolean>` gate (`AppLockUseCases`/
`AppLockManager`), and there is **no** `onStop`/`onPause`/background hook that clears keys. So
`KeyManager.credentialsCache` (decrypted `web3j.Credentials`) stays resident from unlock until wallet
delete/switch or process death — i.e. **while the app is "locked" the private keys are still in the
heap**. For a wallet that ships a lock feature, that makes the lock non-protective against a local
attacker (root/debugger/heap dump). The minimal fix is cheap and closes the locked-but-resident
window, so it belongs in the gate.

## Scope of THIS task (minimal fix only)
Clear the in-memory key cache when the app locks and when it goes to background; re-hydrate on
authenticated unlock. **Not** in scope here: binding the Keystore key to user authentication — that
deeper hardening is **TD-35** (scheduled "Now", not a blocker) and should reference this task.

## Files
- `core/src/main/java/com/mtd/core/wallet/ActiveWalletManager.kt` (`lockWallet()` already clears)
- `core/src/main/java/com/mtd/core/keymanager/KeyManager.kt` (`clearCache()` exists)
- App-lock trigger: `viewmodel/news/AppLockViewModel.kt`, `domain/.../usecase/security/AppLockUseCases.kt`,
  the `AppLockManager` that owns `isLocked`
- Background hook: `MainActivityCompose.kt` / a `ProcessLifecycleOwner` observer

## Proposed change
- When `isLocked` transitions to `true` (and/or on `ON_STOP`/app-background), call
  `activeWalletManager.lockWallet()` (or at minimum `keyManager.clearCache()`).
- On successful unlock, re-hydrate via the existing `unlockWallet(secret)` path (secret comes from
  `SecureStorage`, which is Keystore-encrypted).
- Ensure re-hydration doesn't reintroduce TASK-05's main-thread blocking (do it off-main).

## Acceptance criteria
- [x] After locking or backgrounding, `KeyManager.credentialsCache` is empty (no `Credentials` retained).
- [x] Signing works again after a successful unlock (cache re-hydrated).
- [x] No decrypted key material survives a lock→foreground cycle without a fresh unlock.
- [x] Links TD-35 as the follow-up for cryptographic (auth-bound key) hardening.

## Implementation notes
New app-layer coordinator [`WalletLockKeyCoordinator`](../../app/src/main/java/com/mtd/megawallet/session/WalletLockKeyCoordinator.kt)
enforces the invariant **"decrypted keys are cached iff the app is unlocked AND foregrounded"**:

- Observes `AppLockManager.isLocked` combined with an internal foreground flag fed by
  `MainActivityCompose.onStart`/`onStop`.
- On **lock or background** → `keyManager.clearCache()` (drops the decrypted `web3j.Credentials`).
- On **unlock + foreground** (with a wallet present) → re-hydrates off the main thread via
  `IWalletRepository.loadExistingWallet()` (which re-reads the Keystore-encrypted secret and reloads
  the cache), so this never reintroduces the TASK-05 main-thread `runBlocking` regression.

**Scoped deliberately:** it clears *only* the credential cache — it does **not** call
`ActiveWalletManager.lockWallet()` (which nulls the active wallet and, via
`WalletSessionAuthCoordinator`, would sign out the JWT + drop the `/ws` socket on every soft
auto-lock). Engagement is gated on app-lock actually being enabled, so no-passcode users keep their
current behavior (no resume-time re-derivation, no session churn).

`KeyManager` is confirmed `@Singleton` (provided in `CryptoModule`), so the cleared instance is the
same one the send/signing path reads from.

**Follow-up:** TD-35 / TASK-19 — bind the Keystore master key to user authentication
(`setUserAuthenticationRequired(true)`), which closes the residual window where the *encrypted*
secret is decryptable without a fresh auth.

### On-device verification (pending — cannot run in this environment)
- [ ] Unlock, use wallet, background/auto-lock → heap dump (or `adb shell am dumpheap`) shows no
      `org.web3j.crypto.Credentials` / `ECKeyPair` retained.
- [ ] After a fresh unlock, a send signs successfully (cache re-hydrated).
- [ ] Quick background→foreground within the lock timeout (no lock) still leaves signing functional.
