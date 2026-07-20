pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Bravedns"
include(":shared", ":app", ":tun2socks")

// web-build is NOT includeBuild'd: AGP/KMP composite issues hide wasmJs tasks.
// Always-on via root tasks compileWebJs / runWebDemo (./gradlew -p web-build …).
