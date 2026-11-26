plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.artor.notihop"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.artor.notihop"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // No signingConfig here: Gradle always emits a deterministic
            // notihop-release-unsigned.apk. We sign it externally with apksigner
            // (see tools/sign.sh), and F-Droid rebuilds + verifies the same bytes.
            // Keeping signing out of Gradle avoids AGP's per-run signing variance.
        }
    }

    // Reproducibility notes: no minification/R8, deterministic baseline profiles,
    // and signing done externally with apksigner (v2 scheme, byte-deterministic).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
