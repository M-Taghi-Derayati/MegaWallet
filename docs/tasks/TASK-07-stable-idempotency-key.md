# TASK-07 — Stable per-operation idempotency key for fund endpoints

- **Debt:** TD-31 · **Finding:** Networking N-4 / Security S-8 · **Severity:** 🟠 High (fund safety)
- **Type:** Ship-blocker (pre-release gate — **scoped**, backend-coordinated)
- **Est. difficulty:** Low–Medium · **Status:** OPEN

## Review outcome (why elevated, with nuance)
`IdempotencyInterceptor` mints a fresh `UUID.randomUUID()` per **physical** request. Verified nuances:
- OkHttp connection-level retries (`retryOnConnectionFailure`) reuse the same request → **same key**
  (safe).
- `withBoundedRequote` **intentionally** uses a new key on a 409 `RequoteRequired` because that is a
  genuinely new operation (new prepareToken/nonce/treasury) — **correct**, leave as-is.
- The real gap: a **client/user resubmit after an ambiguous failure** (a `/relay`, `/broadcast`, or
  `/sponsor-approve` POST that timed out but may have been processed server-side) carries a **new**
  key, so the relayer cannot dedupe — the exact "must not double-fund on retry" case the interceptor
  documents.

Partial mitigation: EVM/UTXO **broadcast** is nonce/UTXO-idempotent at the chain level, so plain-send
double-spend is limited. The sharp edge is **`sponsor-approve` (gas double-fund)** and gasless relay.
For a money-moving product, shipping a fund endpoint whose documented safety property is unmet is not
acceptable for production → elevated to the gate, scoped to a stable key.

## Files
- `data/src/main/java/com/mtd/data/network/interceptor/IdempotencyInterceptor.kt`
- Fund call sites: `repository/gasless/GaslessApiGateway.kt`, `EvmGaslessCoordinator.kt`,
  `TronGaslessCoordinator.kt`, `ProxyChainDataSource.kt` (broadcast), `MobileProxyApiService.kt`,
  `GaslessApiService.kt`

## Proposed change
Make the idempotency key **stable for a logical operation** so all resubmits of the same intent carry
one key:
- Caller-supplied key: the coordinator generates one key per send/relay/sponsor-approve intent and
  passes it (e.g. a header set on the request or a param the interceptor honors), instead of the
  interceptor minting a random one. Keep the interceptor's random UUID only as a fallback for calls
  that don't supply one.
- Preserve current-correct behavior: a **new** intent (including a 409 requote) gets a **new** key.
- Confirm the relayer's idempotency **dedup window** covers the client resubmit horizon.

## Acceptance criteria
- [ ] Two submissions of the **same** send/relay/sponsor-approve intent (e.g. user taps retry after a
      timeout) carry the **same** `X-Idempotency-Key`.
- [ ] A distinct intent (and a 409 requote) still gets a distinct key.
- [ ] Existing tests updated/added (`IdempotencyInterceptorTest`, gasless coordinator tests) to assert
      key stability across resubmit and freshness across requote.
- [ ] Backend owner confirms server-side dedup semantics + window for `/relay`, `/transactions/broadcast`,
      `/sponsor-approve`.

## Notes
Backend coordination required (server must dedupe on the key within a sufficient window). If the
backend can't guarantee dedup by the pre-release date, gate the risk another way (e.g. disable
client/user resubmit on those endpoints) and downgrade to fast-follow.
