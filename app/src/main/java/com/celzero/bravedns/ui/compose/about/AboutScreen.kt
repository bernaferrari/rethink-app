/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Android resource/service adapter for the commonMain About renderer.
 */
package com.celzero.bravedns.ui.compose.about

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R

/**
 * Keeps Android-only resources, app state, and intent dispatch out of the renderer. The complete
 * visual tree now lives in shared/commonMain and is also used by the wasm demo.
 */
@Composable
fun AboutScreen(
    uiState: AboutUiState,
    onSponsorClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onWhatsNewClick: () -> Unit,
    onAppUpdateClick: () -> Unit,
    onContributorsClick: () -> Unit,
    onTranslateClick: () -> Unit,
    onWebsiteClick: () -> Unit,
    onGithubClick: () -> Unit,
    onFaqClick: () -> Unit,
    onDocsClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsOfServiceClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onTwitterClick: () -> Unit,
    onEmailClick: () -> Unit,
    onRedditClick: () -> Unit,
    onElementClick: () -> Unit,
    onMastodonClick: () -> Unit,
    onGeneralSettingsClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onVpnProfileClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onStatsClick: () -> Unit,
    onDbStatsClick: () -> Unit,
    onFlightRecordClick: () -> Unit,
    onEventLogsClick: () -> Unit,
    onTokenClick: () -> Unit,
    onTokenDoubleTap: () -> Unit,
    onFossClick: () -> Unit,
    onFlossFundsClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    RethinkAboutScreen(
        uiState = RethinkAboutUiState(
            versionName = uiState.versionName,
            installSource = uiState.installSource,
            buildNumber = uiState.buildNumber,
            slicedVersion = uiState.slicedVersion,
            firebaseToken = uiState.firebaseToken,
            isFirebaseEnabled = uiState.isFirebaseEnabled,
            isFdroid = uiState.isFdroid,
            isDebug = uiState.isDebug,
            isBugReportRunning = uiState.isBugReportRunning,
        ),
        strings = RethinkAboutStrings(
            appName = stringResource(R.string.app_name),
            about = stringResource(R.string.title_about),
            app = stringResource(R.string.about_app),
            whatsNew = stringResource(R.string.about_whats_new, uiState.slicedVersion),
            checkForUpdates = stringResource(R.string.about_app_update_check),
            joinTelegram = stringResource(R.string.about_join_telegram),
            reportABug = stringResource(R.string.about_bug_report),
            collectingLogs = stringResource(R.string.collecting_logs_progress_text),
            web = stringResource(R.string.about_web),
            website = stringResource(R.string.about_website),
            github = stringResource(R.string.about_github),
            faq = stringResource(R.string.about_faq),
            docs = stringResource(R.string.about_docs),
            privacyPolicy = stringResource(R.string.about_privacy_policy),
            terms = stringResource(R.string.about_terms_of_service),
            license = stringResource(R.string.about_license),
            connect = stringResource(R.string.about_connect),
            twitter = stringResource(R.string.about_twitter),
            email = stringResource(R.string.about_email),
            reddit = stringResource(R.string.lbl_reddit),
            element = stringResource(R.string.lbl_matrix),
            mastodon = stringResource(R.string.lbl_mastodon),
            settings = stringResource(R.string.about_settings),
            generalSettings = stringResource(R.string.settings_general_header),
            appInfo = stringResource(R.string.about_settings_app_info),
            vpnProfile = stringResource(R.string.about_settings_vpn_profile),
            notifications = stringResource(R.string.about_settings_notification),
            diagnostics = stringResource(R.string.title_statistics),
            statistics = stringResource(R.string.title_statistics),
            databaseDump = stringResource(R.string.title_database_dump),
            flightRecorder = "Flight Recorder",
            eventLogs = stringResource(R.string.event_logs_title),
            supportedBy = stringResource(R.string.about_mozilla),
        ),
        actions = RethinkAboutActions(
            onTelegram = onTelegramClick,
            onBugReport = onBugReportClick,
            onWhatsNew = onWhatsNewClick,
            onAppUpdate = onAppUpdateClick,
            onWebsite = onWebsiteClick,
            onGithub = onGithubClick,
            onFaq = onFaqClick,
            onDocs = onDocsClick,
            onPrivacyPolicy = onPrivacyPolicyClick,
            onTerms = onTermsOfServiceClick,
            onLicense = onLicenseClick,
            onTwitter = onTwitterClick,
            onEmail = onEmailClick,
            onReddit = onRedditClick,
            onElement = onElementClick,
            onMastodon = onMastodonClick,
            onGeneralSettings = onGeneralSettingsClick,
            onAppInfo = onAppInfoClick,
            onVpnProfile = onVpnProfileClick,
            onNotifications = onNotificationClick,
            onStatistics = onStatsClick,
            onDatabaseDump = onDbStatsClick,
            onFlightRecorder = onFlightRecordClick,
            onEventLogs = onEventLogsClick,
            onToken = onTokenClick,
            onFoss = onFossClick,
            onFlossFunds = onFlossFundsClick,
        ),
        onBackClick = onBackClick,
        assets = RethinkAboutAssets(
            telegram = { tint -> Icon(painterResource(R.drawable.ic_telegram), null, tint = tint) },
            bugReport = { tint -> Icon(painterResource(R.drawable.ic_android_icon), null, tint = tint) },
            github = { tint -> Icon(painterResource(R.drawable.ic_github), null, tint = tint) },
            twitter = { tint -> Icon(painterResource(R.drawable.ic_twitter), null, tint = tint) },
            email = { tint -> Icon(painterResource(R.drawable.ic_mail), null, tint = tint) },
            reddit = { tint -> Icon(painterResource(R.drawable.ic_reddit), null, tint = tint) },
            element = { tint -> Icon(painterResource(R.drawable.ic_element), null, tint = tint) },
            mastodon = { tint -> Icon(painterResource(R.drawable.ic_mastodon), null, tint = tint) },
            mozillaLogo = { modifier -> Image(painterResource(R.drawable.mozilla), null, modifier = modifier, contentScale = ContentScale.FillWidth) },
            fossLogo = { modifier -> Image(painterResource(R.drawable.foss_logo), null, modifier = modifier, contentScale = ContentScale.Fit) },
            flossFundsLogo = { modifier -> Image(painterResource(R.drawable.ic_floss_fund_badge), null, modifier = modifier, contentScale = ContentScale.Fit) },
        ),
    )
}
