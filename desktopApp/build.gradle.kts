import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.example.metalrat.MainKt"

        // jpackage comes from a Temurin 21 toolchain (auto-provisioned): Android Studio's JBR also
        // satisfies "JDK 21" but ships without jpackage. The app bundles that JDK's runtime via jlink.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            // Each format can only be built on its own operating system: the DMG on the Mac, the DEB on Linux.
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "MetalRat"
            packageVersion = libs.versions.app.get()
            description = "MetalRat – Videos mit Zeitpunkt und Textstellen merken"
            vendor = "TheBigSchabowski"
            // jdk.httpserver: receives captures from the browser extension.
            // jdk.crypto.ec: EC cipher suites, without them HTTPS to many sites fails (thumbnails, titles).
            modules("java.desktop", "jdk.httpserver", "jdk.crypto.ec")
            macOS {
                bundleID = "com.example.metalrat"
                iconFile.set(project.file("icon.icns"))
            }
            linux {
                packageName = "metalrat"
                iconFile.set(project.file("icon.png"))
                debMaintainer = "TheBigSchabowski@users.noreply.github.com"
                appCategory = "utils"
                menuGroup = "Utility;"
            }
        }
    }
}

