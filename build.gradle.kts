// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
}

allprojects {
    repositories {
        google()
        mavenCentral()

        val firestackRepo = project.findProperty("firestackRepo") as? String ?: "github"

        if (firestackRepo == "jitpack") {
            // jitpack.io/#celzero/firestack
            maven("https://jitpack.io")
        } else if (firestackRepo == "github") {
            // maven.pkg.github.com/celzero/firestack
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/celzero/firestack")
                credentials {
                    username = project.findProperty("gpr.user") as? String ?: System.getenv("USERNAME_GITHUB")
                    password = project.findProperty("gpr.key") as? String ?: System.getenv("TOKEN_GITHUB")
                }
            }
        } else {
            // ossrh: https://central.sonatype.com/artifact/com.celzero/firestack/
            // no-op; mavenCentral is already included
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Always-on wasmJs web UI: run isolated web-build (includeBuild can hide KMP tasks; -p is reliable)
tasks.register<Exec>("compileWebJs") {
    group = "build"
    description = "Compile Kotlin/wasmJs Compose web demo (web-build)"
    workingDir = rootDir
    commandLine("./gradlew", "-p", "web-build", "compileKotlinWasmJs", "--no-daemon")
}

tasks.register<Exec>("runWebDemo") {
    group = "demo"
    description = "Run wasmJs browser development server for commonMain UI demo"
    workingDir = rootDir
    commandLine("./gradlew", "-p", "web-build", "wasmJsBrowserDevelopmentRun", "--no-daemon")
}
