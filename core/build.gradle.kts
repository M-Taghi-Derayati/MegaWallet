plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

android {
    namespace = "com.mtd.core"
    compileSdk = 35

    defaultConfig {

        minSdk = 26
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

    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.bundles.okhttp)
    implementation(libs.retrofit)

    api(libs.timber)

    api(libs.bundles.web3){
        exclude (group= "org.bouncycastle", module= "bcprov-jdk15to18")
        exclude (group= "org.bouncycastle", module= "bcprov-jdk18on")
    }

    api(libs.bundles.google.auth)


    implementation(libs.security.crypto)
    api(libs.bitcoinj)
    api(libs.bundles.gson)
    implementation(libs.material)

    implementation(libs.dagger.hilt)

    ksp(libs.dagger.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}