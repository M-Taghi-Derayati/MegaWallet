plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

android {
    namespace = "com.mtd.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
            pickFirsts.add("META-INF/INDEX.LIST")
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE-notice.md")
            pickFirsts.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/FastDoubleParser-LICENSE")
            pickFirsts.add("META-INF/FastDoubleParser-NOTICE")
            pickFirsts.add("META-INF/io.netty.versions.properties")
        }

    }
}

dependencies {
    implementation(project(":domain"))
    api(project(":core"))


    androidTestImplementation(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

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