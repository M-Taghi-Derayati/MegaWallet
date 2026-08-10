plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val releaseStoreFilePath = providers.gradleProperty("MEGAWALLET_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("MEGAWALLET_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("MEGAWALLET_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("MEGAWALLET_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }


composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )

    metricsDestination = layout.buildDirectory.dir("compose_compiler")
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
}

android {
    namespace = "com.mtd.megawallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mtd.megawallet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    buildFeatures {
        buildConfig= true
        compose = true
    }

    packaging {
        resources {
            pickFirsts.add("META-INF/FastDoubleParser-LICENSE")
            pickFirsts.add("META-INF/FastDoubleParser-NOTICE")

            // حذف امضاهای تکراری و فایل‌های بیهوده برای کاهش حجم APK
            excludes += setOf(
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "org/bouncycastle/check.properties",
                "google/protobuf/*.proto",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/io.netty.versions.properties",
                "META-INF/native-image/io.netty/**",
                "META-INF/thirdparty-LICENSE",
                "META-INF/thirdparty-NOTICE",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )

            pickFirsts.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
// وابستگی‌های ماژولار داخلی
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":common_ui"))
    implementation(project(":core"))

    // دیسوگر جاوا
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // فقط برای تزریقِ کلاینتِ OkHttpِ اپ به ImageLoaderِ Coil در MegaWalletApplication؛
    // بقیهٔ کارِ شبکه در :data می‌ماند.
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    // AndroidX & UI Essentials
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose Stack
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.hilt)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.leakcanary.android)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // DI Hilt
    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)

    // Utilities & Navigation Legacy Components
    implementation(libs.utilcodex)
    implementation(libs.recyclerview)
    implementation(libs.flexbox)
    implementation(libs.activity)
    implementation(libs.framgnet) // اصلاح تایپو احتمالی کامپوننت فرگمنت شما
    implementation(libs.constraintlayout)
    implementation(libs.bundles.navigation)
    implementation(libs.viewModel)
    implementation(libs.bundles.coroutines)


    // Item 6 — QR address scanner: CameraX preview + ML Kit barcode decoding
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
