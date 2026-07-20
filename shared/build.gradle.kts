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
    id("org.jetbrains.compose") version "1.10.0"
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    android {
        namespace = "com.celzero.bravedns.shared"
        compileSdk = 37
        minSdk = 23
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

    // JS/browser lives in :web (always on). AGP android KMP library + js() in the same
    // module double-registers Gradle's clean task; :web compiles the same commonMain/jsMain.

    // iOS optional (not a product goal)
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.koin.core)
            implementation("com.squareup.okio:okio:3.17.0")
            implementation(libs.androidx.datastore.preferences.core)

            // Room 3 KMP: entities/DAOs in commonMain
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
            implementation("androidx.paging:paging-common:3.3.6")

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
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
            implementation(libs.androidx.room.sqlite.wrapper)
            implementation(libs.androidx.sqlite.bundled)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
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
    mainClass.set("com.celzero.bravedns.JvmDemoKt")
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
