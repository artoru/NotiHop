plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.io.FileInputStream
import java.util.Properties

// Release signing is loaded from a local, git-ignored file (secrets/keystore.properties).
// When that file is absent - as on F-Droid's build server - the release build stays
// unsigned, which is what F-Droid wants: it rebuilds, then copies our signature onto
// its own build to prove the two match (reproducible builds).
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
        // Signing is done by AGP itself, not by an external apksigner call: apksigner
        // re-aligns ZIP entries while signing, and F-Droid's reproducibility check
        // (apksigcopier signature copying) can only reproduce the layout that AGP's own
        // signing step produces. Verified locally with fdroidserver's common.verify_apks():
        // AGP-signed reference + AGP unsigned rebuild -> match.
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file("secrets/" + keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            // v2 only: byte-deterministic, and sufficient for minSdk 26. The optional v1
            // (JAR) scheme adds per-entry digests and the v3 scheme embeds "protected
            // origins" that vary per invocation - both hurt reproducibility.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = false
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // AGP embeds the current git revision in META-INF/version-control-info.textproto,
            // which would make the APK depend on the git state of whoever built it.
            // Disable it so our APK and F-Droid's rebuild are identical.
            vcsInfo {
                include = false
            }
        }
    }

    // Reproducibility notes: no minification/R8, no vcs-info stamping, AGP signing
    // restricted to the byte-deterministic v2 scheme (see signingConfigs above).

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
