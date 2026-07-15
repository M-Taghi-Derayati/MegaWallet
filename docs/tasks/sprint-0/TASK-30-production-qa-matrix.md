# TASK-30 — Production QA manual test matrix

- **ID:** TASK-30
- **Category:** Production QA / Release gate
- **Severity:** 🟠 High (gate — the human sign-off before Beta)
- **Why before release?** Automated coverage lives only in `:data` (TD-06). The money paths,
  lifecycle survival (process death / rotation / low memory), and offline/transport behavior are
  **unverified end-to-end**. This matrix is the manual acceptance gate; run it on a release-config
  build with Crashlytics (TASK-25), StrictMode & LeakCanary (TASK-26/27) active in the debug pass.

## How to use
Run twice: (a) **debug** build with StrictMode + LeakCanary for instrumentation; (b) **signed
release** build (TASK-29) for real behavior. Record device, OS version, DIRECT/PROXY mode, result.
Any 🔴 crash or wrong-funds result is a release blocker.

## Matrix

### Wallet
| # | Case | Expected | Ties to |
|---|------|----------|---------|
| W1 | Create wallet | Seed shown (screenshot blocked — TASK-02); keys derived off-main | TASK-02 |
| W2 | Import (mnemonic + private key) | Wallet loads; addresses correct per chain | — |
| W3 | Manual backup + verify | Verifier flow; screen capture blocked | TASK-02 |
| W4 | Cloud backup + restore (password) | Encrypted upload; restore only with correct password | Phase 6 |
| W5 | Lock → unlock (passcode + biometric) | Unlock re-enables signing; capture blocked on entry | TASK-02, TASK-06† |
| W6 | Multi-wallet switch | Keys swap; no stale key/address; no leak (LeakCanary) | TASK-27 |

### Transactions
| # | Case | Expected | Ties to |
|---|------|----------|---------|
| T1 | EVM send (native + token) | Correct amount/fee; broadcast; history updates | — |
| T2 | Tron send (DIRECT + PROXY) | Fee tier honored; **PROXY feeLevel mapped** | code-review fix |
| T3 | Gasless / sponsor-approve | Sponsored; no double-fund on retry | TASK-07 |
| T4 | Failed tx + retry | No double-broadcast/double-fund; clear error | TASK-07 |
| T5 | Base/L2 fee | L1+L2 fee correct (not under-funded) | code-review fix |
| T6 | Multi-asset balances/prices | Correct USD values (fallback path) | code-review fix |

### Lifecycle
| # | Case | Expected | Ties to |
|---|------|----------|---------|
| L1 | Background → foreground | State intact; keys handling per TASK-06† | TASK-06† |
| L2 | Process death (`Don't keep activities` + `am kill`) → restore | Lands on same screen; no crash; critical flow state kept | TD-41 |
| L3 | Rotation / config change | No state loss; no leak | TASK-27 |
| L4 | Low-memory kill during send | Graceful; no partial/duplicate send | TD-41, TASK-07 |
| L5 | Cold/warm/hot start | No ANR; StrictMode clean | TASK-05, TASK-26, TASK-28 |

### Network
| # | Case | Expected | Ties to |
|---|------|----------|---------|
| N1 | Offline | Fail-fast with clear error; no hang | Phase 5/8 |
| N2 | Slow/flaky network | Bounded timeouts; retry affordance | Phase 5 |
| N3 | Reconnect re-sync | Balances/history refresh on online transition | TD-42/46 |
| N4 | DIRECT vs PROXY parity | Same result both transports | TD-05 |
| N5 | Notifications (A13+) | Permission requested; events delivered | TD-43 |

### Compatibility (spot-check)
| # | Case | Expected | Ties to |
|---|------|----------|---------|
| C1 | Dark mode | Readable; no hardcoded-color breakage | TD-15 |
| C2 | Large font (2.0) | No clipping on key screens | TD-45 |
| C3 | Tablet / foldable | Usable layout | TD-45 |
| C4 | Android 10/13/14/15 | No version-specific crash | — |

† **TASK-06 is HELD** (clear-keys-on-lock needs unlock re-hydration + test). Until it lands, W5/L1
verify current behavior (keys retained across lock) and re-run after TASK-06 ships.

## Acceptance criteria
- [ ] Matrix executed on ≥2 devices (one low-RAM, one recent) in debug + release passes.
- [ ] Zero 🔴 crashes / wrong-funds outcomes; all deviations filed against a TD/TASK.
- [ ] Results archived with build hash, device, OS, mode.

## Dependencies
- TASK-25/26/27 (instrumentation), TASK-29 (release build). Correctness fixes (code-review cluster)
  for T2/T5/T6 to pass cleanly.

## Estimated effort
1–2 dev-days per full pass.
