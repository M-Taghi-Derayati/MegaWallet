# Server Integration Delta — client vs `ANDROID_SERVER_INTEGRATION.md`

Compared the server guide (`megaWallet_server/.../docs/ANDROID_SERVER_INTEGRATION.md`, 2026-07-13,
testnet live on NL) against the current client. **Structure matches; four items diverge** and change
task priority.

## ✅ Matches (no action)
- **Proxy endpoints:** `/api/mobile/v1/*` (history/balances/fees/prepare/broadcast/status), gasless
  routers `/api/evm` + `/api/tron` — client `ProxyChainDataSource` + gasless coordinators align.
- **Config bundle:** `/api/v1/config/{public-key,bundle,version}` + `/capabilities`, secp256k1-signed
  — client `ConfigManager` verifies signature. (Ensure the public-key is **pinned** on first fetch.)
- **Realtime dedupe:** 5s window by `eventId` — client `EventDeduplicationCache` (5s TTL) matches.
- **Idempotency transport:** `X-Idempotency-Key` on relay/broadcast — client `IdempotencyInterceptor`
  sends it (but see mismatch #2 for the *generation* bug).

## ❌ Mismatches → task updates

### 1. 🔴 Relayer base URL is stale — server is now HTTPS (unblocks TLS)
- **Server:** `https://wallet.intexchange.ir` (CDN→NL, TLS live). Raw origin `http://103.112.69.154:3000`
  debug-only. WS `wss://wallet.intexchange.ir/ws`.
- **Client:** `RELAYER_BASE_URL=http://192.168.90.153:3000/`, `RELAYER_HOST=192.168.90.153`,
  `RELAYER_WS_URL=ws://…` (old dev IP, plaintext).
- **Impact:** This **unblocks** the release-TLS close-out. TASK-01 (NSC) and TASK-11 (TLS+pinning) are
  no longer blocked. TD-30 (plaintext relayer) is now fixable.
- **Action → NEW `TASK-31`** (relayer HTTPS cutover): point release BuildConfig at
  `https://wallet.intexchange.ir` / `wss://…/ws`; keep the raw IP for debug; update the NSC **debug**
  overlay host (`192.168.90.153` → `103.112.69.154`). Then TASK-11 can add cert pinning on the domain.
  **Priority: P0 (top of Sprint 0 remaining).**

### 2. 🟠 Idempotency generation bug — server contract now confirms the fix (unblocks TASK-07)
- **Server:** "same key + **same payload** = same result, no double-send"; conflict code
  `IDEMPOTENCY_KEY_CONFLICT`.
- **Client bug:** `IdempotencyInterceptor` mints a **fresh `UUID` per physical request**, so a
  client resubmit after an ambiguous timeout sends a *different* key → server can't dedupe.
- **Action → TASK-07 un-held.** No backend change needed — the contract already supports it. Fix is
  purely client: generate the key **once per logical operation** (caller-supplied/content-derived) and
  reuse across retries. **Status: HELD → READY (P0).**

### 3. 🟠 Auth model — server uses wallet-signature + device-challenge, not a static app secret
- **Server:** `POST /api/auth/challenge` **or** `/api/auth/device-challenge` → wallet signs →
  `/api/auth/verify {address,signature}` → JWT (+ refresh/logout). No mention of an `APP_SECRET_KEY`
  HMAC.
- **Client:** carries `APP_SECRET_KEY` HMAC (`HMAC-SHA256("$nonce-$deviceId", secret)`, `AuthApiDto`).
- **Impact:** TASK-04's target — replace the static secret with **server device-challenge** — is
  already supported server-side (`/api/auth/device-challenge`). The static HMAC looks legacy.
- **Action → TASK-04 direction confirmed.** ⚠️ **One clarification needed from backend:** does the
  current server still validate the `APP_SECRET_KEY` HMAC on any path, or is `device-challenge` the
  sole attestation? Until answered, keep TASK-04 held but re-scoped to "adopt `/api/auth/device-challenge`".

### 4. 🟡 Realtime is thin-signal + refresh-on-signal, and gated OFF
- **Server:** pushes **signals, never data** — `tx.new`, `balance.invalidated`, `tx.status.updated`
  (envelope `{id,name,ts,seq,payload}`); client maps each → a repository **refresh**; `cursor` opaque
  (pass to `syncSince`, never parse); a missed/dup signal → at worst a redundant re-fetch. **Gated
  behind `REALTIME_THIN_EVENTS_ENABLED` (currently OFF).**
- **Impact on our findings:**
  - **TD-42** (SharedFlow `replay=1` event loss) **severity ↓** — refresh-on-signal is idempotent, so
    a dropped signal only delays a refresh; not a data-corruption risk. Keep the fix (adequate buffer)
    but it's no longer gate-critical.
  - **TD-46** (reconnect re-sync) **severity ↑** — the correct recovery for *lost* signals is a
    **full re-sync on reconnect** using `seq` gap detection + `syncSince(cursor)`. This is the primary
    realtime robustness requirement.
  - Realtime is **OFF** → not an immediate release blocker; build the `RealtimeEventDispatcher` to
    spec (signal→repo refresh) so it activates when the flag flips.
- **Action:** fold into TASK-22 (realtime robustness); re-scope to the thin-signal dispatcher +
  reconnect `seq`-gap re-sync. Move TASK-22 detail note; no new task.

## Minor alignment notes (fold into existing tasks / QA)
- **`Cache-Control: no-store`** on API — ensure OkHttp/Retrofit does not cache API responses (verify in TASK-29).
- **`gaslessEnabled` per token** gates the gasless button (only sepolia + shasta live) — verify client reads the flag (QA T3, TASK-30).
- **Sponsor `approveTxTemplate`** gas params must be used verbatim (no auto-estimate) — QA T3.
- **`staleSources[]`** in history already handled client-side (Phase 7).

## Net effect on Sprint 0
- **Newly actionable (were blocked):** relayer HTTPS cutover (**TASK-31**, P0), stable idempotency
  (**TASK-07**, P0, no backend change), TLS pinning (**TASK-11**, now unblocked).
- **Still needs backend answer:** TASK-04 (one question: is the app-secret HMAC still required?).
- **De-prioritized:** TD-42 (realtime event loss) — from gate to normal, since design is idempotent
  and the feature is flag-off.
