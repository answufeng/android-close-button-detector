plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.answufeng.closebuttondemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.answufeng.closebuttondemo"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Play (Android 15+) 16KB page size 檢查：避免把 x86/x86_64 的 native libs 打進 APK。
        // 正式上架通常只需要 arm64-v8a（可選加 armeabi-v7a）。
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
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

    viewBinding {
        enable = true
    }
}

dependencies {
    implementation(project(":close-button-detector"))

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.constraintlayout)
}