plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.android.ksp)
}

android {
    namespace = "com.mtd.core"
    compileSdk = 36

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


}

dependencies {

    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.bundles.okhttp)
    implementation(libs.retrofit)

    api(libs.timber)
    api("org.bouncycastle:bcprov-jdk15to18:1.70")
    api(libs.bundles.web3){
        exclude(group = "org.bouncycastle")
    }
    api(libs.bitcoinj){
        exclude(group = "org.bouncycastle")
    }

    api(libs.bitcoin.kmp)
    api(libs.bitcoin.jni)
    api(libs.bundles.gson)

    api(libs.bundles.google.auth)


    implementation(libs.security.crypto)
    api(libs.socket)
    {
        exclude(group = "org.json", module = "json")
    }
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