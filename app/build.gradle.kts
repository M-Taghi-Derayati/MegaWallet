plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
    alias (libs.plugins.android.navsafeArgs)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.mtd.megawallet"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mtd.megawallet"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled=true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "env"

    productFlavors {
        create("prod") {
            dimension = "env"
            applicationIdSuffix = ".prod"
            versionNameSuffix = "-prod"
        }

        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
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
    buildFeatures {
        dataBinding= true
        viewBinding=true
        buildConfig= true
        compose = true
    }

    packaging {
        resources {
            // حل مشکل مانیفست که قبلاً خوردید
            pickFirsts.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")

            // موارد تکراری رایج
            pickFirsts.add("META-INF/INDEX.LIST")
            pickFirsts.add("META-INF/LICENSE*")
            pickFirsts.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/NOTICE*")
            pickFirsts.add("META-INF/io.netty.versions.properties")
            pickFirsts.add("META-INF/FastDoubleParser-LICENSE")
            pickFirsts.add("META-INF/FastDoubleParser-NOTICE")

            // حذف امضاهای BouncyCastle برای جلوگیری از SecurityException
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.RSA"
            excludes += "org/bouncycastle/check.properties"
        }
    }
}

dependencies {

    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":common_ui"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose Dependencies
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.hilt)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.dagger.hilt)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.utilcodex)
    implementation(libs.recyclerview)
    implementation(libs.flexbox)
    implementation(libs.multidex)
    implementation(libs.activity)
    implementation(libs.framgnet)
    implementation(libs.viewpager2)
    implementation(libs.constraintlayout)
    implementation(libs.bundles.navigation)

    implementation(libs.viewModel)


    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

