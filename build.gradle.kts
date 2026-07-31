// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // PERF-10 — register com.android.test once here so :baselineprofile applies it version-less
    // (it ships inside AGP and is already on the classpath, so re-requesting it with a version fails).
    // NOTE: the androidx.baselineprofile Gradle plugin is intentionally NOT used — it is incompatible
    // with AGP 9.2.1 (expects the removed TestExtension type). We generate profiles the plugin-free
    // way via benchmark-macro's BaselineProfileRule instead.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.ksp)
   alias(libs.plugins.android.kapt)
    alias(libs.plugins.android.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            // ۱. فورس کردن آخرین نسخه‌های پایدار برای هر دو نسل جکسون به صورت همزمان

            eachDependency {
                if (requested.group == "org.bouncycastle" && requested.name.startsWith("bcprov")) {
                    useTarget("org.bouncycastle:bcprov-jdk18on:1.73")
                    because("Fix duplicate class conflict between jdk15to18 and jdk18on in Web3/Bitcoin libraries")
                }
            }
        }
    }
}