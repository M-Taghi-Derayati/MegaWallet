# Baseline Profile + Macrobenchmark setup (PERF-10)

**Why:** a Baseline Profile AOT-compiles the startup + hot scroll paths, so the first launch and the
first scrolls of Wallet/History render without JIT/interpreter jank. It's usually the **single
biggest real-world lag/startup win** — and unlike the compiler stability work (which cut *potential*
recomposition), this cuts *actual* frame time. Macrobenchmark also gives you real before/after
numbers (startup ms, jank %) so "is the lag gone?" becomes measurable.

**Why this is a guide, not a commit:** it needs a new Gradle module wired into `settings.gradle.kts`
+ `app/build.gradle.kts` + `libs.versions.toml` (all currently your uncommitted WIP), and it must be
**generated on a device/emulator** — neither a Gradle run nor a device is available in the agent
env. Apply the snippets below yourself, then run the commands.

Project facts used: `applicationId = com.mtd.megawallet`, `minSdk 26`, AGP `9.2.1`, modules
`:app :common_ui :core :data :domain`.

---

## 1. `gradle/libs.versions.toml`

```toml
[versions]
# … existing …
benchmark = "1.3.4"                # androidx.benchmark macro
baselineprofile = "1.3.4"         # androidx.baselineprofile gradle plugin
profileinstaller = "1.4.1"
uiautomator = "2.3.0"

[libraries]
benchmark-macro-junit4 = { group = "androidx.benchmark", name = "benchmark-macro-junit4", version.ref = "benchmark" }
profileinstaller = { group = "androidx.profileinstaller", name = "profileinstaller", version.ref = "profileinstaller" }
uiautomator = { group = "androidx.test.uiautomator", name = "uiautomator", version.ref = "uiautomator" }
# (junit / androidx-junit / espresso already exist in your catalog)

[plugins]
android-test = { id = "com.android.test", version.ref = "agp" }
androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "baselineprofile" }
```

## 2. `settings.gradle.kts`

```kotlin
include(":baselineprofile")
```

## 3. `app/build.gradle.kts`

```kotlin
plugins {
    // … existing …
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    // A non-debuggable, non-minified build type the generator/benchmark runs against.
    // Copy signingConfig/matchingFallbacks from release as needed.
    buildTypes {
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug") // so it installs without release keys
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

dependencies {
    // … existing …
    implementation(libs.profileinstaller)          // lets the app load the generated profile at runtime
    baselineProfile(project(":baselineprofile"))    // consume the generated profile
}
```

## 4. New module `:baselineprofile`

`baselineprofile/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)             // your existing kotlin-android alias
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.mtd.megawallet.baselineprofile"
    compileSdk = 36
    defaultConfig {
        minSdk = 28                                 // Macrobenchmark requires minSdk 28+
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    targetProjectPath = ":app"
    // Use a Gradle Managed Device if you don't have a rooted/AOSP physical device (see §6).
}

baselineProfile {
    useConnectedDevices = true                      // or configure managedDevices below
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
```

`baselineprofile/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

`baselineprofile/src/main/java/com/mtd/megawallet/baselineprofile/StartupBaselineProfileGenerator.kt`:
```kotlin
package com.mtd.megawallet.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET = "com.mtd.megawallet"

@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET) {
        pressHome()
        startActivityAndWait()          // WelcomeActivity/MainActivity cold start
        // The wallet app locks on launch; if a passcode/onboarding gate blocks the main UI, the
        // profile still captures the launch + gate. To also capture the wallet list + history scroll,
        // drive past the lock here with device.wait(Until.hasObject(...)) + scroll gestures on the
        // Wallet list and the History tab. Keep it deterministic (no network-dependent asserts).
    }
}
```

*(Optional but recommended)* a Macrobenchmark to get before/after numbers —
`baselineprofile/src/main/java/.../StartupBenchmark.kt`:
```kotlin
package com.mtd.megawallet.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = "com.mtd.megawallet",
        metrics = listOf(StartupTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) { pressHome(); startActivityAndWait() }

    @Test fun startupNone() = measure(CompilationMode.None())                 // baseline (no profile)
    @Test fun startupBaselineProfile() = measure(CompilationMode.Partial())   // with the generated profile
}
```

## 5. Generate + apply + measure

```bash
# 1) Generate the profile (writes app/src/<variant>/generated/baselineProfiles/):
./gradlew :app:generateBaselineProfile

# 2) Build a release/benchmark APK that now bundles the profile.
./gradlew :app:assembleBenchmark

# 3) Measure the win (compare startupNone vs startupBaselineProfile in the test output / build report):
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
```

## 6. Device requirement (important)

Macrobenchmark/baseline-profile generation needs a **physical device** OR an **emulator with an
AOSP/"Google APIs" (not Play) image, API 28+**, ideally rooted/`userdebug`. If you don't have one,
add a **Gradle Managed Device** to `:baselineprofile` and use `useManagedDevices` instead of
`useConnectedDevices`:

```kotlin
android {
    testOptions.managedDevices.devices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"; apiLevel = 34; systemImageSource = "aosp"
        }
    }
}
baselineProfile { managedDevices += "pixel6Api34"; useConnectedDevices = false }
```
Then generate with `./gradlew :app:generateBaselineProfile` (it spins the managed device up/down).

## 7. What "good" looks like
- `StartupTimingMetric` (timeToInitialDisplay) drops meaningfully from `startupNone` → `startupBaselineProfile`
  (typically 15–30% on cold start).
- Pair with `FrameTimingMetric` on a scroll journey (Wallet/History) to confirm jank% falls.
- This is the **runtime** proof the earlier compiler-level recomposition work asked for.
