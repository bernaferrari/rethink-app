package com.celzero.bravedns.ui.compose.about

data class AboutUiState(
    val versionName: String = "",
    val installSource: String = "",
    val buildNumber: String = "",
    val lastUpdated: String = "",
    val slicedVersion: String = "",
    val daysSinceInstall: String = "",
    val sponsoredAmount: String = "",
    val firebaseToken: String = "",
    val isFirebaseEnabled: Boolean = false,
    val isFdroid: Boolean = false,
    val isPlayStore: Boolean = false,
    val isDebug: Boolean = false,
    val isBugReportRunning: Boolean = false,
)
