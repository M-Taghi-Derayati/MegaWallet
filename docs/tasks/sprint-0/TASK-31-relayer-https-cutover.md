# TASK-31 — Point client at the live HTTPS relayer

- **ID:** TASK-31
- **Category:** Release Security / Networking
- **Severity:** 🔴 Critical (release blocker) — **now unblocked** (server TLS is live)
- **Why before release?** The server moved to `https://wallet.intexchange.ir` (TLS, CDN→NL), but the
  client still ships `http://192.168.90.153:3000` (old dev IP, plaintext). Until the client points at
  HTTPS, all relayer traffic — signed txs, JWT — is cleartext, and the release NSC (TASK-01, now
  HTTPS-only) will simply **fail to connect**. This is the concrete unblock of TD-30 and the gateway
  to TASK-11 (pinning).

## Problem / Evidence
- Server guide: base `https://wallet.intexchange.ir`; raw origin `http://103.112.69.154:3000`
  (debug only); WS `wss://wallet.intexchange.ir/ws`.
- `data/build.gradle.kts`: `RELAYER_BASE_URL="http://192.168.90.153:3000/"`,
  `RELAYER_HOST="192.168.90.153"`, `RELAYER_WS_URL="ws://192.168.90.153:3000/ws"`.
- `app/src/debug/res/xml/network_security_config.xml` (TASK-01) whitelists the old `192.168.90.153`.

## Proposed solution
1. **Release** BuildConfig (`data/build.gradle.kts`):
   - `RELAYER_BASE_URL = "https://wallet.intexchange.ir/"`
   - `RELAYER_HOST = "wallet.intexchange.ir"`
   - `RELAYER_WS_URL = "wss://wallet.intexchange.ir/ws"`
   (Split per build type: keep the raw origin IP for `debug` if useful.)
2. **Debug** NSC overlay: replace `192.168.90.153` with `103.112.69.154` (raw origin) so on-device
   debugging against the origin still works; keep emulator/loopback entries.
3. Confirm `NotificationSocketManager` derives `wss://…/ws` correctly from the new HTTPS base (it
   already does `https→wss`).
4. Remove the `TODO(release)` markers; hand off to **TASK-11** for cert/public-key pinning on the domain.

## Acceptance criteria
- [ ] Release build talks to `https://wallet.intexchange.ir` only; **no** cleartext relayer traffic.
- [ ] WS connects to `wss://wallet.intexchange.ir/ws` with the JWT.
- [ ] Debug build still reaches the origin (`103.112.69.154`) / local as needed.
- [ ] `AuthInterceptor`/`IdempotencyInterceptor` host-scoping updated to `wallet.intexchange.ir`
      (they read `RELAYER_HOST`, so this follows automatically — verify).

## Testing steps
- Release build: config bundle fetch + balances + a testnet send succeed over HTTPS; capture no
  cleartext in a proxy.
- Verify JWT is attached only to `wallet.intexchange.ir` (host-scope) and not leaked to price hosts.

## Dependencies
- Server TLS live (✅ done). Precedes **TASK-11** (pinning) and satisfies the release half of **TASK-01**.

## Estimated effort
0.25 dev-day (config) + smoke test.
