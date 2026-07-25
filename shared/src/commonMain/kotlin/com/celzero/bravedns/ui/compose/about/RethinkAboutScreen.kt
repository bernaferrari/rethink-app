/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Portable About surface. Android injects its resources and platform actions; wasm uses the same
 * renderer with local vector fallbacks and no-op/demo actions.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.about

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.ui.compose.theme.rethinkGroupedListPairShape

/** Target-neutral state supplied by platform services. */
data class RethinkAboutUiState(
    val versionName: String = "",
    val installSource: String = "",
    val buildNumber: String = "",
    val slicedVersion: String = "",
    val firebaseToken: String = "",
    val isFirebaseEnabled: Boolean = false,
    val isFdroid: Boolean = false,
    val isDebug: Boolean = false,
    val isBugReportRunning: Boolean = false,
)

/** Copy belongs to the host so browser and Android can localise independently. */
data class RethinkAboutStrings(
    val appName: String,
    val about: String,
    val app: String,
    val whatsNew: String,
    val checkForUpdates: String,
    val joinTelegram: String,
    val reportABug: String,
    val collectingLogs: String,
    val web: String,
    val website: String,
    val github: String,
    val faq: String,
    val docs: String,
    val privacyPolicy: String,
    val terms: String,
    val license: String,
    val connect: String,
    val twitter: String,
    val email: String,
    val reddit: String,
    val element: String,
    val mastodon: String,
    val settings: String,
    val generalSettings: String,
    val appInfo: String,
    val vpnProfile: String,
    val notifications: String,
    val diagnostics: String,
    val statistics: String,
    val databaseDump: String,
    val flightRecorder: String,
    val eventLogs: String,
    val supportedBy: String,
)

/** Every side effect lives in the host adapter, keeping this renderer fully commonMain. */
data class RethinkAboutActions(
    val onTelegram: () -> Unit = {},
    val onBugReport: () -> Unit = {},
    val onWhatsNew: () -> Unit = {},
    val onAppUpdate: () -> Unit = {},
    val onWebsite: () -> Unit = {},
    val onGithub: () -> Unit = {},
    val onFaq: () -> Unit = {},
    val onDocs: () -> Unit = {},
    val onPrivacyPolicy: () -> Unit = {},
    val onTerms: () -> Unit = {},
    val onLicense: () -> Unit = {},
    val onTwitter: () -> Unit = {},
    val onEmail: () -> Unit = {},
    val onReddit: () -> Unit = {},
    val onElement: () -> Unit = {},
    val onMastodon: () -> Unit = {},
    val onGeneralSettings: () -> Unit = {},
    val onAppInfo: () -> Unit = {},
    val onVpnProfile: () -> Unit = {},
    val onNotifications: () -> Unit = {},
    val onStatistics: () -> Unit = {},
    val onDatabaseDump: () -> Unit = {},
    val onFlightRecorder: () -> Unit = {},
    val onEventLogs: () -> Unit = {},
    val onToken: () -> Unit = {},
    val onFoss: () -> Unit = {},
    val onFlossFunds: () -> Unit = {},
)

/**
 * Android provides branded drawables here. The defaults deliberately keep the complete page
 * legible and polished on wasm without relying on Android resources.
 */
data class RethinkAboutAssets(
    val telegram: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Send, null, tint = tint) },
    val bugReport: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.BugReport, null, tint = tint) },
    val github: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Code, null, tint = tint) },
    val twitter: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Share, null, tint = tint) },
    val email: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Email, null, tint = tint) },
    val reddit: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Share, null, tint = tint) },
    val element: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Share, null, tint = tint) },
    val mastodon: @Composable (Color) -> Unit = { tint -> Icon(MaterialSymbols.Filled.Share, null, tint = tint) },
    val mozillaLogo: @Composable (Modifier) -> Unit = { modifier ->
        Text("Mozilla", modifier = modifier, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    },
    val fossLogo: @Composable (Modifier) -> Unit = { modifier ->
        Text("FOSS", modifier = modifier, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    },
    val flossFundsLogo: @Composable (Modifier) -> Unit = { modifier ->
        Text("FLOSS Funds", modifier = modifier, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    },
)

private data class AboutRow(
    val headline: String,
    val icon: @Composable (Color) -> Unit,
    val onClick: () -> Unit,
)

/** The same production About layout used by Android and the wasm preview. */
@Composable
fun RethinkAboutScreen(
    uiState: RethinkAboutUiState,
    strings: RethinkAboutStrings,
    actions: RethinkAboutActions = RethinkAboutActions(),
    modifier: Modifier = Modifier,
    assets: RethinkAboutAssets = RethinkAboutAssets(),
    appearanceContent: (@Composable () -> Unit)? = null,
) {
    val version = uiState.versionName.ifBlank { uiState.slicedVersion }
        .takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }

    RethinkTopBarLazyColumnScreen(
        title = strings.appName,
        subtitle = version,
        modifier = modifier,
    ) {
        appearanceContent?.let { content ->
            item { content() }
        }
        item {
            AboutAppSection(
                uiState = uiState,
                strings = strings,
                actions = actions,
                assets = assets,
            )
        }
        item {
            AboutSection(
                title = strings.web,
                accent = MaterialTheme.colorScheme.secondary,
                rows = listOf(
                    AboutRow(strings.website, { tint -> Icon(MaterialSymbols.Filled.Public, null, tint = tint) }, actions.onWebsite),
                    AboutRow(strings.github, assets.github, actions.onGithub),
                    AboutRow(strings.faq, { tint -> Icon(MaterialSymbols.AutoMirrored.Filled.HelpOutline, null, tint = tint) }, actions.onFaq),
                    AboutRow(strings.docs, { tint -> Icon(MaterialSymbols.AutoMirrored.Filled.Article, null, tint = tint) }, actions.onDocs),
                    AboutRow(strings.privacyPolicy, { tint -> Icon(MaterialSymbols.Filled.Policy, null, tint = tint) }, actions.onPrivacyPolicy),
                    AboutRow(strings.terms, { tint -> Icon(MaterialSymbols.Filled.Gavel, null, tint = tint) }, actions.onTerms),
                    AboutRow(strings.license, { tint -> Icon(MaterialSymbols.AutoMirrored.Filled.Article, null, tint = tint) }, actions.onLicense),
                ),
            )
        }
        item { AboutConnectSection(strings, actions, assets) }
        item {
            AboutSection(
                title = strings.settings,
                accent = MaterialTheme.colorScheme.primary,
                rows = listOf(
                    AboutRow(strings.generalSettings, { tint -> Icon(MaterialSymbols.Filled.Settings, null, tint = tint) }, actions.onGeneralSettings),
                    AboutRow(strings.appInfo, { tint -> Icon(MaterialSymbols.Filled.Info, null, tint = tint) }, actions.onAppInfo),
                    AboutRow(strings.vpnProfile, { tint -> Icon(MaterialSymbols.Filled.VpnKey, null, tint = tint) }, actions.onVpnProfile),
                    AboutRow(strings.notifications, { tint -> Icon(MaterialSymbols.Filled.Notifications, null, tint = tint) }, actions.onNotifications),
                ),
            )
        }
        item {
            val rows = buildList {
                add(AboutRow(strings.statistics, { tint -> Icon(MaterialSymbols.Filled.BarChart, null, tint = tint) }, actions.onStatistics))
                add(AboutRow(strings.databaseDump, { tint -> Icon(MaterialSymbols.Filled.Backup, null, tint = tint) }, actions.onDatabaseDump))
                if (uiState.isDebug) add(AboutRow(strings.flightRecorder, { tint -> Icon(MaterialSymbols.Filled.Backup, null, tint = tint) }, actions.onFlightRecorder))
                add(AboutRow(strings.eventLogs, { tint -> Icon(MaterialSymbols.Filled.Subject, null, tint = tint) }, actions.onEventLogs))
            }
            AboutSection(strings.diagnostics, MaterialTheme.colorScheme.secondary, rows)
        }
        item { PartnerLogosCard(strings, actions, assets) }
        item { AboutFooter(uiState, actions) }
    }
}

@Composable
private fun AboutAppSection(
    uiState: RethinkAboutUiState,
    strings: RethinkAboutStrings,
    actions: RethinkAboutActions,
    assets: RethinkAboutAssets,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        AboutSectionHeader(strings.app, accent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            AboutActionTile(
                title = strings.joinTelegram,
                accent = Color(0xFF74C5FF),
                shape = rethinkGroupedListPairShape(true, CardPosition.First),
                icon = assets.telegram,
                modifier = Modifier.weight(1f),
                onClick = actions.onTelegram,
            )
            AboutActionTile(
                title = if (uiState.isBugReportRunning) strings.collectingLogs else strings.reportABug,
                accent = Color(0xFFFF907F),
                shape = rethinkGroupedListPairShape(false, CardPosition.First),
                icon = assets.bugReport,
                modifier = Modifier.weight(1f),
                onClick = actions.onBugReport,
                inProgress = uiState.isBugReportRunning,
            )
        }
        val rows = buildList {
            add(AboutRow(strings.whatsNew, { tint -> Icon(MaterialSymbols.Filled.NewReleases, null, tint = tint) }, actions.onWhatsNew))
            if (!uiState.isFdroid) add(AboutRow(strings.checkForUpdates, { tint -> Icon(MaterialSymbols.Filled.SystemUpdateAlt, null, tint = tint) }, actions.onAppUpdate))
        }
        rows.forEachIndexed { index, row ->
            AboutListRow(row, accent, aboutTopClusterPosition(index, rows.lastIndex))
        }
    }
}

@Composable
private fun AboutSection(title: String, accent: Color, rows: List<AboutRow>) {
    Column {
        AboutSectionHeader(title, accent)
        rows.forEachIndexed { index, row ->
            AboutListRow(row, accent, cardPositionFor(index, rows.lastIndex))
        }
    }
}

@Composable
private fun AboutSectionHeader(title: String, accent: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier.padding(start = SharedDimensions.spacingNone, top = SharedDimensions.spacingMd, bottom = SharedDimensions.spacingSm),
    )
}

@Composable
private fun AboutListRow(row: AboutRow, accent: Color, position: CardPosition) {
    RethinkListItem(
        headline = row.headline,
        position = position,
        highlightContainerColor = accent.copy(alpha = 0.22f),
        onClick = row.onClick,
        leadingContent = {
            RethinkSharedIconContainer(accent) { row.icon(accent) }
        },
    )
}

private fun aboutTopClusterPosition(index: Int, lastIndex: Int): CardPosition = when {
    lastIndex <= 0 -> CardPosition.Last
    index == lastIndex -> CardPosition.Last
    else -> CardPosition.Middle
}

@Composable
private fun AboutActionTile(
    title: String,
    accent: Color,
    shape: RoundedCornerShape,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
    inProgress: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.clip(shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RethinkSharedIconContainer(accent) { icon(accent) }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            if (inProgress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
        }
    }
}

@Composable
private fun AboutConnectSection(strings: RethinkAboutStrings, actions: RethinkAboutActions, assets: RethinkAboutAssets) {
    val accent = MaterialTheme.colorScheme.tertiary
    Column {
        AboutSectionHeader(strings.connect, accent)
        AboutListRow(AboutRow(strings.twitter, assets.twitter, actions.onTwitter), accent, CardPosition.First)
        Spacer(Modifier.height(SharedDimensions.spacingXs))
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
                AboutActionTile(strings.email, accent, rethinkGroupedListPairShape(true, CardPosition.First), assets.email, Modifier.weight(1f), actions.onEmail)
                AboutActionTile(strings.reddit, accent, rethinkGroupedListPairShape(false, CardPosition.First), assets.reddit, Modifier.weight(1f), actions.onReddit)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
                AboutActionTile(strings.element, accent, rethinkGroupedListPairShape(true, CardPosition.Last), assets.element, Modifier.weight(1f), actions.onElement)
                AboutActionTile(strings.mastodon, accent, rethinkGroupedListPairShape(false, CardPosition.Last), assets.mastodon, Modifier.weight(1f), actions.onMastodon)
            }
        }
    }
}

@Composable
private fun PartnerLogosCard(strings: RethinkAboutStrings, actions: RethinkAboutActions, assets: RethinkAboutAssets) {
    val lightSurface = MaterialTheme.colorScheme.surface.red > 0.5f
    val cardColor = if (lightSurface) Color(0xFF141922) else MaterialTheme.colorScheme.surfaceContainerLow
    val textColor = if (lightSurface) Color(0xFFE8EDF8) else MaterialTheme.colorScheme.onSurfaceVariant
    val chipColor = if (lightSurface) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
    val chipBorder = if (lightSurface) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
        color = cardColor,
        border = BorderStroke(1.dp, if (lightSurface) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(SharedDimensions.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            Text(strings.supportedBy, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, color = textColor, modifier = Modifier.alpha(0.8f))
            assets.mozillaLogo(Modifier.width(150.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd), verticalAlignment = Alignment.CenterVertically) {
                PartnerLogoChip(chipColor, chipBorder, actions.onFoss) { assets.fossLogo(Modifier.width(110.dp)) }
                PartnerLogoChip(chipColor, chipBorder, actions.onFlossFunds) { assets.flossFundsLogo(Modifier.width(110.dp)) }
            }
        }
    }
}

@Composable
private fun PartnerLogoChip(color: Color, border: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    val chipShape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg)
    Surface(
        onClick = onClick,
        shape = chipShape,
        color = color,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clip(chipShape),
    ) {
        Row(
            modifier = Modifier.height(46.dp).padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@Composable
private fun AboutFooter(uiState: RethinkAboutUiState, actions: RethinkAboutActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.isFirebaseEnabled && !uiState.isFdroid) {
            Text(
                text = uiState.firebaseToken,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().alpha(0.5f).padding(bottom = SharedDimensions.spacingMd).clickable(onClick = actions.onToken),
            )
        }
        Text(
            text = listOf(uiState.versionName, uiState.installSource).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().alpha(0.75f),
        )
        if (uiState.buildNumber.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(uiState.buildNumber, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().alpha(0.55f))
        }
    }
}
