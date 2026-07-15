# docs/performance

Performance findings live in the canonical audit reports — this folder is an index to avoid
duplication.

- Static/structural performance: [../audits/performance-audit.md](../audits/performance-audit.md)
  (Phase 3 — cold-start `runBlocking`, sequential balances, O(pages²) history, config memoization).
- Runtime performance (startup/Compose/CPU under load): the Parts 1–4 sections of
  [../runtime/production-readiness-audit.md](../runtime/production-readiness-audit.md).
- Compose allocation/recomposition detail: [../audits/compose-audit.md](../audits/compose-audit.md).

Perf tasks: TASK-08, TASK-09, TASK-10 (Sprint 1) in
[../tasks/master-task-register.md](../tasks/master-task-register.md).
