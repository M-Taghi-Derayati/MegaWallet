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
            // ۱. انتقال موارد مشکل‌ساز از pickFirst به excludes (راه حل قطعی Netty)
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/native-image/io.netty/**"

            // ۲. موارد BouncyCastle و امضاهای JAR
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.RSA"
            excludes += "META-INF/INDEX.LIST" // از pickFirst به اینجا منتقل شد چون نیازی به آن نیست

            // ۳. حل تداخل Protobuf (اگر هنوز فایل‌های .proto خطا می‌دهند)
            excludes += "google/protobuf/*.proto"

            // ۴. مواردی که واقعاً باید یکی از آن‌ها انتخاب شود (Licenseها و موارد خاص)
            pickFirsts.add("META-INF/LICENSE.md")
            pickFirsts.add("META-INF/LICENSE-notice.md")
            pickFirsts.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/FastDoubleParser-LICENSE")
            pickFirsts.add("META-INF/FastDoubleParser-NOTICE")
            pickFirsts.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
            pickFirsts.add("org/bouncycastle/x509/CertPathReviewerMessages.properties")

            // ۵. برای فایل‌های پروتوباف که در پیام‌های قبلی تداخل داشتند
            pickFirsts.add("google/protobuf/type.proto")
            pickFirsts.add("google/protobuf/descriptor.proto")
            // و بقیه فایل‌های .proto که در خطای قبلی لیست شده بود
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

configurations.all {
    resolutionStrategy {

    }
}

