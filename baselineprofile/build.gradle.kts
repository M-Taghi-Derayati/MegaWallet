plugins {
    alias(libs.plugins.android.test)
}

// PERF-10 — Baseline Profile generator module. Mirrors :app (AGP built-in Kotlin, JVM 17).
android {
    namespace = "com.mtd.megawallet.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28 // Macrobenchmark / baseline-profile generation requires API 28+
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        // Maps to :app's non-debuggable "benchmark" variant (same name, with a release fallback) so
        // the app under test isn't debuggable. The test apk itself stays debug-signed.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Macrobenchmark / BaselineProfileRule run in a separate process against the target app.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // The app under test.
    targetProjectPath = ":app"

    // A Gradle Managed Device so you don't need a rooted/AOSP physical phone — Gradle downloads the
    // image and spins it up/down automatically. (Image download happens on first run.)
    testOptions {
        managedDevices {
            allDevices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "aosp" // AOSP image => rooted, required for benchmarks
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
