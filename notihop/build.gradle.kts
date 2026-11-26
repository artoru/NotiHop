plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.io.FileInputStream
import java.util.Properties

// Release signing is loaded from a local, git-ignored file (secrets/keystore.properties).
// When absent (e.g. F-Droid's build server), the release build stays unsigned and
// F-Droid signs it itself — which is exactly what we want.
val keystorePropsFile = rootProject.file("secrets/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
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

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file("secrets/" + keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            // v2 is sufficient for minSdk 26 and is byte-deterministic; the optional
            // v3 scheme embeds a "protected origins" field that varies per signing
            // invocation, so disable it for byte-for-byte reproducible releases.
            enableV3Signing = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Keep the APK byte-for-byte reproducible: v2 signing (above) is deterministic,
    // and no minification/R8 is used. The ART baseline profiles this build emits are
    // themselves deterministic, so they don't affect reproducibility.

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
