plugins {
    alias(libs.plugins.android.library)

    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

// TASK-04 — device-attestation shared secret (masterSecret for the 2-level HMAC). This is
// semi-public by design (embedded in the APK; it's an anti-spoof/integrity layer, not a strong
// secret), so the testnet value is embedded directly. Kept PER-ENVIRONMENT so mainnet is just a
// value swap. Either can be overridden via an (untracked) Gradle property without touching code.
val deviceAttestSecretTestnet = providers.gradleProperty("DEVICE_ATTEST_HMAC_SECRET_TESTNET").orNull
    ?: "6a15371d3f05309c9e8a52b893f23f3cb58027d1f2db70d58a0b57ad1c204533"
val deviceAttestSecretMainnet = providers.gradleProperty("DEVICE_ATTEST_HMAC_SECRET_MAINNET").orNull ?: ""

android {
    namespace = "com.mtd.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // TASK-31 / TD-30 — live HTTPS relayer (testnet on the NL server, fronted by CDN+nginx).
        // HTTPS/WSS so release traffic (signed txs + JWT) is encrypted; host-scopes the JWT +
        // idempotency headers (AuthInterceptor/IdempotencyInterceptor read RELAYER_HOST).
        // TODO(TASK-11): pin the cert / public key (GET /api/v1/config/public-key) before Beta.
        buildConfigField("String", "RELAYER_BASE_URL", "\"https://wallet.intexchange.ir/\"")
        buildConfigField("String", "RELAYER_HOST", "\"wallet.intexchange.ir\"")
        buildConfigField("String", "RELAYER_WS_URL", "\"wss://wallet.intexchange.ir/ws\"")

    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // TASK-04 — testnet server (current). The old dead `APP_SECRET_KEY` was removed.
            buildConfigField("String", "DEVICE_ATTEST_HMAC_SECRET", "\"$deviceAttestSecretTestnet\"")
        }
        release {
            isMinifyEnabled = true
            // TASK-04 — mainnet secret (set DEVICE_ATTEST_HMAC_SECRET_MAINNET when mainnet launches;
            // empty until then → device-bound features are simply unavailable, core flows still work).
            buildConfigField("String", "DEVICE_ATTEST_HMAC_SECRET", "\"$deviceAttestSecretMainnet\"")
            /*proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )*/
        }
    }

    compileOptions {
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


    androidTestImplementation(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.bundles.okhttp)
    implementation(libs.retrofit)
    implementation(libs.bundles.gson)
    implementation(libs.bundles.web3)
    implementation(libs.bitcoinj)
    implementation(libs.bitcoin.kmp)
    implementation(libs.bitcoin.jni)
    // secp256k1 support: Conscrypt can't resolve the curve name; BouncyCastle can. Version is pinned
    // to 1.73 by the root resolutionStrategy (force) regardless of the coordinate requested here.
    implementation("org.bouncycastle:bcprov-jdk18on:1.73")
    implementation(libs.bundles.google.auth)
    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.coroutines)


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
