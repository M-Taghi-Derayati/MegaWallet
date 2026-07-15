# TASK-03 — Disable/scope Android auto-backup for wallet data

- **Debt:** TD-38 · **Finding:** Security S-6 · **Severity:** 🟡 Medium (ship-blocker per gate)
- **Type:** Pre-release security gate · **Est. difficulty:** Low
- **Status:** OPEN

## Problem
`AndroidManifest.xml` sets `android:allowBackup="true"`, and both `backup_rules.xml` and
`data_extraction_rules.xml` are empty templates. So default auto-backup / `adb backup` includes
**all** app storage. The Keystore-encrypted secrets in `secure_prefs` stay safe (the AES key is
non-exportable and not backed up), but any **non-Keystore** prefs/DataStore (addresses, cached
balances, connection mode, lock state) are backed up in cleartext — a privacy leak and a confusing
restore surface for a wallet.

## Files
- `app/src/main/AndroidManifest.xml` (`android:allowBackup`)
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

## Proposed change
- Preferred: set `android:allowBackup="false"` (recommended for non-custodial wallets).
- If backup must stay on: explicitly `<exclude>` `secure_prefs` and every sensitive
  SharedPreferences/DataStore file in both `backup_rules.xml` (fullBackupContent) and
  `data_extraction_rules.xml` (cloud + device transfer).

## Acceptance criteria
- [ ] `adb backup` / auto-backup no longer captures `secure_prefs` or other sensitive stores.
- [ ] Decision documented (full disable vs. scoped exclusion) with rationale.
- [ ] App still launches cleanly on a fresh install (no reliance on restored state).
