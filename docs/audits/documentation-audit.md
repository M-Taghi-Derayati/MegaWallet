# Documentation Cleanup Audit (Phase 0)

Read-only classification of existing docs. **Nothing is deleted or moved** — recommendations only.

## Inventory

Markdown / doc assets found outside `build/` and `.git/`:

| Path | Type | Size | Classification | Recommendation |
|------|------|------|----------------|----------------|
| `CLAUDE.md` (repo root) | AI/dev guidance | ~4 KB | **ACTIVE** | Keep. Accurate module map, build/test commands, gotchas. Update when feature modules or the untracked auth/config/realtime subsystems land. |
| `docs/mobile.zip` | Binary archive | ~39 KB | **UPDATE REQUIRED** | Not documentation — a zipped bundle checked into `docs/`. Its contents are opaque to reviewers and untracked in git history semantics. Recommend: unzip, inspect, and either convert to real markdown docs or move out of `docs/` (e.g. an `assets/` or external store). Do **not** delete without owner confirmation. |
| `docs/audits/*`, `docs/architecture/*`, `docs/prompts/*`, `docs/tasks/*` | Newly created | — | **ACTIVE** | Created by this audit-system initialization. |

## Notable absences (gaps, not cleanup)

- **No `README.md`** at repo root or in any module. New contributors have only `CLAUDE.md`.
  Recommend adding a minimal root README (build, run, module map) — `CLAUDE.md` can remain the
  detailed source of truth.
- **No architecture decision records (ADRs)**. Several non-obvious choices (state-based
  navigation instead of Navigation-Compose, DIRECT/PROXY transport toggle, BouncyCastle pin)
  live only as inline comments. Recommend capturing these in `docs/architecture/`.
- **No CHANGELOG** and **no per-module docs**.

## Phase 7 addendum — cleanup pass (2026-07)

Re-checked after Phases 1–7. **No obsolete or duplicated reports to delete or merge.** All audit
docs are current and cross-linked; the two new pointer folders (`docs/performance/`, `docs/security/`)
intentionally index the canonical audits instead of copying them. `docs/mobile.zip` remains the only
UPDATE-REQUIRED item (unchanged recommendation). Canonical sources are now unambiguous:
- Debt → `audits/technical-debt.md` · Tasks → `tasks/master-task-register.md` ·
  Order → `roadmaps/execution-roadmap.md` · Index → `audits/master-audit.md`.

## Classification summary

- **ACTIVE:** `CLAUDE.md`, the new `docs/` tree.
- **UPDATE REQUIRED:** `docs/mobile.zip` (clarify purpose or relocate).
- **ARCHIVE:** none.
- **DELETE RECOMMENDED:** none (per audit rules, deletion is never auto-recommended for action;
  `docs/mobile.zip` is the only candidate and is deferred to owner review).
