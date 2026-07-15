# Networking Audit (Phase 5)

**Scope:** transport security (TLS/cleartext/pinning), OkHttp/Retrofit config, interceptors
(auth, idempotency, connectivity, logging), timeouts/retries, WebSocket, error taxonomy, and
DIRECT/PROXY parity.
**Method:** read-only inspection of `NetworkModule`, interceptors, the network security config,
manifest, and socket manager. No code modified. Reuses prior code-review findings rather than
re-deriving them.
**Note:** deep key/secret handling is Phase 6; this phase covers the transport layer and flags the
security-critical transport items for that phase.

---

## Executive Summary

The **application-layer** networking design is **good**: interceptors are ordered correctly and
**host-scoped** so the wallet's bearer JWT and idempotency keys are only ever sent to the relayer
(never to CoinCap/Wallex), log output **redacts** `Authorization`/`Cookie`/`X-Idempotency-Key` and
drops to `BASIC` in release, the error taxonomy is typed (`ApiError`/`SafeApiCall`/`proxyCall`), and
dynamic config is integrity-checked with a **secp256k1 signature** at the app layer.

The **transport layer is the problem**, and one finding is serious:

- **`network_security_config.xml` weakens TLS for *release*, not just debug.** Despite a comment
  saying it "applies only to debug builds," the file uses a plain **`<base-config>`** (no
  `<debug-overrides>`), so **`cleartextTrafficPermitted="true"`** *and* a **`<certificates
  src="user"/>`** trust anchor apply to **all build types including release**. A production build
  therefore permits cleartext HTTP app-wide and trusts user-installed CAs — trivially MITM-able.
- **No TLS certificate pinning** exists anywhere.
- The relayer itself is plaintext `http://`/`ws://` to a dev IP via `BuildConfig` (known
  `TODO(release)`), and the config's dev-IP allowlist is **stale** (doesn't even include the
  configured relayer IP — cleartext "works" only because the global base-config permits it).
- **Idempotency keys are regenerated per attempt** (`UUID.randomUUID()` in the interceptor), so
  app-level retries of `/relay`, `/broadcast`, `/sponsor-approve` get *new* keys — defeating the
  double-submit/double-fund protection the interceptor exists to provide.

Net: fix the transport posture before any non-debug release. The app-layer plumbing is sound and
mostly release-ready.

**Verdict:** 🔴 Transport security misconfigured for release; 🟢 solid application-layer networking.

---

## Strengths

- **Host-scoped auth & idempotency.** `AuthInterceptor` adds `Bearer <jwt>` only when
  `request.url.host == RELAYER_HOST`, respects a pre-set `Authorization`, and checks token validity;
  `IdempotencyInterceptor` is likewise relayer-scoped. The JWT cannot leak to third-party price
  hosts sharing the client.
- **Log redaction + release downgrade.** `redactHeader("Authorization"/"Cookie"/"X-Idempotency-Key")`;
  `Level.BODY` only in `BuildConfig.DEBUG`, `BASIC` in release.
- **App-layer integrity for config.** The dynamic config bundle is verified with a pinned secp256k1
  key (`ConfigSignatureVerifier`) — a strong pattern that could inform TLS-trust decisions.
- **Reasonable client config.** 35 s connect/read/write timeouts; WS uses `readTimeout(0)` +
  `pingInterval` (20 s / 30 s). Typed errors via `ApiError`/`ApiException`/`SafeApiCall`.
- **Third-party calls are HTTPS** (CoinCap `rest.coincap.io`, Wallex `api.wallex.ir`).

---

## Problems

Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low. Each: **Impact · Reason · Suggested Solution ·
Estimated Difficulty**.

### Critical 🔴

#### N-1 — Cleartext + user-CA trust apply to **release** builds (not debug-only)
- **Severity:** 🔴 Critical
- **Impact:** A production build permits cleartext HTTP for every domain and **trusts
  user-installed CA certificates**, making all traffic — including relayed **signed transactions**
  and the session **JWT** — susceptible to trivial man-in-the-middle (a user-installed or
  device-admin-pushed cert, or any interception proxy on the network path). For a non-custodial
  wallet this is a severe exposure.
- **Reason:** `app/src/main/res/xml/network_security_config.xml` declares a plain
  `<base-config cleartextTrafficPermitted="true">` with `<certificates src="user"/>`. `base-config`
  is the default for **all** build types; there is **no `<debug-overrides>`** wrapper, so the inline
  comment ("only in debug") is false.
- **Suggested Solution:** Move cleartext + `src="user"` trust into a `<debug-overrides>` block;
  make the release `base-config` HTTPS-only with system trust anchors. Pair with N-2/N-3.
- **Estimated Difficulty:** Low (config file) — but gated on the relayer supporting TLS.

### High 🟠

#### N-2 — No TLS certificate pinning
- **Severity:** 🟠 High
- **Impact:** Even once the relayer is on HTTPS, absent pinning the app trusts the entire CA set
  (and, per N-1, user CAs), so a mis-issued/compromised CA can MITM wallet↔relayer traffic.
- **Reason:** No `CertificatePinner`/`sslSocketFactory`/`hostnameVerifier` anywhere (the sole
  "pinning" grep hit is an unrelated comment about secp256k1 **config-bundle** signing).
- **Suggested Solution:** Add an OkHttp `CertificatePinner` (or NSC `<pin-set>`) for the relayer
  host once TLS is live; keep backup pins and a rotation plan.
- **Estimated Difficulty:** Medium (needs cert/key material + rotation process).

#### N-3 — Plaintext relayer endpoints (http/ws) shipped via BuildConfig
- **Severity:** 🟠 High
- **Impact:** `RELAYER_BASE_URL`/`RELAYER_WS_URL` are `http://`/`ws://` to a dev IP; combined with
  N-1 the relay path is unencrypted in any build.
- **Reason:** `:data` `BuildConfig` fields still point at the dev host (`TODO(release)` present).
- **Suggested Solution:** Switch to `https://`/`wss://` prod hosts before non-debug release; the
  socket manager already derives `wss` from `https` automatically.
- **Estimated Difficulty:** Low (config) once the backend has TLS.

#### N-4 — Idempotency key regenerated per attempt defeats double-submit protection
- **Severity:** 🟠 High (fund-safety)
- **Impact:** `IdempotencyInterceptor` generates a fresh `UUID.randomUUID()` on each interceptor
  pass. An **app-level retry** (e.g. `GaslessRequoteRetry`/`ExponentialBackoff` re-invoking the
  Retrofit call) sends a **different** `X-Idempotency-Key`, so the relayer cannot dedupe — exactly
  the "must not double-fund on retry" case the interceptor documents for `/relay`,
  `/transactions/broadcast`, and `/sponsor-approve`.
- **Reason:** The key is minted inside the interceptor per physical request rather than being
  stable for a logical operation (caller-supplied or content-derived).
- **Suggested Solution:** Derive the key from the logical request (caller passes a stable key, or
  hash the idempotent payload) so all retries of the same operation carry one key. Interceptor-minted
  UUIDs only cover in-call connection retries, not app retries.
- **Estimated Difficulty:** Medium.

### Medium 🟡

#### N-5 — DIRECT/PROXY behavioral drift (carried from code review)
- **Severity:** 🟡 Medium
- **Impact:** PROXY-mode TRON send forwards the raw Persian `feeLevel` (EVM maps it to
  slow/standard/fast) → relayer mis-parses the tier; multi-asset price fallback sends ids into the
  `search` query param → wrong/empty prices. Both break the "DIRECT and PROXY are equivalent"
  contract.
- **Reason:** See code-review findings (ProxyChainDataSource feeLevel; MarketDataRepositoryImpl).
- **Suggested Solution:** Map `feeLevel` uniformly in the PROXY Tron path; pass ids in `ids=`.
- **Estimated Difficulty:** Low.

#### N-6 — Socket reconnect guard + duplicated backoff (carried from code review)
- **Severity:** 🟡 Medium
- **Impact:** `connect()`'s `shouldBeConnected` guard can defeat the documented post-sign-in retry
  (socket never connects if `connect()` races ahead of the token); reconnect backoff is
  re-implemented instead of using the shared `ExponentialBackoff`.
- **Reason:** See Phase-1 code review (NotificationSocketManager).
- **Suggested Solution:** Re-arm `attemptConnection()` when a token arrives; reuse `ExponentialBackoff`.
- **Estimated Difficulty:** Low.

### Low 🟢

#### N-7 — Stale cleartext allowlist in NSC
- **Severity:** 🟢 Low
- **Impact:** The `<domain-config>` lists dev IPs (195.78.49.45, 192.168.235.230, …) that do **not**
  include the configured relayer `192.168.90.153`; the list is dead weight and misleading — cleartext
  to the relayer only works via the global base-config (N-1).
- **Suggested Solution:** Remove the stale list; scope cleartext to the actual dev host under
  `<debug-overrides>`.
- **Estimated Difficulty:** Low.

#### N-8 — Debug BODY logging includes signed payloads/tokens in the body; release logs to Logcat
- **Severity:** 🟢 Low
- **Impact:** `redactHeader` covers headers, not bodies; debug `BODY` logs signatures / prepare /
  quote tokens. Release `BASIC` still emits request lines to Logcat at `ERROR` (the app's release
  Timber tree logs ≥ INFO).
- **Suggested Solution:** Gate all network logging off in release; avoid logging signed-payload
  bodies even in debug (or redact them).
- **Estimated Difficulty:** Low.

#### N-9 — `provideRetrofitBuilder` default `baseUrl("https://placeholder.com/")`
- **Severity:** 🟢 Low
- **Impact:** Any service created without overriding `baseUrl` silently targets `placeholder.com`.
- **Suggested Solution:** Fail-fast default or require an explicit base URL per service.
- **Estimated Difficulty:** Low.

---

## Recommended Order
1. **N-1** — make cleartext + user-CA trust debug-only (blocker for release).
2. **N-3 → N-2** — move relayer to TLS, then pin.
3. **N-4** — stabilize idempotency keys (fund-safety).
4. **N-5 / N-6** — DIRECT/PROXY parity + socket reconnect.
5. **N-7…N-9** — config/logging cleanup.

## Cross-references
- N-1/N-2/N-3 feed the **Security** audit (Phase 6). N-4 is fund-safety and security-adjacent.
- N-5/N-6 originate in the Phase-1 code review.
- New debt appended to `technical-debt.md` as TD-28…TD-33.

_Phase 5 complete. Awaiting approval before starting Phase 6 (Security)._
