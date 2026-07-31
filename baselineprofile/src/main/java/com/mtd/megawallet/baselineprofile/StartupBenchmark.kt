package com.mtd.megawallet.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.mtd.megawallet"

/**
 * PERF-10 — measures cold-start time WITHOUT the profile ([startupNone]) vs WITH it
 * ([startupBaselineProfile]) so "is the lag gone?" is a real number.
 *
 * Run:  ./gradlew :baselineprofile:pixel6Api34BenchmarkAndroidTest
 *       (or :baselineprofile:connectedBenchmarkAndroidTest on a physical device)
 * Compare the two StartupTimingMetric (timeToInitialDisplayMs) results in the test output.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupNone() = measure(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = measure(CompilationMode.Partial())
}
