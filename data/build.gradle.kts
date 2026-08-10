plugins {
    alias(libs.plugins.android.library)

    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

val deviceAttestSecretTestnet = providers.gradleProperty("DEVICE_ATTEST_HMAC_SECRET_TESTNET").orNull
    ?: "6a15371d3f05309c9e8a52b893f23f3cb58027d1f2db70d58a0b57ad1c204533"
//TODO change this code for mainnet
val deviceAttestSecretMainnet = providers.gradleProperty("DEVICE_ATTEST_HMAC_SECRET_MAINNET").orNull
    ?: "6a15371d3f05309c9e8a52b893f23f3cb58027d1f2db70d58a0b57ad1c204533"

android {
    namespace = "com.mtd.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "RELAYER_BASE_URL", "\"https://wallet.intexchange.ir/\"")
        buildConfigField("String", "RELAYER_HOST", "\"wallet.intexchange.ir\"")
        buildConfigField("String", "RELAYER_WS_URL", "\"wss://wallet.intexchange.ir/ws\"")

        buildConfigField("boolean", "CONFIG_BUNDLE_APPLY_ENABLED", "true")

    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEVICE_ATTEST_HMAC_SECRET", "\"$deviceAttestSecretTestnet\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEVICE_ATTEST_HMAC_SECRET", "\"$deviceAttestSecretMainnet\"")
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
    packaging {
        resources {
            // فقط موارد بسیار ضروری را نگه دارید، بقیه با اصلاح ماژول core خودبخود حل می‌شوند
            excludes += setOf(
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/io.netty.versions.properties"
            )
            pickFirsts.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }

    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    androidTestImplementation(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // لایه شبکه اختصاصی ماژول دیتا
    implementation(platform(libs.okhttp.bom))
    implementation(libs.bundles.okhttp)
    implementation(libs.retrofit)
    implementation(libs.bundles.gson)

    // وابستگی‌های بلاکچینی صریح جهت حفظ ساختار ماژول
    implementation(libs.bundles.web3)
    implementation(libs.bitcoinj)
    implementation(libs.bitcoin.kmp)
    implementation(libs.bitcoin.jni)

    implementation("org.bouncycastle:bcprov-jdk18on:1.73")
    implementation(libs.bundles.google.auth)

    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)
    implementation(libs.bundles.coroutines)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.coroutines.test)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.hilt.test)
    kspAndroidTest(libs.dagger.hilt.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
