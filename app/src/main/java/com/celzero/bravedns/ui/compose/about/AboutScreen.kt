/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
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
    onFlossFundsClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(id = R.string.title_about),
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingSm,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {

            // ── App identity hero card ────────────────────────────────────
            item { AppHeroCard(uiState) }

            // ── Sponsor / support card ────────────────────────────────────
            item { SponsorCard(uiState, onSponsorClick) }

            // ── Quick actions row (Telegram + Bug Report) ─────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
                ) {
                    QuickActionCard(
                        title = stringResource(id = R.string.about_join_telegram),
                        iconId = R.drawable.ic_telegram,
                        modifier = Modifier.weight(1f),
                        onClick = onTelegramClick
                    )
                    if (uiState.isBugReportRunning) {
                        BugReportLoadingCard(modifier = Modifier.weight(1f))
                    } else {
                        QuickActionCard(
                            title = stringResource(id = R.string.about_bug_report),
                            iconId = R.drawable.ic_android_icon,
                            modifier = Modifier.weight(1f),
                            onClick = onBugReportClick
                        )
                    }
                }
            }

            // ── App section ───────────────────────────────────────────────
            item {
                SectionHeader(title = stringResource(id = R.string.about_app))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_whats_new, uiState.slicedVersion),
                        leadingIconPainter = painterResource(id = R.drawable.ic_whats_new),
                        position = CardPosition.First,
                        onClick = onWhatsNewClick
                    )
                    if (!uiState.isFdroid) {
                        RethinkListItem(
                            headline = stringResource(id = R.string.about_app_update_check),
                            leadingIconPainter = painterResource(id = R.drawable.ic_update),
                            position = CardPosition.Middle,
                            onClick = onAppUpdateClick
                        )
                    }
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_app_contributors),
                        leadingIconPainter = painterResource(id = R.drawable.ic_authors),
                        position = CardPosition.Middle,
                        onClick = onContributorsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_app_translate),
                        leadingIconPainter = painterResource(id = R.drawable.ic_translate),
                        position = CardPosition.Last,
                        onClick = onTranslateClick
                    )
                }
            }

            // ── Web section ───────────────────────────────────────────────
            item {
                SectionHeader(title = stringResource(id = R.string.about_web))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_website),
                        leadingIconPainter = painterResource(id = R.drawable.ic_website),
                        position = CardPosition.First,
                        onClick = onWebsiteClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_github),
                        leadingIconPainter = painterResource(id = R.drawable.ic_github),
                        position = CardPosition.Middle,
                        onClick = onGithubClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_faq),
                        leadingIconPainter = painterResource(id = R.drawable.ic_faq),
                        position = CardPosition.Middle,
                        onClick = onFaqClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_docs),
                        leadingIconPainter = painterResource(id = R.drawable.ic_blog),
                        position = CardPosition.Middle,
                        onClick = onDocsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_privacy_policy),
                        leadingIconPainter = painterResource(id = R.drawable.ic_privacy_policy),
                        position = CardPosition.Middle,
                        onClick = onPrivacyPolicyClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_terms_of_service),
                        leadingIconPainter = painterResource(id = R.drawable.ic_terms_service),
                        position = CardPosition.Middle,
                        onClick = onTermsOfServiceClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_license),
                        leadingIconPainter = painterResource(id = R.drawable.ic_terms_service),
                        position = CardPosition.Last,
                        onClick = onLicenseClick
                    )
                }
            }

            // ── Connect / community section ───────────────────────────────
            item {
                SectionHeader(title = stringResource(id = R.string.about_connect))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_twitter),
                        leadingIconPainter = painterResource(id = R.drawable.ic_twitter),
                        position = CardPosition.First,
                        onClick = onTwitterClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_email),
                        leadingIconPainter = painterResource(id = R.drawable.ic_mail),
                        position = CardPosition.Middle,
                        onClick = onEmailClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_reddit),
                        leadingIconPainter = painterResource(id = R.drawable.ic_reddit),
                        position = CardPosition.Middle,
                        onClick = onRedditClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_matrix),
                        leadingIconPainter = painterResource(id = R.drawable.ic_element),
                        position = CardPosition.Middle,
                        onClick = onElementClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.lbl_mastodon),
                        leadingIconPainter = painterResource(id = R.drawable.ic_mastodon),
                        position = CardPosition.Last,
                        onClick = onMastodonClick
                    )
                }
            }

            // ── System settings section ───────────────────────────────────
            item {
                SectionHeader(title = stringResource(id = R.string.about_settings))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_settings_app_info),
                        leadingIconPainter = painterResource(id = R.drawable.ic_app_info),
                        position = CardPosition.First,
                        onClick = onAppInfoClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_settings_vpn_profile),
                        leadingIconPainter = painterResource(id = R.drawable.ic_about_key),
                        position = CardPosition.Middle,
                        onClick = onVpnProfileClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.about_settings_notification),
                        leadingIconPainter = painterResource(id = R.drawable.ic_notification),
                        position = CardPosition.Last,
                        onClick = onNotificationClick
                    )
                }
            }

            // ── Debug / diagnostics section ───────────────────────────────
            item {
                SectionHeader(title = stringResource(id = R.string.title_statistics))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.settings_general_header),
                        leadingIconPainter = painterResource(id = R.drawable.ic_log_level),
                        position = CardPosition.First,
                        onClick = onStatsClick
                    )
                    RethinkListItem(
                        headline = stringResource(id = R.string.title_database_dump),
                        leadingIconPainter = painterResource(id = R.drawable.ic_backup),
                        position = CardPosition.Middle,
                        onClick = onDbStatsClick
                    )
                    if (uiState.isDebug) {
                        RethinkListItem(
                            headline = "Flight Recorder",
                            leadingIconPainter = painterResource(id = R.drawable.ic_backup),
                            position = CardPosition.Middle,
                            onClick = onFlightRecordClick
                        )
                    }
                    RethinkListItem(
                        headline = stringResource(id = R.string.event_logs_title),
                        leadingIconPainter = painterResource(id = R.drawable.ic_event_note),
                        position = CardPosition.Last,
                        onClick = onEventLogsClick
                    )
                }
            }

            // ── Partner logos ─────────────────────────────────────────────
            item { PartnerLogosCard(onFossClick, onFlossFundsClick) }

            // ── Version footer ────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.isFirebaseEnabled && !uiState.isFdroid) {
                        Text(
                            text = uiState.firebaseToken,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.5f)
                                .padding(bottom = Dimensions.spacingMd)
                                .clickable { onTokenClick() }
                        )
                    }
                    Text(
                        text = "${uiState.versionName} · ${uiState.installSource}",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().alpha(0.65f)
                    )
                    if (uiState.buildNumber.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.buildNumber,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().alpha(0.45f)
                        )
                    }
                }
            }
        }
    }
}

// ─── App Identity Hero Card ────────────────────────────────────────────────────

@Composable
private fun AppHeroCard(uiState: AboutUiState) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App icon with rounded container
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.about_title_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                if (uiState.versionName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "v${uiState.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Sponsor Card ─────────────────────────────────────────────────────────────

@Composable
private fun SponsorCard(uiState: AboutUiState, onSponsorClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.about_bravedns_explantion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(
                    id = R.string.sponser_dialog_usage_msg,
                    uiState.daysSinceInstall,
                    uiState.sponsoredAmount
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Button(
                onClick = onSponsorClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.about_sponsor_link_text),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Quick Action Card ─────────────────────────────────────────────────────────

@Composable
private fun QuickActionCard(
    title: String,
    iconId: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = iconId),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BugReportLoadingCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(id = R.string.collecting_logs_progress_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ─── Partner Logos Card ────────────────────────────────────────────────────────

@Composable
private fun PartnerLogosCard(onFossClick: () -> Unit, onFlossFundsClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.about_mozilla),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.75f)
            )
            Image(
                painter = painterResource(id = R.drawable.mozilla),
                contentDescription = null,
                modifier = Modifier.width(140.dp),
                contentScale = ContentScale.FillWidth
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.foss_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .height(44.dp)
                        .clickable { onFossClick() },
                    contentScale = ContentScale.Fit
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_floss_fund_badge),
                    contentDescription = null,
                    modifier = Modifier
                        .width(120.dp)
                        .height(44.dp)
                        .clickable { onFlossFundsClick() },
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
