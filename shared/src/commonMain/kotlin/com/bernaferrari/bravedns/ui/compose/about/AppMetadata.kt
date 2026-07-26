package com.bernaferrari.bravedns.ui.compose.about

/**
 * Target-neutral metadata for the running Rethink application.
 *
 * Android obtains this from PackageManager. Browser and desktop demos deliberately use a stable
 * value instead, so the shared About surface never needs an Android PackageInfo.
 */
data class AppMetadata(
    val versionName: String = "",
    val installSource: String = "",
    val buildNumber: String = "",
    val lastUpdated: String = "",
    val firstInstalledAtMillis: Long = 0L,
    val isFdroid: Boolean = false,
    val isPlayStore: Boolean = false,
    val isDebug: Boolean = false,
)

fun interface AppMetadataProvider {
    fun metadata(): AppMetadata
}

/** Stable metadata used by the non-functional browser preview. */
val RethinkWebDemoMetadata =
    AppMetadata(
        versionName = "0.0.0-web",
        installSource = "Web preview",
        buildNumber = "Compose Multiplatform",
        isDebug = true,
    )
