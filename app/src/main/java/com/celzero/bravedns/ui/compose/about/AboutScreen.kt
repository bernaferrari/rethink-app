package com.celzero.bravedns.ui.compose.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader

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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal),
                shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.spacingXl),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                ) {
                    Text(
                        text = stringResource(id = R.string.title_about),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.about_title_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
            ) {

                // Sponsorship Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)),
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(Dimensions.spacingXl)) {
                        Text(
                            text = stringResource(id = R.string.about_bravedns_explantion),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spacingMd))
                        Text(
                            text = stringResource(id = R.string.about_bravedns_whoarewe),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spacingMd))
                        Text(
                            text = stringResource(id = R.string.sponser_dialog_usage_msg, uiState.daysSinceInstall, uiState.sponsoredAmount),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(Dimensions.spacingXl))
                        Button(
                            onClick = onSponsorClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_heart_accent),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.about_sponsor_link_text),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Social & Bug Report Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AboutSmallCard(
                            title = stringResource(id = R.string.about_join_telegram),
                            iconId = R.drawable.ic_telegram,
                            onClick = onTelegramClick
                        )
                        Text(
                            text = stringResource(id = R.string.about_bravedns_toreport_text),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (uiState.isBugReportRunning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(id = R.string.collecting_logs_progress_text), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            AboutSmallCard(
                                title = stringResource(id = R.string.about_bug_report),
                                iconId = R.drawable.ic_android_icon,
                                onClick = onBugReportClick
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.about_bug_report_desc),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }

                // Sections
                AboutSection(title = stringResource(id = R.string.about_app)) {
                    AboutItem(stringResource(id = R.string.about_whats_new, uiState.slicedVersion), R.drawable.ic_whats_new, onWhatsNewClick)
                    if (!uiState.isFdroid) {
                        AboutItem(stringResource(id = R.string.about_app_update_check), R.drawable.ic_update, onAppUpdateClick)
                    }
                    AboutItem(stringResource(id = R.string.about_app_contributors), R.drawable.ic_authors, onContributorsClick)
                    AboutItem(stringResource(id = R.string.about_app_translate), R.drawable.ic_translate, onTranslateClick)
                }

                AboutSection(title = stringResource(id = R.string.about_web)) {
                    AboutItem(stringResource(id = R.string.about_website), R.drawable.ic_website, onWebsiteClick)
                    AboutItem(stringResource(id = R.string.about_github), R.drawable.ic_github, onGithubClick)
                    AboutItem(stringResource(id = R.string.about_faq), R.drawable.ic_faq, onFaqClick)
                    AboutItem(stringResource(id = R.string.about_docs), R.drawable.ic_blog, onDocsClick)
                    AboutItem(stringResource(id = R.string.about_privacy_policy), R.drawable.ic_privacy_policy, onPrivacyPolicyClick)
                    AboutItem(stringResource(id = R.string.about_terms_of_service), R.drawable.ic_terms_service, onTermsOfServiceClick)
                    AboutItem(stringResource(id = R.string.about_license), R.drawable.ic_terms_service, onLicenseClick)
                }

                AboutSection(title = stringResource(id = R.string.about_connect)) {
                    AboutItem(stringResource(id = R.string.about_twitter), R.drawable.ic_twitter, onTwitterClick)
                    AboutItem(stringResource(id = R.string.about_email), R.drawable.ic_mail, onEmailClick)
                    AboutItem(stringResource(id = R.string.lbl_reddit), R.drawable.ic_reddit, onRedditClick)
                    AboutItem(stringResource(id = R.string.lbl_matrix), R.drawable.ic_element, onElementClick)
                    AboutItem(stringResource(id = R.string.lbl_mastodon), R.drawable.ic_mastodon, onMastodonClick)
                }

                AboutSection(title = stringResource(id = R.string.about_settings)) {
                    AboutItem(stringResource(id = R.string.about_settings_app_info), R.drawable.ic_app_info, onAppInfoClick)
                    AboutItem(stringResource(id = R.string.about_settings_vpn_profile), R.drawable.ic_about_key, onVpnProfileClick)
                    AboutItem(stringResource(id = R.string.about_settings_notification), R.drawable.ic_notification, onNotificationClick)
                }

                AboutSection(title = stringResource(id = R.string.title_statistics)) {
                    AboutItem(stringResource(id = R.string.settings_general_header), R.drawable.ic_log_level, onStatsClick)
                    AboutItem(stringResource(id = R.string.title_database_dump), R.drawable.ic_backup, onDbStatsClick)
                    if (uiState.isDebug) {
                        AboutItem("Flight Recorder", R.drawable.ic_backup, onFlightRecordClick)
                    }
                    AboutItem(stringResource(id = R.string.event_logs_title), R.drawable.ic_event_note, onEventLogsClick)
                }

                // Logos
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mozilla),
                        contentDescription = null,
                        modifier = Modifier.width(160.dp),
                        contentScale = ContentScale.FillWidth
                    )
                    Image(
                        painter = painterResource(id = R.drawable.foss_logo),
                        contentDescription = null,
                        modifier = Modifier.width(160.dp).height(60.dp).clickable { onFossClick() },
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_floss_fund_badge),
                        contentDescription = null,
                        modifier = Modifier.width(160.dp).height(60.dp).clickable { onFlossFundsClick() },
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = stringResource(id = R.string.about_mozilla),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(0.7f)
                )

                // Version info
                Text(
                    text = stringResource(id = R.string.about_version_install_source, uiState.versionName, uiState.installSource) +
                            "\n${uiState.buildNumber}\n${uiState.lastUpdated}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(0.75f).padding(top = Dimensions.spacingLg)
                )

                if (uiState.isFirebaseEnabled && !uiState.isFdroid) {
                    Text(
                        text = uiState.firebaseToken,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(0.75f)
                            .padding(vertical = 16.dp)
                            .clickable { onTokenClick() }
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
            }
        }
    }
}

@Composable
fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionHeader(title = title)
        RethinkListGroup(content = content)
    }
}

@Composable
fun AboutItem(title: String, iconId: Int, onClick: () -> Unit) {
    RethinkListItem(
        headline = title,
        leadingIconPainter = painterResource(id = iconId),
        onClick = onClick
    )
}

@Composable
fun AboutSmallCard(title: String, iconId: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimensions.spacingLg,
                vertical = Dimensions.spacingLg
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = iconId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
