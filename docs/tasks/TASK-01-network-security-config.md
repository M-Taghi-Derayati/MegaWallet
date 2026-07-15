# TASK-01 — Fix network-security config (cleartext + user-CA trust in release)

- **Debt:** TD-34 · **Finding:** Security S-1 / Networking N-1 · **Severity:** 🔴 Critical
- **Type:** Ship-blocker (pre-release security gate) · **Est. difficulty:** Low (config)
- **Status:** OPEN

## Problem
`app/src/main/res/xml/network_security_config.xml` uses a plain `<base-config>` (no
`<debug-overrides>`) with `cleartextTrafficPermitted="true"` and `<certificates src="user"/>`.
`base-config` applies to **all** build types, so **release** builds permit cleartext HTTP app-wide
and trust user-installed CAs → MITM of relayed signed transactions and the session JWT. The inline
comment claiming "debug-only" is false. The `<domain-config>` dev-IP allowlist is also stale
(excludes the actual relayer `192.168.90.153`).

## Files
- `app/src/main/res/xml/network_security_config.xml`
- (context) `app/src/main/AndroidManifest.xml` (`android:networkSecurityConfig`)

## Proposed change
- Wrap cleartext + `src="user"` trust in `<debug-overrides>` so it applies to debug only.
- Release `<base-config>`: `cleartextTrafficPermitted="false"`, system trust anchors only.
- Scope any needed dev-host cleartext to the real host under `<debug-overrides>`; drop the stale IP list.

## Acceptance criteria
- [ ] Release build (`assembleRelease`) **rejects** cleartext HTTP and does **not** trust user CAs.
- [ ] Debug build still reaches the local relayer.
- [ ] No `src="user"` trust anchor outside `<debug-overrides>`.
- [ ] Coordinate with TASK-05 (relayer must be HTTPS before release cleartext is removed) and the
      future TLS-pinning task (TD-29).

## Notes
Depends on the relayer supporting TLS for release (see Networking N-3 / TD-30). Until then this can
land as debug-scoped cleartext so release is HTTPS-only by construction.
