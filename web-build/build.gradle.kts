@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

/*
 * Always-on wasmJs web demo. Compiles the complete shared common graph: UI, state, Room/Paging
 * contracts, storage, and networking, with browser-specific actual implementations.
 * Invoke: ./gradlew -p web-build compileKotlinWasmJs  OR  ./gradlew compileWebJs
 */
plugins {
    kotlin("multiplatform") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0-beta02"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.10"
    id("androidx.room3") version "3.0.0"
}

val rethinkRoot = rootDir.parentFile!!

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            // Compile the same common graph as Android: UI, state, paging, database models,
            // storage contracts, and networking. Platform implementations live in wasmJsMain.
            kotlin.srcDir(rethinkRoot.resolve("shared/src/commonMain/kotlin"))
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("io.ktor:ktor-client-core:3.5.1")
                implementation("io.insert-koin:koin-core:4.2.2")
                implementation("androidx.room3:room3-runtime:3.0.0")
                implementation("androidx.room3:room3-paging:3.0.0")
                implementation("androidx.paging:paging-common:3.5.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
                implementation(compose.ui)
            }
        }
        val wasmJsMain by getting {
            kotlin.srcDir(rethinkRoot.resolve("shared/src/wasmJsMain/kotlin"))
            dependencies {
                implementation(compose.ui)
                implementation("com.squareup.okio:okio:3.18.0")
                implementation("com.squareup.okio:okio-fakefilesystem:3.18.0")
                implementation("io.ktor:ktor-client-js:3.5.1")
            }
        }
    }
}

dependencies {
    add("kspWasmJs", "androidx.room3:room3-compiler:3.0.0")
}

room3 {
    schemaDirectory(rethinkRoot.resolve("shared/schemas").absolutePath)
}
