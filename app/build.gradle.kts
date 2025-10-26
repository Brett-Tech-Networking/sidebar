plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.bretttech.sidebar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bretttech.sidebar"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // We use package manager queries; no special permissions needed
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat.v170)
    implementation(libs.material.v1120)
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx.v286)
}
