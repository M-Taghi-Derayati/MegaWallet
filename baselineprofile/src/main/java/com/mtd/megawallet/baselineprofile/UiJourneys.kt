package com.mtd.megawallet.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

// Keep these in sync with com.mtd.megawallet.ui.compose.TestTags (:app). They are exposed to
// UiAutomator as resource-ids because the MainScreen root sets testTagsAsResourceId = true.
private const val MAIN_ROOT = "main_root"
private const val NAV_EXPLORE = "nav_explore"
private const val NAV_WALLET = "nav_wallet"
private const val NAV_HISTORY = "nav_history"
private const val WALLET_LIST = "wallet_asset_list"
private const val HISTORY_LIST = "history_list"

/**
 * Full section journey — navigates Wallet → History → Explore via the bottom-nav test tags and
 * scrolls each list, so the Baseline Profile / FrameTimingMetric cover the real, lag-prone screens.
 *
 * If the app is still at the lock / onboarding gate (e.g. a fresh install with no wallet yet), the
 * main root never appears; it then falls back to [exerciseScroll] so the run still profiles whatever
 * is reachable rather than failing.
 */
fun MacrobenchmarkScope.exerciseAllSections() {
    val reachedMain = device.wait(Until.hasObject(By.res(MAIN_ROOT)), 5_000) != null
    if (!reachedMain) {
        exerciseScroll() // locked / onboarding — profile what's on screen
        return
    }

    tapTag(NAV_WALLET)
    scrollTag(WALLET_LIST)

    tapTag(NAV_HISTORY)
    scrollTag(HISTORY_LIST)

    tapTag(NAV_EXPLORE)
    device.waitForIdle(1_000)

    tapTag(NAV_WALLET) // return to the default tab
    device.waitForIdle(1_000)
}

/** Generic, tag-free scroll usable on any screen (onboarding, or before tags exist). */
fun MacrobenchmarkScope.exerciseScroll(passes: Int = 3) {
    device.waitForIdle(2_000)
    val centerX = device.displayWidth / 2
    val top = (device.displayHeight * 0.30f).toInt()
    val bottom = (device.displayHeight * 0.75f).toInt()
    repeat(passes) {
        device.swipe(centerX, bottom, centerX, top, 12) // fling up
        device.waitForIdle(1_000)
        device.swipe(centerX, top, centerX, bottom, 12) // fling back down
        device.waitForIdle(1_000)
    }
}

private fun MacrobenchmarkScope.tapTag(tag: String) {
    device.wait(Until.hasObject(By.res(tag)), 3_000)
    device.findObject(By.res(tag))?.click()
    device.waitForIdle(1_500)
}

private fun MacrobenchmarkScope.scrollTag(tag: String, passes: Int = 3) {
    val list = device.findObject(By.res(tag))
    if (list == null) {
        exerciseScroll(passes) // tag not found on this device state — generic fallback
        return
    }
    repeat(passes) {
        list.fling(Direction.DOWN)
        device.waitForIdle(800)
        list.fling(Direction.UP)
        device.waitForIdle(800)
    }
}
