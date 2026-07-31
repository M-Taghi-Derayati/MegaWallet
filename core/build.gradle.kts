plugins {
    alias(libs.plugins.android.library)

    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

android {
    namespace = "com.mtd.core"
    compileSdk = 37

    defaultConfig {

        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
           /* proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )*/
        }
        create("benchmark") {
            initWith(getByName("debug"))
            isMinifyEnabled = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }


}

dependencies {
    implementation(project(":domain"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.bundles.okhttp)
    api(libs.timber)

    // تعریف به صورت api جهت بهینه‌سازی پردازش موازی گریدل و دسترسی سریع دیتا
    api(libs.bundles.web3)
    api(libs.bitcoinj)
    api(libs.bitcoin.kmp)
    api(libs.bitcoin.jni)

    implementation(libs.security.crypto)
    api(libs.socket) {
        exclude(group = "org.json", module = "json")
    }
    implementation(libs.gson)
    implementation(libs.material)

    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.web3) {
        exclude(group = "org.bouncycastle")
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}



configurations.all {
    resolutionStrategy {
        //failOnVersionConflict()
       // activateDependencyLocking()
    }
}
