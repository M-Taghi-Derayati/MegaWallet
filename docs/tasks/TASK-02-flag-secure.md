# TASK-02 — Add FLAG_SECURE to seed / private-key / passcode screens

- **Debt:** TD-36 · **Finding:** Security S-3 · **Severity:** 🟠 High
- **Type:** Ship-blocker (pre-release security gate) · **Est. difficulty:** Low
- **Status:** OPEN

## Problem
`FLAG_SECURE` is set **nowhere** in the app. Screens that render the recovery phrase, private keys,
or the passcode can be screenshotted, screen-recorded, and appear in the app-switcher (recents)
thumbnail — a classic seed-exfiltration path.

## Files (screens/flows to protect)
- Onboarding create/import (seed display + entry): `ui/compose/screens/createwallet/*`,
  `ui/compose/screens/addexistingwallet/*` (incl. `manualimport/`)
- Backup verification: `ui/compose/screens/wallet/ManualBackupVerifier.kt`
- Security/passcode: `ui/compose/screens/security/*` (passcode setup/keypad)
- Host activities: `ui/compose/WelcomeActivityCompose.kt`, `ui/compose/MainActivityCompose.kt`

## Proposed change
Add `FLAG_SECURE` while a sensitive screen is shown and clear it on exit. Options:
- Per-window: `window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)` /
  `clearFlags(...)` from a `DisposableEffect` in the sensitive Composable, or
- A small `SecureScreen { }` Compose wrapper that sets/clears the flag on enter/leave.

Prefer the scoped wrapper so only sensitive screens are protected (blanket FLAG_SECURE on the whole
app breaks legitimate screenshots elsewhere).

## Acceptance criteria
- [ ] Screenshot/screen-record is blocked (black frame) on seed display, seed entry, backup verify,
      and passcode screens.
- [ ] Recents thumbnail is blanked while those screens are foreground.
- [ ] Flag is cleared when leaving the sensitive screen (non-sensitive screens still allow screenshots).
- [ ] Manual test on a device: attempt screenshot on each listed screen.
