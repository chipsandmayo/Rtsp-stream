plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chipsandmayo.rtspstream"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chipsandmayo.rtspstream"
        minSdk = 26
        targetSdk = 35
        // CI sets GITHUB_RUN_NUMBER, so every build gets a higher version for the in-app updater
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    // One fixed key for every build, so new APKs install as updates over old ones.
    // Fine for personal/sideloaded use; use a private key (kept out of git) before publishing to Play.
    signingConfigs {
        create("shared") {
            storeFile = file("rtsp-debug.keystore")
            storePassword = "rtspstream"
            keyAlias = "rtspstream"
            keyPassword = "rtspstream"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
        viewBinding = true
        buildConfig = true
    }
    lint {
        // Media3's RTSP knobs are marked @UnstableApi; we opt in deliberately.
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
