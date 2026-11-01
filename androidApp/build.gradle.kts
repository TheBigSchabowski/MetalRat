plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

// Release key lives outside the repo; without it the release APK stays unsigned.
val releaseKey = file("${System.getProperty("user.home")}/.android/metalrat-release.jks")
val releasePass = file("${System.getProperty("user.home")}/.android/metalrat-release.pass")

android {
    namespace = "com.example.metalrat"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.metalrat"
        minSdk = 27
        targetSdk = 36
        versionCode = 2
        versionName = libs.versions.app.get()
    }

    signingConfigs {
        if (releaseKey.exists() && releasePass.exists()) {
            create("release") {
                val pass = releasePass.readText().trim()
                storeFile = releaseKey
                storePassword = pass
                keyAlias = "metalrat"
                keyPassword = pass
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
