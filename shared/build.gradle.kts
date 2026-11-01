plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.example.eichhorn.shared"
        compileSdk = 36
        minSdk = 27
    }
    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        // Android and desktop both run on the JVM: model, storage, link logic and the whole UI live here once.
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        named("desktopMain") { dependsOn(jvmCommonMain) }
        androidMain { dependsOn(jvmCommonMain) }
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
