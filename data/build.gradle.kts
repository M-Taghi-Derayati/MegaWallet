plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

android {
    namespace = "com.mtd.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    kotlinOptions {
        jvmTarget = "17"
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
    implementation(libs.bundles.google.auth)
    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.coroutines)


    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.coroutines.test)

    androidTestImplementation(libs.multidex)

    androidTestImplementation(libs.hilt.test)
    kspAndroidTest(libs.dagger.hilt.compiler)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
