package com.celzero.bravedns.ui.compose.about

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R

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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Column {
                Text(
                    text = stringResource(id = R.string.app_name_small_case),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alpha(0.5f)
                )
                Text(
                    text = stringResource(id = R.string.about_title_desc),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(0.5f)
                )
            }

            // Sponsorship Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(id = R.string.about_bravedns_explantion),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.about_bravedns_whoarewe),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.sponser_dialog_usage_msg, uiState.daysSinceInstall, uiState.sponsoredAmount),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onSponsorClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_heart_accent),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.about_sponsor_link_text))
                    }
                }
            }

            // Social & Bug Report Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
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
                modifier = Modifier.fillMaxWidth().alpha(0.75f).padding(top = 16.dp)
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color(0xFFA5D6A7), // Placeholder for accentGood
            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun AboutItem(title: String, iconId: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AboutSmallCard(title: String, iconId: Int, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = iconId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}
