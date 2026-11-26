plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.infinicada.focuspocus"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.infinicada.focuspocus"
        minSdk = 29
        targetSdk = 36
        versionCode = 48
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing for CI (see .github/workflows/release.yml). Only set up when
    // the environment provides a keystore, so local builds and Android Studio's
    // "Generate Signed Bundle" wizard work as before.
    val uploadStoreFile = System.getenv("SIGNING_STORE_FILE")
    if (uploadStoreFile != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "upload"
                // PKCS12 keystores use the store password for the key as well.
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
                    ?: System.getenv("SIGNING_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (uploadStoreFile != null) {
                signingConfig = signingConfigs.getByName("upload")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // False positive: MainActivity extends ComponentActivity, not FragmentActivity,
        // so the Fragment >= 1.3.0 requirement for ActivityResult APIs does not apply.
        disable += "InvalidFragmentVersionForActivityResult"
        // The baseline is gone on purpose: it had drifted badly (entries for files
        // deleted releases ago), which made "grandfathered" indistinguishable from
        // "real". Issues are now either fixed, suppressed at the site with a reason,
        // or left visible. The only warnings that should remain are the
        // dependency/targetSdk currency nags — those are a deliberate per-release
        // decision that needs a build and a device pass, so they stay in the report
        // rather than being silenced.
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Explicit dep so the outdated fragment 1.1.0 pulled in transitively (Play
    // Console warning) resolves to a current version.
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
}