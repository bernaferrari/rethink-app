/*
 * Always-on wasmJs web UI demo. Renders :shared ui/compose via CMP wasmJs.
 * Invoke: ./gradlew -p web-build compileKotlinWasmJs  OR  ./gradlew compileWebJs
 */
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.10.0"
}

val rethinkRoot = rootDir.parentFile!!

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(rethinkRoot.resolve("shared/src/commonMain/kotlin/com/celzero/bravedns/ui/compose"))
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val wasmJsMain by getting {
            kotlin.srcDir(rethinkRoot.resolve("shared/src/wasmJsMain/kotlin"))
            dependencies {
                implementation(compose.ui)
            }
        }
    }
}
