# Tasks

Actionable follow-ups derived from audit findings. Tasks are created **only after** a finding is
approved for action — audits themselves never change code.

Task IDs map back to the technical-debt register (`docs/audits/technical-debt.md`, `TD-##`) and
the roadmap. Tasks are specs only — implementing them is a separate, explicitly-approved step
(audits never change code).

## Sprint 0 — implementation status (2026-07)

| Task | Status | Notes |
|------|--------|-------|
| TASK-01 NSC | ✅ Implemented | Main config now HTTPS-only + system trust; cleartext/user-CA moved to `app/src/debug/res/xml/network_security_config.xml` (debug overlay). Release close-out still needs relayer TLS (TASK-11). |
| TASK-02 FLAG_SECURE | ✅ Implemented | New `SecureFlagEffect` (ref-counted) on `SecretRevealOverlay`, `ManualBackupVerifier`, `PasscodeKeypadSheet`, `PasscodeSetupSheet`; whole onboarding in `WelcomeActivityCompose`. Receive-QR/history stay screenshot-able (scoped by design). |
| TASK-03 allowBackup | ✅ Implemented | `android:allowBackup="false"` + `tools:replace`. |
| TASK-05 runBlocking | ✅ Implemented | `currentMode()` non-blocking (cache-or-DIRECT); `prime()` hydrates off-main from app warm-up + `MainScreenViewModel.init`. |
| TASK-06 clear keys on lock | ⏸️ HELD | Trace shows app-lock unlock only flips `isLocked` and **never reloads keys** (keys load once via `WalletRepositoryImpl.unlockWallet`). Clearing on lock without wiring re-hydration into the unlock path would break signing. Needs a re-hydration hook (mirror `switchActiveWallet` secret-reload) + on-device lock→unlock→sign test. |
| TASK-04 APP_SECRET_KEY | ⏸️ HELD | Backend-coordinated (relayer validates the HMAC). |
| TASK-07 idempotency | ⏸️ HELD | Backend-coordinated (server dedup window). |

**Verification note:** edits are inspection-verified only — Gradle could not run in this environment
(`Unable to establish loopback connection`, a harness JVM/socket limitation). Run
`./gradlew :app:assembleDebug` locally to compile-verify, then the per-task on-device checks.

> **Canonical, full task list:** [master-task-register.md](master-task-register.md) — all 24
> consolidated tasks (TASK-01…TASK-24) with every required field, grouped by sprint.
> **Build order:** [../roadmaps/execution-roadmap.md](../roadmaps/execution-roadmap.md).
> The detailed files below (TASK-01…07) are the Sprint-0 gate; TASK-08+ are specced in the register.

## Open — pre-release blockers (Roadmap "Phase 0" gate)

| Task | Title | Debt | Finding | Severity | Difficulty |
|------|-------|------|---------|----------|------------|
| [TASK-01](TASK-01-network-security-config.md) | Fix NSC cleartext + user-CA trust in release | TD-34 | S-1/N-1 | 🔴 Critical | Low |
| [TASK-02](TASK-02-flag-secure.md) | Add `FLAG_SECURE` to seed/key/passcode screens | TD-36 | S-3 | 🟠 High | Low |
| [TASK-03](TASK-03-disable-allowbackup.md) | Disable/scope Android auto-backup | TD-38 | S-6 | 🟡 Medium | Low |
| [TASK-04](TASK-04-app-secret-key.md) | Remove hardcoded `APP_SECRET_KEY`; real attestation | TD-37 | S-4 | 🟠 High | Medium |
| [TASK-05](TASK-05-remove-mainthread-runblocking.md) | Remove main-thread `runBlocking` at cold start | TD-19 | P-1 | 🟠 High | Low |
| [TASK-06](TASK-06-clear-keys-on-lock.md) | Clear key cache on lock/background (**scoped**) | TD-23 | M4-1/S-7 | 🟠 High | Low |
| [TASK-07](TASK-07-stable-idempotency-key.md) | Stable per-operation idempotency key (**scoped**) | TD-31 | N-4/S-8 | 🟠 High | Low–Med |

**Elevation note (TD-23, TD-31):** both reviewed and elevated to the gate, each **scoped to the
low-effort fix**. TASK-06 covers clearing keys on lock only — the deeper auth-bound-key hardening
stays scheduled as **TD-35** (not a blocker). TASK-07 covers a stable idempotency key only; the
deeper attestation/relay work is separate.

**Suggested sequence:**
- **Land immediately (independent, low effort):** TASK-02, TASK-03, TASK-05, TASK-06.
- **Backend-coordinated:** TASK-04 (attestation), TASK-07 (server dedup window must be confirmed).
- **Blocked on relayer TLS:** TASK-01 (interim: make cleartext/user-CA `<debug-overrides>`-only).

_Created from the audit's pre-release gate. Awaiting approval to implement any of them._
