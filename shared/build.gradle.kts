@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val firestackRepo = project.findProperty("firestackRepo") as? String ?: "github"
val firestackCommit = project.findProperty("firestackCommit") as? String ?: "main"
fun firestackDep(): String = when (firestackRepo) {
    "jitpack", "github" -> "com.github.celzero:firestack:$firestackCommit@aar"
    "ossrh" -> "com.celzero:firestack:$firestackCommit@aar"
    else -> "com.github.celzero:firestack:$firestackCommit@aar"
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    // Matches NetGuard's KMP UI stack and exposes Material 3 Expressive shapes on wasmJs.
    id("org.jetbrains.compose") version "1.12.0-beta02"
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    // AGP-backed KMP library target. It can coexist with
    // wasmJs in one shared module; keeping both targets here makes commonMain the single source
    // of truth for Android and the browser.
    androidLibrary {
        namespace = "com.bernaferrari.bravedns.shared"
        compileSdk = 37
        minSdk = 23
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    wasmJs {
        browser()
        useEsModules()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.koin.core.viewmodel)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.okio)

            // Room 3 KMP: entities/DAOs in commonMain
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
            implementation(libs.paging.common)
            // Lifecycle ViewModel is multiplatform as well. Keeping paging query state in
            // commonMain lets every UI target use the same query and refresh semantics.
            implementation(libs.lifecycle.viewmodel)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

            implementation(compose.runtime)
            implementation(compose.foundation)
            // Keep Material 3 Expressive aligned with the Compose Multiplatform beta on every
            // target so Android and Wasm render the same commonMain controls and shapes.
            implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
            // NavigationSuiteScaffold gives the shared UI the same adaptive bottom-bar/rail
            // behavior without platform-specific navigation chrome.
            implementation(libs.jetbrains.material3.adaptive.navigation.suite)
            // Single Nav3 coordinate. On Android this aliases to androidx.navigation3-ui
            // (plus androidx.navigation3-runtime); wasm keeps the JetBrains implementation.
            // api() so :app can use EntryProviderScope.entry without a second Nav3 dep.
            api(libs.jetbrains.navigation3.ui)
            implementation(libs.compose.adaptive.navigation3)
            implementation(libs.compose.lifecycle.viewmodel.navigation3)
            implementation(compose.ui)
            // Generate seed-based Material 3 color schemes so the
            // shared web demo can apply every appearance swatch immediately.
            implementation(libs.materialkolor.kmp)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(firestackDep())
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.room.sqlite.wrapper)
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            // room-runtime + sqlite-bundled are KMP; room3-sqlite-wrapper is
            // Android-only (AAR) and unresolvable on JVM.
            implementation(libs.androidx.sqlite.bundled)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.okio.fakefilesystem)
            implementation(libs.ktor.client.js)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspWasmJs", libs.androidx.room.compiler)
}

// JVM demo (server-side / local stand-in alongside browser js target)
tasks.register<JavaExec>("runJvmDemo") {
    group = "demo"
    description = "Run non-functional KMP demo on JVM (models/WG/Room types only)"
    dependsOn("compileKotlinJvm")
    classpath = files(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        configurations.getByName("jvmRuntimeClasspath")
    )
    mainClass.set("com.bernaferrari.bravedns.JvmDemoKt")
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
