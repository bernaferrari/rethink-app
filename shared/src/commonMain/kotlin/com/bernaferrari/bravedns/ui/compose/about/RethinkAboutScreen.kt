/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Portable About surface. Android injects its resources and platform actions; wasm uses the same
 * renderer with local vector fallbacks and no-op/demo actions.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.about

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellContainerColor
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

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
    val appInfo: String,
    val vpnProfile: String,
    val notifications: String,
    val diagnostics: String,
    val statistics: String,
    val databaseDump: String,
    val flightRecorder: String,
    val eventLogs: String,
    val supportedBy: String,
    /** Partner names: text fallback for hosts without logo assets, and the logos' a11y labels. */
    val partnerMozilla: String,
    val partnerFoss: String,
    val partnerFlossFunds: String,
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
    /** Null renders the partner name from [RethinkAboutStrings] as text. */
    val mozillaLogo: (@Composable (Modifier) -> Unit)? = null,
    val fossLogo: (@Composable (Modifier) -> Unit)? = null,
    val flossFundsLogo: (@Composable (Modifier) -> Unit)? = null,
)

private val MozillaLogoWidth = 132.dp
private val PartnerLogoWidth = 100.dp

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
    // The hero shows the release ("v0.5.5y"), not the git-describe build string
    // ("v0.5.5y-120-g…-storecapture"); the footer keeps the full string for bug reports.
    val version = uiState.versionName.ifBlank { uiState.slicedVersion }
        .substringBefore('-')
        .takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("v", ignoreCase = true)) it else "v$it" }

    RethinkTopBarLazyColumnScreen(
        title = strings.about,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        item { AboutIdentityHero(strings.appName, version) }
        item { AboutAppSection(uiState, strings, actions) }
        if (uiState.isDebug) {
            item { AboutDiagnosticsSection(strings, actions) }
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
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RethinkIconWell(
                icon = MaterialSymbols.Filled.Shield,
                tint = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                size = SharedDimensions.iconContainerLg,
                iconSize = SharedDimensions.iconSizeLg,
                shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                Text(appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                version?.let {
                    RethinkPill(it, colors = RethinkPillDefaults.onContainer(MaterialTheme.colorScheme.onPrimaryContainer), size = RethinkPillSize.Large)
                }
            }
        }
    }
}

private fun aboutIcon(icon: ImageVector): @Composable (Color) -> Unit = { tint -> Icon(icon, null, tint = tint) }

@Composable
private fun AboutAppSection(uiState: RethinkAboutUiState, strings: RethinkAboutStrings, actions: RethinkAboutActions) {
    AboutSection(
        title = strings.app,
        accent = MaterialTheme.colorScheme.primary,
        rows = buildList {
            add(AboutRow(strings.whatsNew, aboutIcon(MaterialSymbols.Filled.NewReleases), actions.onWhatsNew))
            // F-Droid owns updates for its builds; the in-app checker only applies to other channels.
            if (!uiState.isFdroid) {
                add(AboutRow(strings.checkForUpdates, aboutIcon(MaterialSymbols.Filled.SystemUpdate), actions.onAppUpdate))
            }
            add(AboutRow(strings.appInfo, aboutIcon(MaterialSymbols.Filled.Info), actions.onAppInfo))
            add(AboutRow(strings.vpnProfile, aboutIcon(MaterialSymbols.Filled.VpnKey), actions.onVpnProfile))
            add(AboutRow(strings.notifications, aboutIcon(MaterialSymbols.Filled.Notifications), actions.onNotifications))
        },
    )
}

@Composable
private fun AboutDiagnosticsSection(strings: RethinkAboutStrings, actions: RethinkAboutActions) {
    AboutSection(
        title = strings.diagnostics,
        accent = MaterialTheme.colorScheme.tertiary,
        rows = listOf(
            AboutRow(strings.statistics, aboutIcon(MaterialSymbols.Filled.BarChart), actions.onStatistics),
            AboutRow(strings.databaseDump, aboutIcon(MaterialSymbols.Filled.Storage), actions.onDatabaseDump),
            AboutRow(strings.flightRecorder, aboutIcon(MaterialSymbols.Filled.NetworkCheck), actions.onFlightRecorder),
            AboutRow(strings.eventLogs, aboutIcon(MaterialSymbols.Filled.EventNote), actions.onEventLogs),
        ),
    )
}

@Composable
private fun AboutSection(title: String, accent: Color, rows: List<AboutRow>) {
    Column {
        // Headers use the theme's primary section style; [accent] only tints each section's icons.
        SectionHeader(title)
        RethinkListGroup {
            rows.forEachIndexed { index, row ->
                AboutListRow(row, accent, cardPositionFor(index, rows.lastIndex))
            }
        }
    }
}

@Composable
private fun AboutListRow(row: AboutRow, accent: Color, position: CardPosition) {
    RethinkListItem(
        headline = row.headline,
        position = position,
        onClick = row.onClick,
        trailing = row.trailing,
        leadingContent = {
            // One well for every About row, whatever the section: the shared tonal well and primary icon.
            val tint = MaterialTheme.colorScheme.primary
            RethinkIconWell(containerColor = RethinkIconWellContainerColor, contentColor = tint) { row.icon(tint) }
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
        SectionHeader(strings.connect)
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
                        { LoadingIndicator(Modifier.size(SharedDimensions.iconSizeLg), color = accent) }
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
            // Same 16dp inset and icon container as the rows above, so the leading column aligns.
            modifier = Modifier.fillMaxWidth().padding(start = SharedDimensions.spacingLg, end = SharedDimensions.spacingXs, top = SharedDimensions.spacingSm, bottom = SharedDimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            RethinkIconWell(MaterialSymbols.Filled.Share, tint = accent, containerColor = RethinkIconWellDefaults.tinted(accent), iconSize = SharedDimensions.iconSizeMd)
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
        modifier = Modifier.size(SharedDimensions.touchTargetMin).semantics { contentDescription = label },
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
        AboutPartnerLogo(
            label = strings.partnerMozilla,
            logo = assets.mozillaLogo,
            textStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.width(MozillaLogoWidth).padding(SharedDimensions.spacingXs),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            AboutPartnerLogo(
                label = strings.partnerFoss,
                logo = assets.fossLogo,
                textStyle = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .width(PartnerLogoWidth)
                    .heightIn(min = SharedDimensions.touchTargetMin)
                    .clip(logoShape)
                    .clickable(onClick = actions.onFoss)
                    .padding(SharedDimensions.spacingXs),
            )
            AboutPartnerLogo(
                label = strings.partnerFlossFunds,
                logo = assets.flossFundsLogo,
                textStyle = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .width(PartnerLogoWidth)
                    .heightIn(min = SharedDimensions.touchTargetMin)
                    .clip(logoShape)
                    .clickable(onClick = actions.onFlossFunds)
                    .padding(SharedDimensions.spacingXs),
            )
        }
    }
}

@Composable
private fun AboutPartnerLogo(
    label: String,
    logo: (@Composable (Modifier) -> Unit)?,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier,
) {
    // Logos are images without their own label, so the partner name is the accessible name.
    val labelled = modifier.semantics { contentDescription = label }
    if (logo != null) {
        logo(labelled)
    } else {
        Text(label, modifier = labelled, style = textStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SharedDimensions.touchTargetMin)
                    .clickable(onClick = actions.onToken)
                    .padding(bottom = SharedDimensions.spacingMd),
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
            Spacer(Modifier.height(SharedDimensions.spacingGridTile))
            Text(uiState.buildNumber, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().alpha(0.55f))
        }
    }
}
