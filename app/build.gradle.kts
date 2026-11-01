plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.cronoszip.app"
    compileSdk = 35

    defaultConfig {
        // A separate package from CronosZip, so both apps can be installed side by side.
        applicationId = "com.gamingwithrevals.chronosmanager"
        minSdk = 26
        targetSdk = 35
        // Monotonic, time-based code so every build installs over the previous one.
        versionCode = (System.currentTimeMillis() / 60_000L).toInt()
        versionName = System.getenv("CRONOSZIP_VERSION_NAME") ?: "1.2.3"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("CRONOSZIP_KEYSTORE_PATH") ?: "cronoszip-release.keystore"
            storeFile = file(keystorePath)
            storePassword = System.getenv("CRONOSZIP_KEYSTORE_PASSWORD") ?: "cronoszip123"
            keyAlias = System.getenv("CRONOSZIP_KEY_ALIAS") ?: "cronoszip"
            keyPassword = System.getenv("CRONOSZIP_KEY_PASSWORD") ?: "cronoszip123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
