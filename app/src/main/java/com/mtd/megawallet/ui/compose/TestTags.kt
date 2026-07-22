package com.mtd.megawallet.ui.compose

/**
 * Stable Compose test tags for the macrobenchmark / baseline-profile journeys (PERF-10) and future
 * UI tests. They are exposed to UiAutomator as view resource-ids because the MainScreen root sets
 * `Modifier.semantics { testTagsAsResourceId = true }`.
 *
 * Keep these strings in sync with the `:baselineprofile` journey (UiJourneys.kt), which finds nodes
 * via `By.res(...)`. Adding a tag is a semantics-only change — no visual or behavioral effect.
 */
object TestTags {
    const val MAIN_ROOT = "main_root"
    const val NAV_EXPLORE = "nav_explore"
    const val NAV_WALLET = "nav_wallet"
    const val NAV_HISTORY = "nav_history"
    const val WALLET_LIST = "wallet_asset_list"
    const val HISTORY_LIST = "history_list"
}
