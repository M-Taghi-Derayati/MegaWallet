# docs/security

Security findings live in the canonical audit reports — this folder is an index to avoid duplication.

- Security audit (keys, storage, backup, screen exposure, secrets): [../audits/security-audit.md](../audits/security-audit.md) (Phase 6).
- Transport security (cleartext/TLS/pinning/interceptors): [../audits/networking-audit.md](../audits/networking-audit.md) (Phase 5).
- Key-material lifetime: [../audits/memory-cpu-audit.md](../audits/memory-cpu-audit.md) (Phase 4, M4-1).

**Release-blocking security tasks:** TASK-01 (NSC), TASK-02 (`FLAG_SECURE`), TASK-03 (backup),
TASK-04 (`APP_SECRET_KEY`), TASK-06 (clear keys on lock), TASK-07 (idempotency). Post-gate: TASK-11
(TLS+pinning), TASK-19 (auth-bound key). See [../tasks/README.md](../tasks/README.md).
