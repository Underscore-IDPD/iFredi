plugins {
    id("com.android.application") version "9.4.1"
    id("com.google.gms.google-services") version "4.5.0"
}

android {
    namespace = "com.ifredi.chat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ifredi.chat"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.auth)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.recyclerview)
    implementation(libs.material)

    implementation(libs.glide)
}