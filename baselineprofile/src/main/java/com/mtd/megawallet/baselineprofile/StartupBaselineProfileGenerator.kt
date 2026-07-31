package com.mtd.megawallet.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.mtd.megawallet"

/**
 * PERF-10 — generates the app's Baseline Profile (AOT-compiled startup path).
 *
 * Run:  ./gradlew :app:generateBaselineProfile
 * Output is written into app/src/<variant>/generated/baselineProfiles/ and bundled automatically.
 *
 * The wallet locks/onboards on launch, so this captures the cold-start + first-frame path (the main
 * win). To also profile the wallet list + history scroll, drive past the lock here with UiAutomator
 * (device.wait(Until.hasObject(...)) + scroll gestures) — keep it deterministic (no network asserts).
 */
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait(
            Intent().apply {
                setPackage(TARGET_PACKAGE)
                component = ComponentName(
                    TARGET_PACKAGE,
                    "com.mtd.megawallet.ui.compose.WelcomeActivityCompose"
                )
            }
        )   // cold-start the launcher activity (WelcomeActivityCompose)
        exerciseAllSections()    // navigate Wallet/History/Explore + scroll each (falls back if locked)
    }
}
