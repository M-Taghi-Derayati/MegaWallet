# Audit Prompts

Per-phase prompts for the MegaWallet audit system. Each phase is **read-only** and produces a
report under `docs/audits/`. Run one phase at a time; **wait for approval** before the next.

## Shared rules (apply to every phase)
- Never modify, refactor, patch, or delete production code.
- Minimize tokens: reuse facts from `docs/architecture/overview.md`, `CLAUDE.md`, and prior
  reports. Do not reopen files already analyzed unless a finding requires confirmation.
- Every finding uses: **Severity · Impact · Reason · Suggested Solution · Estimated Difficulty**.
- Severity: 🔴 Critical · 🟠 High · 🟡 Medium · 🟢 Low.

## Phase prompts (fill in when each phase is approved)
- Phase 2 — Compose/UI → `docs/audits/compose-audit.md` (leads pre-seeded)
- Phase 3 — Performance → `docs/audits/performance-audit.md`
- Phase 4 — Memory & CPU → `docs/audits/memory-cpu-audit.md`
- Phase 5 — Networking → `docs/audits/networking-audit.md`
- Phase 6 — Security → `docs/audits/security-audit.md`

Each report already lists **pre-seeded leads** from Phase 1 so the phase starts from evidence
rather than a cold scan.
