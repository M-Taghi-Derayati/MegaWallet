# TASK-05 — Remove main-thread runBlocking at cold start

- **Debt:** TD-19 · **Finding:** Performance P-1 · **Severity:** 🟠 High
- **Type:** Ship-blocker (pre-release gate — StrictMode/ANR risk) · **Est. difficulty:** Low
- **Status:** OPEN

## Problem
`MainScreenViewModel.kt:33` seeds `_connectionMode = MutableStateFlow(connectionModeProvider.currentMode())`
as a **field initializer**, and `DefaultBlockchainConnectionModeProvider.currentMode()` resolves its
first (uncached) value via `runBlocking { userPreferencesRepository.getConnectionMode() }`. Because
the ViewModel is constructed on the main thread during main-screen composition, the UI thread blocks
on a DataStore/SharedPreferences disk read at cold start (StrictMode disk-read-on-main violation,
ANR-adjacent under slow storage, delays first frame). These are the only two `runBlocking` calls in
the codebase.

## Files
- `app/src/main/java/com/mtd/megawallet/viewmodel/news/MainScreenViewModel.kt` (line ~33)
- `data/src/main/java/com/mtd/data/datasource/DefaultBlockchainConnectionModeProvider.kt` (`currentMode()`)

## Proposed change
- Seed `_connectionMode` with a **default** (`BlockchainConnectionMode.DIRECT`) and update it from
  `viewModelScope.launch { _connectionMode.value = provider.awaitMode() }` (add a suspend accessor),
  **or**
- Hydrate the provider's in-memory cache once off the main thread at app start (e.g. from
  `MegaWalletApplication`'s IO warm-up) so `currentMode()` is already cached before any VM reads it.
- Remove `runBlocking` from any main-reachable path.

## Acceptance criteria
- [ ] No `runBlocking` on a main-thread-reachable path (grep clean, or both remaining calls removed).
- [ ] StrictMode (thread policy: detect disk reads on main) shows no violation opening the main screen.
- [ ] Connection-mode header still reflects the persisted value after cold start (may resolve a frame
      later — acceptable).
