/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Portable About surface. Android injects its resources and platform actions; wasm uses the same
 * renderer with local vector fallbacks and no-op/demo actions.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.celzero.bravedns.ui.compose.about

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.celzero.bravedns.ui.compose.components.RethinkSharedIconContainer
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor
import com.celzero.bravedns.ui.compose.theme.rethinkGroupedListShape

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
    val trailing: (@Composable () -> Unit)? = null,
)

/** The same production About layout used by Android and the wasm preview. */
@Composable
fun RethinkAboutScreen(
    uiState: RethinkAboutUiState,
    strings: RethinkAboutStrings,
    actions: RethinkAboutActions = RethinkAboutActions(),
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    assets: RethinkAboutAssets = RethinkAboutAssets(),
) {
    val version = uiState.versionName.ifBlank { uiState.slicedVersion }
        .takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }

    RethinkTopBarLazyColumnScreen(
        title = strings.about,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        item { AboutIdentityHero(strings.appName, version) }
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
        item { AboutCommunitySection(uiState, strings, actions, assets) }
        item { AboutPartners(strings, actions, assets) }
        item { AboutFooter(uiState, actions) }
    }
}

@Composable
private fun AboutIdentityHero(
    appName: String,
    version: String?,
) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.heroCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SharedDimensions.iconContainerLg),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        MaterialSymbols.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(SharedDimensions.iconSizeLg),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                version?.let {
                    Surface(
                        shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = SharedDimensions.spacingSmMd, vertical = SharedDimensions.spacingXs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, accent: Color, rows: List<AboutRow>) {
    Column {
        AboutSectionHeader(title, accent)
        RethinkListGroup {
            rows.forEachIndexed { index, row ->
                AboutListRow(row, accent, cardPositionFor(index, rows.lastIndex))
            }
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
        trailing = row.trailing,
        leadingContent = {
            RethinkSharedIconContainer(accent) { row.icon(accent) }
        },
    )
}

@Composable
private fun AboutCommunitySection(
    uiState: RethinkAboutUiState,
    strings: RethinkAboutStrings,
    actions: RethinkAboutActions,
    assets: RethinkAboutAssets,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    Column {
        AboutSectionHeader(strings.connect, accent)
        RethinkListGroup {
            AboutListRow(
                AboutRow(strings.joinTelegram, assets.telegram, actions.onTelegram),
                accent,
                CardPosition.First,
            )
            AboutListRow(
                AboutRow(
                    headline = if (uiState.isBugReportRunning) strings.collectingLogs else strings.reportABug,
                    icon = assets.bugReport,
                    onClick = actions.onBugReport,
                    trailing = if (uiState.isBugReportRunning) {
                        { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent) }
                    } else {
                        null
                    },
                ),
                accent,
                CardPosition.Middle,
            )
            AboutSocialRow(strings, actions, assets, accent)
        }
    }
}

@Composable
private fun AboutSocialRow(
    strings: RethinkAboutStrings,
    actions: RethinkAboutActions,
    assets: RethinkAboutAssets,
    accent: Color,
) {
    val shape = rethinkGroupedListShape(CardPosition.Last)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingGridTile).clip(shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = SharedDimensions.spacingMd, end = SharedDimensions.spacingXs, top = SharedDimensions.spacingXs, bottom = SharedDimensions.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Icon(
                MaterialSymbols.Filled.Share,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(SharedDimensions.iconSizeSm),
            )
            Spacer(Modifier.weight(1f))
            AboutSocialIcon(strings.twitter, actions.onTwitter, assets.twitter, accent)
            AboutSocialIcon(strings.email, actions.onEmail, assets.email, accent)
            AboutSocialIcon(strings.reddit, actions.onReddit, assets.reddit, accent)
            AboutSocialIcon(strings.element, actions.onElement, assets.element, accent)
            AboutSocialIcon(strings.mastodon, actions.onMastodon, assets.mastodon, accent)
        }
    }
}

@Composable
private fun AboutSocialIcon(
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    tint: Color,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(SharedDimensions.touchTargetSm).semantics { contentDescription = label },
    ) {
        icon(tint)
    }
}

@Composable
private fun AboutPartners(strings: RethinkAboutStrings, actions: RethinkAboutActions, assets: RethinkAboutAssets) {
    val logoShape = RoundedCornerShape(SharedDimensions.cornerRadiusMd)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        Text(
            strings.supportedBy,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(0.78f),
        )
        assets.mozillaLogo(
            Modifier
                .width(132.dp)
                .padding(SharedDimensions.spacingXs),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            assets.fossLogo(
                Modifier
                    .width(100.dp)
                    .clip(logoShape)
                    .clickable(onClick = actions.onFoss)
                    .padding(SharedDimensions.spacingXs),
            )
            assets.flossFundsLogo(
                Modifier
                    .width(100.dp)
                    .clip(logoShape)
                    .clickable(onClick = actions.onFlossFunds)
                    .padding(SharedDimensions.spacingXs),
            )
        }
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
