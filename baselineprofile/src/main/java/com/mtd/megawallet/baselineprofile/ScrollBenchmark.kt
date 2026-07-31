package com.mtd.megawallet.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.mtd.megawallet"

/**
 * PERF-10 — measures scroll jank (FrameTimingMetric) WITHOUT the profile ([scrollNone]) vs WITH it
 * ([scrollBaselineProfile]). Compare frameDurationCpuMs P50/P90/P99 between the two runs.
 *
 * Run:  ./gradlew :baselineprofile:connectedBenchmarkAndroidTest      (your personal phone)
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = mode,
        setupBlock = {
            startActivityAndWait(
                Intent().apply {
                    component = ComponentName(
                        TARGET_PACKAGE,
                        "com.mtd.megawallet.ui.compose.WelcomeActivityCompose"
                    )
                }
            )
        },
    ) {
        exerciseAllSections()
    }

    @Test
    fun scrollNone() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial())
}
