import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.util.Properties
import java.io.FileInputStream


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    alias(libs.plugins.detekt)

    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.roborazzi)
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


// Apply plugins independently of requested task spelling; disable their tasks for
// channels that do not contain Firebase. This also handles aggregate/mixed builds.
apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.google.firebase.crashlytics")

val artifactOnlyBuild = providers.gradleProperty("keepoutArtifactsOnly").map(String::toBoolean).getOrElse(false)

tasks.configureEach {
    if (artifactOnlyBuild && name.startsWith("uploadCrashlytics")) enabled = false
    if (name.contains("Fdroid", ignoreCase = true) &&
        (name.contains("GoogleServices") || name.contains("Crashlytics"))) {
        enabled = false
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

// Archive deployments intentionally exclude .git. Android builds keep the descriptive Git
// version, while the browser-only Vercel build gets a stable, non-empty version name instead.
val gitVersion =
    if (rootProject.file(".git").exists()) {
        providers.exec {
            commandLine("git", "describe", "--tags", "--always")
        }.standardOutput.asText.get().trim()
    } else {
        System.getenv("VERCEL_GIT_COMMIT_SHA")
            ?.takeIf(String::isNotBlank)
            ?.take(12)
            ?: "web-demo"
    }

fun getVersionCode(project: Project): Int {
    var code = 0
    try {
        val envCode = System.getenv("VERSION_CODE")
        if (envCode != null) {
            code = Integer.parseInt(envCode)
            project.logger.info("env version code: $code")
        }
    } catch (ex: NumberFormatException) {
        project.logger.info("missing env version code: ${ex.message}")
    }
    if (code == 0) {
        code = (project.properties["VERSION_CODE"] as? String)?.toIntOrNull() ?: 0
        project.logger.info("project properties version code: $code")
    }
    return code
}

try {
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }
} catch (ex: Exception) {
    logger.info("missing keystore prop: ${ex.message}")
    keystoreProperties["keyAlias"] = ""
    keystoreProperties["keyPassword"] = ""
    keystoreProperties["storeFile"] = "/dev/null"
    keystoreProperties["storePassword"] = ""
}

android {
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    namespace = "com.bernaferrari.bravedns"

    androidResources {
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "com.bernaferrari.bravedns"
        minSdk = 24
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = getVersionCode(project)
        versionName = gitVersion
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("config") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            val storeFilePath = keystoreProperties["storeFile"] as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
            }
            storePassword = keystoreProperties["storePassword"] as String?
        }
        create("alpha") {
            keyAlias = System.getenv("ALPHA_KS_ALIAS")
            keyPassword = System.getenv("ALPHA_KS_PASSPHRASE")
            val storeFilePath = System.getenv("ALPHA_KS_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
            }
            storePassword = System.getenv("ALPHA_KS_STORE_PASSPHRASE")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }



    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
            }
        }
        create("leakCanary") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
        }
        // Store screenshots only: the real UI over fictional apps and traffic from src/storeCapture.
        // No VPN, no INTERNET permission; installs beside the real app and is never released.
        create("storeCapture") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".storecapture"
            versionNameSuffix = "-storecapture"
            matchingFallbacks += listOf("debug")
        }
        create("alpha") {
            applicationIdSuffix = ".alpha"
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("alpha")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    run {
        afterEvaluate {
            tasks.configureEach {
                if (name.contains("injectCrashlyticsBuildIds")) {
                    enabled = false
                    logger.warn("disabled build id injection for: $name")
                }
                if (name.contains("uploadCrashlyticsSymbolFile")) {
                    doFirst {
                        logger.info("uploading crashlytics symbols: $name")
                    }
                }
            }
        }
    }



    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            keepDebugSymbols += listOf("**/*.so")
        }
    }

    flavorDimensions += listOf("releaseChannel")
    productFlavors {
        create("play") {
            dimension = "releaseChannel"
        }
        create("fdroid") {
            dimension = "releaseChannel"
        }
        create("website") {
            dimension = "releaseChannel"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
        xmlReport = true
        htmlReport = true
        sarifReport = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

}

// Robolectric loads several Android framework images; keep each worker bounded and
// recycle it between groups of test classes instead of exhausting the default heap.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "2g"
    maxParallelForks = 1
    forkEvery = 12
}

configure<DetektExtension> {
    parallel = true
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$rootDir/config/detekt/baseline.xml")
    source.setFrom(
        files(
            "src/main/kotlin",
            "src/billing/kotlin",
        )
    )
}

// Shared production code has its own ratchet, initialized from the committed tree.
// Keeping it separate prevents previously unscanned KMP debt from expanding the app baseline.
val detektShared by tasks.registering(Detekt::class) {
    description = "Check shared production Kotlin against its existing-debt baseline"
    setSource(files(
        "../shared/src/commonMain/kotlin",
        "../shared/src/androidMain/kotlin",
        "../shared/src/androidJvmMain/kotlin",
        "../shared/src/jvmMain/kotlin",
        "../shared/src/wasmJsMain/kotlin",
    ))
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline.set(file("$rootDir/config/detekt/shared-baseline.xml"))
    buildUponDefaultConfig = true
    parallel = true
    reports {
        html.outputLocation.set(layout.buildDirectory.file("reports/detekt/shared.html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/shared.xml"))
        sarif.outputLocation.set(layout.buildDirectory.file("reports/detekt/shared.sarif"))
    }
}

// The existing local command and CI quality gate now cover both modules.
tasks.named("detekt") { dependsOn(detektShared) }

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
        md.required.set(false)
    }
}

val download by configurations.creating {
    isTransitive = false
}

val firestackRepo = project.findProperty("firestackRepo") as? String ?: "github"
val firestackCommit = project.findProperty("firestackCommit") as? String ?: "main"

fun firestackDependency(suffix: String = ":debug"): String {
    return when (firestackRepo) {
        "jitpack", "github" -> "com.github.celzero:firestack:$firestackCommit$suffix@aar"
        "ossrh" -> "com.celzero:firestack:$firestackCommit@aar"
        else -> throw GradleException("Unknown firestackRepo: $firestackRepo")
    }
}

dependencies {
    implementation(project(":shared"))


    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.materialkolor)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)


    // Room 3 (androidx.room3)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.room.sqlite.wrapper)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Paging
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.okio.jvm)

    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)

    implementation(platform(libs.koin.bom))
    download(libs.koin.core)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    download(libs.koin.android)
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)



    download(firestackDependency())
    "websiteImplementation"(firestackDependency())
    "fdroidImplementation"(firestackDependency())
    "playImplementation"(firestackDependency())

    implementation(libs.androidx.work.runtime.ktx)

    download(libs.ipaddress)
    implementation(libs.ipaddress)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.rules)
    testImplementation(libs.robolectric)
    testImplementation(composeBom)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    androidTestImplementation(libs.mockk.android)

    "leakCanaryImplementation"(libs.leakcanary.android)

    implementation(libs.androidx.biometric)

    "playImplementation"(libs.play.app.update)
    "playImplementation"(libs.play.app.update.ktx)
    "playImplementation"(libs.play.billing)
    "websiteImplementation"(libs.play.billing)

    implementation(libs.androidx.security.crypto)

    implementation(libs.zxing.embedded)

    // lint
    lintChecks(libs.android.security.lint)

    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)

    "websiteImplementation"(platform(libs.firebase.bom))
    "websiteImplementation"(libs.firebase.crashlytics)
    "websiteImplementation"(libs.firebase.crashlytics.ndk)

    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.crashlytics)
    "playImplementation"(libs.firebase.crashlytics.ndk)
}

androidComponents {
    // Store captures use the de-Googled channel only: no Firebase, no billing, no Play variants.
    beforeVariants(selector().withBuildType("storeCapture")) { variant ->
        variant.enable = variant.productFlavors.any { it.second == "fdroid" }
    }

    // Billing (Play Billing + backend client) is shared verbatim by the play and website
    // channels; each channel keeps only its thin wiring (BillingModule, Security, updater).
    // fdroid must never see Play Billing classes.
    onVariants { variant ->
        val channels = variant.productFlavors.map { it.second }
        if ("play" in channels || "website" in channels) {
            variant.sources.java?.addStaticSourceDirectory("src/billing/kotlin")
            variant.sources.kotlin?.addStaticSourceDirectory("src/billing/kotlin")
            variant.sources.res?.addStaticSourceDirectory("src/billing/res")
        }
    }

    // Validate the resolved base code before ABI-specific output encoding. The action must
    // capture only this value, never the script or Project, so configuration caching stays safe.
    val releaseBaseVersionCode = getVersionCode(project)
    val validateReleaseVersionCode =
        tasks.register("validateReleaseVersionCode") {
            inputs.property("baseVersionCode", releaseBaseVersionCode)
            doLast {
                require(releaseBaseVersionCode > 0) {
                    "versionCode is 0: set VERSION_CODE (env or -PVERSION_CODE=...) for release builds."
                }
            }
        }
    tasks
        .matching { (it.name.startsWith("assemble") || it.name.startsWith("bundle")) && it.name.endsWith("Release") }
        .configureEach { dependsOn(validateReleaseVersionCode) }

    onVariants { variant ->
        val versionCodes = mapOf(
            "armeabi-v7a" to 2,
            "arm64-v8a" to 3,
            "x86" to 8,
            "x86_64" to 9
        )
        variant.outputs.forEach { output ->
            val abi = output.filters.find {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val multiplier = if (abi == null) versionCodes.getValue("arm64-v8a")
                else requireNotNull(versionCodes[abi]) { "Unknown output ABI: $abi" }
            val base = output.versionCode.get()
            require(base in 0 until 10_000_000) { "VERSION_CODE must be between 0 and 9,999,999" }
            val encoded = multiplier * 10_000_000L + base
            require(encoded <= Int.MAX_VALUE) { "APK version code overflow" }
            output.versionCode.set(encoded.toInt())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
