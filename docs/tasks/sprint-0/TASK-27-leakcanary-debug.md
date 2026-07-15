# TASK-27 — LeakCanary in debug builds

- **ID:** TASK-27
- **Category:** Memory Leak Protection / Dev-infra
- **Severity:** 🟡 Medium
- **Why before release?** Phase 4 flagged concrete leak-risk candidates (keys retained across lock,
  process-static Web3j clients, session-coordinator scope). LeakCanary turns those hypotheses into
  observed facts during QA so real leaks are caught before Beta rather than as slow OOMs in the field.

## Problem
No automated leak detection. Leak-risk items (TD-23, TD-26, TD-27) are currently only reasoned about.

## Evidence
- `grep leakcanary` → 0. Phase 4 memory audit lists retained-reference candidates.

## Files involved
- `app/build.gradle.kts` — `debugImplementation(libs.leakcanary.android)`
- `gradle/libs.versions.toml` — add the dependency.
- (no production code; LeakCanary auto-installs in debug)

## Proposed solution
- Add LeakCanary as a **debugImplementation** dependency only (never in release).
- Run the QA matrix (TASK-30) under a LeakCanary debug build; capture and triage any leak traces,
  cross-referencing TD-23/26/27.

## Acceptance criteria
- [ ] LeakCanary present in debug APK, **absent** in release APK (verify via dependency report).
- [ ] Core flows (create/import/unlock/switch wallet, send, background/foreground, rotate) produce
      **no** leaks, or each leak is filed against a TD item.
- [ ] Activity/ViewModel/coroutine-scope retention around wallet switching is clean.

## Testing steps
- Build debug; exercise wallet-switch, lock/unlock, send, rotation, background/foreground; watch for
  LeakCanary notifications; export traces for any hit.

## Dependencies
- None to install; most valuable run alongside TASK-30 (QA matrix).

## Estimated effort
0.25 dev-day (install) + triage time during QA.
