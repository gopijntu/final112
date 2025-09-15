plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.gopi.securevault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gopi.securevault"
        minSdk = 26
        targetSdk = 35
        versionCode = 11          // First release
        versionName = "7"      // Human-readable version

        // Needed for SQLCipher native libs
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    // 🔐 Signing config for Play Store release (edit keystore details in gradle.properties)
    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks") // replace with your keystore path
            storePassword = "your-store-password"
            keyAlias = "your-key-alias"
            keyPassword = "your-key-password"
        }
    }

    buildTypes {
        release {
            // ✅ Security + Play Store compliance
            isMinifyEnabled = true
            isDebuggable = false
            isShrinkResources = true

            // 🔑 Release signing
            signingConfig = signingConfigs.getByName("release")

            // 📌 Generate debug symbols for crash reporting
            ndk {
                debugSymbolLevel = "FULL"   // FULL or SYMBOL_TABLE
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true

            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    buildFeatures {
        viewBinding = true
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val room_version = "2.6.1"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0-rc01")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    // Security - EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // SQLCipher for encrypted Room
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Iconics
    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:iconics-views:5.4.0")
    implementation("com.mikepenz:google-material-typeface:4.0.0.1-kotlin@aar")
}
