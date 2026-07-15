# Technical Debt Register

Rolling register aggregated across audit phases. Seeded from Phase 1 (architecture). Later
phases append their debt items here rather than duplicating detail.

| ID | Area | Item | Severity | Source phase |
|----|------|------|----------|--------------|
| TD-01 | Architecture | God files (`SendConfirmScreen` 1977, `SendScreen` 1472, `MainScreen` 1152, `SendViewModel` 1137, `TransactionHistoryViewModel` 1017 LOC) | 🔴 Critical | 1 |
| TD-02 | Architecture | Domain module depends on Android/Hilt (`androidx.core.ktx`, `material`, `dagger.hilt`) — not framework-free | 🟠 High | 1 |
| TD-03 | Architecture | Layer-first packaging + catch-all `viewmodel/news` package; no package-by-feature | 🟠 High | 1 |
| TD-04 | Architecture | Business logic (formatting, tier mapping, normalization) living in ViewModels/Composables instead of domain use cases | 🟠 High | 1 |
| TD-05 | Architecture | Transport-mode (DIRECT/PROXY) decision duplicated across `ChainDataSourceFactory` and `UnifiedTransferCoordinator` | 🟡 Medium | 1 |
| TD-06 | Testing | Real test coverage only in `:data`; `:app`, `:domain`, `:core` have placeholder `ExampleUnitTest` — ViewModels & use cases largely untested | 🟠 High | 1 |
| TD-07 | Navigation | Hand-rolled state-based navigation concentrated in `MainScreen`; no typed nav abstraction; overlay z-index bugs class | 🟠 High | 1 |
| TD-08 | Build/Security | Hardcoded `APP_SECRET_KEY` and plaintext relayer IP in `:data` BuildConfig | 🟠 High | 1 (→ 6) |
| TD-09 | Build | `jcenter()` + `isAllowInsecureProtocol=true` in `settings.gradle.kts` | 🟡 Medium | 1 |
| TD-10 | Consistency | Overlapping `MainViewModel` vs `MainScreenViewModel`; unclear responsibility split | 🟡 Medium | 1 |
| TD-11 | Docs | `docs/mobile.zip` opaque binary in docs tree; no README/ADRs | 🟢 Low | 0 |
| TD-12 | Compose | 185 inline `FontFamily(Font(...))` allocations; custom fonts not wired into `Typography` (uses `FontFamily.Default`) | 🔴 Critical | 2 |
| TD-13 | Compose | No lifecycle-aware collection — 45 `collectAsState()`, 0 `collectAsStateWithLifecycle` | 🟠 High | 2 |
| TD-14 | Compose | Formatting/business calls executed in composition (31 `viewModel.format*/get*`, many unremembered) | 🟠 High | 2 |
| TD-15 | Compose | 135 hardcoded `Color(0xFF…)` literals + scattered `isSystemInDarkTheme()` bypass theme | 🟠 High | 2 |
| TD-16 | Compose | Accessibility: 70 `contentDescription = null` on meaningful icons | 🟡 Medium | 2 |
| TD-17 | Compose | Low preview coverage: 17 `@Preview` / 223 composables (~8%) | 🟡 Medium | 2 |
| TD-18 | Compose | `SendConfirmScreen` fee tabs bound to hardcoded placeholder data; commented-out dead code | 🟡 Medium | 2 |

| TD-19 | Performance | Main-thread `runBlocking` prefs read in `MainScreenViewModel` field init (`currentMode()`) | 🟠 High | 3 |
| TD-20 | Performance | `ProxyChainDataSource.getBalancesForMultipleAddresses` sequential (N×RTT); DIRECT sources parallelize | 🟡 Medium | 3 |
| TD-21 | Performance | `applyUnifiedPage` re-normalizes whole history list per page → O(pages²) | 🟡 Medium | 3 |
| TD-22 | Performance | `ConfigManager.getValidatedConfig()` no in-memory memoization (latent; single caller today) | 🟢 Low | 3 |

_Note: Phase-3 P-2 (UI recomposition/allocation runtime cost) is tracked under TD-12/TD-13._

| TD-23 | Memory/Security | Decrypted keys (`KeyManager.credentialsCache`) not cleared on app-lock — only on wallet delete/switch | 🟠 High | 4 (→ 6) · **ELEVATED → gate (TASK-06, scoped: clear-on-lock; deep fix = TD-35)** |
| TD-24 | Memory | `CacheManager.ASSETS_TTL` ≈ 13,700 years (unit error ×10⁶) → assets never expire in memory/disk | 🟡 Medium | 4 |
| TD-25 | Memory | `CacheManager.memoryCache` no size cap; lazy-only eviction, no scheduled `clearExpired()` | 🟢 Low | 4 |
| TD-26 | Memory | Process-static Web3j/HttpService caches never `shutdown()` (bounded by RPC-URL count) | 🟢 Low | 4 |
| TD-27 | Memory | Verify `WalletSessionAuthCoordinator` is `@Singleton` / `start()` idempotent (Activity-recreation duplicate jobs) | 🟢 Low | 4 |

| TD-28 | Networking/Security | `network_security_config.xml` `base-config` (not `debug-overrides`) permits cleartext + trusts user CAs in **release** | 🔴 Critical | 5 (→ 6) |
| TD-29 | Networking/Security | No TLS certificate pinning for the relayer | 🟠 High | 5 (→ 6) |
| TD-30 | Networking/Security | Relayer endpoints plaintext `http://`/`ws://` via BuildConfig | 🟠 High | 5 (→ 6) |
| TD-31 | Networking | Idempotency key regenerated (`UUID`) per attempt → client resubmit after ambiguous failure defeats double-fund protection | 🟠 High | 5 · **ELEVATED → gate (TASK-07, scoped: stable key; backend-coordinated)** |
| TD-32 | Networking | Stale NSC dev-IP allowlist (excludes actual relayer IP) | 🟢 Low | 5 |
| TD-33 | Networking | Debug BODY logging includes signed payloads/tokens; release logs request lines to Logcat | 🟢 Low | 5 |

_DIRECT/PROXY parity (Tron feeLevel, multi-asset price param) and socket reconnect are tracked via the Phase-1 code review._

| TD-34 | Security | Release cleartext + user-CA trust (NSC) → MITM of signed tx + JWT | 🔴 Critical | 6 (=N-1) |
| TD-35 | Security | Keystore master key not bound to user auth (`setUserAuthenticationRequired` unset) → app-lock is UI-only | 🟠 High | 6 |
| TD-36 | Security | No `FLAG_SECURE` → seed/private-key/passcode screens screenshot/record/recents-capturable | 🟠 High | 6 |
| TD-37 | Security | ~~Hardcoded weak `APP_SECRET_KEY` → forgeable attestation~~ **DOWNGRADED**: backend confirms `APP_SECRET_KEY` is **dead code** (never validated server-side, never read client-side). ✅ removed; secret externalized as `DEVICE_ATTEST_HMAC_SECRET`; `HmacUtils` corrected to server's 2-level scheme. Attestation is soft (gates only gas-credit + Mystery Box). | 🟢 Low (was 🟠 High) | 6 (→ TASK-04) |
| TD-38 | Security | `allowBackup="true"` with empty backup/extraction rules → non-Keystore prefs backed up in cleartext | 🟡 Medium | 6 |
| TD-39 | Security | PBKDF2 iterations (65,536) below current guidance; derived AES key not zeroed | 🟢 Low | 6 |
| TD-40 | Security | Crypto dependency CVE hygiene (BC 1.73 pin, web3j/bitcoinj) — periodic scan | 🟢 Low | 6 |

_S-5 (no TLS pinning) = TD-29; S-7 (keys not cleared on lock) = TD-23; S-8 (idempotency) = TD-31; S-10 (insecure repos) = TD-09._

**All six audit phases complete.** Register total: 40 items (TD-01…TD-40).

### Phase 7 — Production readiness (runtime) additions

| TD | Area | Item | Severity | Source |
|----|------|------|----------|--------|
| TD-41 | Runtime/Reliability | Weak process-death restoration — state-based nav + only 10 `rememberSaveable`/2 `SavedStateHandle`; in-flight flows & nav state lost on low-memory kill | 🟠 High | 7 (PR-1) |
| TD-42 | Realtime | `MutableSharedFlow(replay=1)` socket/event bus with no buffer/overflow policy → burst or late-subscriber event loss | 🟡 Medium | 7 (PR-2) |
| TD-43 | Runtime/Notifications | `POST_NOTIFICATIONS` declared + checked but not requested at runtime → notifications suppressed on Android 13+ | 🟡 Medium | 7 (PR-3) |
| TD-44 | Dev-infra | No `StrictMode` in debug → main-thread I/O (P-1) went unflagged | 🟢 Low | 7 (PR-4) |
| TD-45 | UX/Compat | Large-font & tablet/foldable layouts unvalidated; fixed dims in god-composables | 🟡 Medium | 7 (PR-5) |
| TD-46 | Realtime/Offline | No confirmed balances/history **re-sync on reconnect/online** transition (compounds TD-42) | 🟡 Medium | 7 (PR-2/8) |

_PR-6 (predictive-back with custom nav) tracked as a verification item under TD-45's testing scope._

**Register total: 46 items (TD-01…TD-46).**

### Sprint 0 — Release readiness infrastructure gaps (verified absent)

| TD | Area | Item | Severity | Task |
|----|------|------|----------|------|
| TD-47 | Observability | No crash/exception reporting (Crashlytics/Sentry absent; only `firebase-auth` present) | 🟠 High | TASK-25 |
| TD-48 | Dev-infra/Memory | No LeakCanary; leak-risk items (TD-23/26/27) only reasoned, not observed | 🟡 Medium | TASK-27 |
| TD-49 | Perf-infra | No Baseline Profile / Macrobenchmark / startup benchmark (no measurement foundation) | 🟡 Medium | TASK-28 |

_Release build verification (TASK-29) folds TD-09 + TD-33; StrictMode (TASK-26) implements TD-44._

**Register total: 49 items (TD-01…TD-49).**
