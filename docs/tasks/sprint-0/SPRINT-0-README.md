# Sprint 0 — Release Readiness Hardening

## Goal
Make the MegaWallet Android app **safe, stable, and measurable** before Beta/Production — not by
refactoring or adding features, but by closing security gate items, adding crash/leak/ANR safety
nets, establishing a performance baseline, verifying the release build, and defining the manual QA
gate.

## Scope (what Sprint 0 IS)
- Release-security gate closure (NSC, FLAG_SECURE, backup, secrets/attestation).
- Crash & stability observability (crash reporting; global exception safety net).
- ANR / main-thread safety verification (StrictMode).
- Memory-leak detection tooling (LeakCanary) + verification of known leak-risk items.
- Performance **measurement foundation** (Baseline Profile + Macrobenchmark) — measure, don't optimize.
- Release build verification (R8/reflection safety, no secrets, logging, debug-dep isolation).
- Production QA manual test matrix.

## Non-goals (explicitly NOT Sprint 0)
- ❌ Architecture refactoring (domain purity, god-file decomposition, package-by-feature) → Sprint 2.
- ❌ Performance **optimization** (fonts, lifecycle collection, balances) → Sprint 1 (after baseline).
- ❌ Compose UX cleanup (colors, a11y, previews) → Sprint 3.
- ❌ Deep crypto hardening (auth-bound Keystore key = TD-35) → Sprint 4.
- ❌ Feature work of any kind.

## Priority order
1. **Security gate** (done/in-progress): TASK-01, 02, 03, 05 ✅ · TASK-04, 06, 07 pending.
2. **Observability & safety nets:** TASK-25 (crash), TASK-26 (StrictMode), TASK-27 (LeakCanary).
3. **Release verification:** TASK-29.
4. **Measurement foundation:** TASK-28.
5. **QA gate:** TASK-30 (runs last, gates Beta).

## Dependencies (cross-task)
- TASK-26 after TASK-05 (main-thread `runBlocking` removed).
- TASK-29 needs TASK-04 (no-secrets criterion) + TASK-25 (mapping upload).
- TASK-30 needs TASK-25/26/27 (instrumentation) + TASK-29 (release build) + the code-review
  correctness cluster for the money-path cases.
- TASK-01 release close-out blocked on relayer TLS (TASK-11, Sprint 4).

---

## Sprint 0 scope audit (categories 1–7)

Format: **Finding · Severity · Risk (why before release) · Fix → Task**. Items already fixed are
marked ✅ and are **not** re-created.

### 1. Release Security
| Finding | Sev | Risk | Task |
|---|---|---|---|
| NSC permitted cleartext + user-CA trust in release | 🔴 | MITM of signed tx + JWT | **TASK-01 ✅** (release TLS close-out → TASK-11) |
| Seed/key/passcode screens capturable | 🟠 | Seed exfiltration via screenshot/recents | **TASK-02 ✅** |
| `allowBackup=true`, empty rules | 🟡 | Prefs backed up in cleartext | **TASK-03 ✅** |
| Hardcoded `APP_SECRET_KEY` | 🟠 | Attestation forgeable from APK | **TASK-04** (backend) |
| No TLS cert pinning | 🟠 | CA-compromise MITM | TASK-11 (Sprint 4) |
| Exported components / deep links / permissions | 🟢 | — | **No action:** launcher `exported=true` only; `MainActivity exported=false`; no deep links/intent-filters beyond LAUNCHER; perms minimal (INTERNET, NETWORK_STATE, POST_NOTIFICATIONS). Verified clean. |

### 2. Crash & Stability Readiness
| Finding | Sev | Risk | Task |
|---|---|---|---|
| No crash/exception reporting | 🟠 | Blind to production crashes in money path | **TASK-25** |
| Global coroutine/uncaught handling partial (3 sites) | 🟡 | Silent coroutine failures | fold into **TASK-25** |
| Weak process-death restoration | 🟠 | Lost in-flight send/import on kill | **TD-41** (verify in TASK-30; fix = TASK-15, Sprint 3) |
| Wallet-flow crash safety (create/import/unlock/sign/gasless/send) | 🟠 | Unverified end-to-end | **TASK-30** matrix |

### 3. ANR & Main-Thread Safety
| Finding | Sev | Risk | Task |
|---|---|---|---|
| Main-thread `runBlocking` at cold start | 🟠 | ANR / delayed first frame | **TASK-05 ✅** |
| No StrictMode tripwire | 🟡 | Next disk/net-on-main regression ships silently | **TASK-26** |
| Crypto/derivation/encryption on main? | 🟡 | Potential jank | **Verified:** key derivation runs via `WalletRepositoryImpl` suspend paths; SharedPreferences reads are the only main hits → StrictMode (TASK-26) will confirm |

### 4. Memory Leak Protection
| Finding | Sev | Risk | Task |
|---|---|---|---|
| No LeakCanary | 🟡 | Leaks found late as OOM | **TASK-27** |
| Keys retained across lock | 🟠 | Sensitive-material lifetime | **TASK-06** (held) |
| Process-static Web3j clients never shutdown | 🟢 | Bounded resource retention | TD-26 (Sprint 4 TASK-20) |
| Session coordinator scope / Context leaks | 🟢 | Verify @Singleton | TD-27; **Context leaks: none** (all `@ApplicationContext`, Phase 4) |

### 5. Performance Baseline (measure only)
| Finding | Sev | Risk | Task |
|---|---|---|---|
| No Baseline Profile / Macrobenchmark / startup benchmark | 🟡 | Can't measure or prevent regressions; slower cold start | **TASK-28** |

### 6. Release Build Verification
| Finding | Sev | Risk | Task |
|---|---|---|---|
| Signed release R8/reflection un-smoke-tested (web3j/bitcoinj/Gson) | 🟠 | Release-only crashes in signing path | **TASK-29** |
| Module consumer ProGuard rules commented out | 🟡 | Keeps may not propagate | **TASK-29** |
| Release logging to Logcat; secrets in BuildConfig | 🟢/🟠 | Info leak / extractable secret | **TASK-29** (+ TD-33, TASK-04) |
| `jcenter()` + insecure repos | 🟡 | Supply-chain | **TASK-29** (folds TD-09) |

### 7. Production QA
| Finding | Sev | Risk | Task |
|---|---|---|---|
| No end-to-end manual gate for money/lifecycle/network flows | 🟠 | Unverified release | **TASK-30** |

---

## Task index (Sprint 0)

**Security gate** (specs in `docs/tasks/TASK-01…07`):
- TASK-01 NSC ✅ · TASK-02 FLAG_SECURE ✅ · TASK-03 allowBackup ✅ · TASK-05 runBlocking ✅
- TASK-04 APP_SECRET_KEY ⏸ (backend) · TASK-06 clear-keys-on-lock ⏸ (needs re-hydration + test) ·
  TASK-07 idempotency ⏸ (backend)

**Release-readiness hardening** (specs in this folder):
- [TASK-25](TASK-25-crash-reporting.md) crash reporting 🟠
- [TASK-26](TASK-26-strictmode-debug.md) StrictMode (debug) 🟡 — implements TD-44
- [TASK-27](TASK-27-leakcanary-debug.md) LeakCanary (debug) 🟡
- [TASK-28](TASK-28-performance-baseline-infra.md) Baseline Profile + Macrobenchmark 🟡
- [TASK-29](TASK-29-release-build-verification.md) release build verification 🟠
- [TASK-30](TASK-30-production-qa-matrix.md) production QA matrix 🟠

Canonical register: [../master-task-register.md](../master-task-register.md). Order:
[../../roadmaps/execution-roadmap.md](../../roadmaps/execution-roadmap.md).
