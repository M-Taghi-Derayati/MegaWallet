// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.ksp)
   alias(libs.plugins.android.kapt)
    alias(libs.plugins.android.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.bouncycastle" && requested.name.startsWith("bcprov")) {
                    useTarget("org.bouncycastle:bcprov-jdk18on:1.73")
                    because("Fix duplicate class conflict between jdk15to18 and jdk18on in Web3/Bitcoin libraries")
                }
            }
        }
    }
}