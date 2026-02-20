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
package com.celzero.bravedns.ui.compose.configure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.SectionHeader

@Composable
fun ConfigureScreen(
    isDebug: Boolean,
    onAppsClick: () -> Unit,
    onDnsClick: () -> Unit,
    onFirewallClick: () -> Unit,
    onProxyClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onOthersClick: () -> Unit,
    onLogsClick: () -> Unit,
    onAntiCensorshipClick: () -> Unit,
    onAdvancedClick: () -> Unit
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
        ) {
            // Expressive gradient header
            RethinkAnimatedSection(index = 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(
                            start = Dimensions.screenPaddingHorizontal,
                            end = Dimensions.screenPaddingHorizontal,
                            top = Dimensions.spacing2xl,
                            bottom = Dimensions.spacingLg
                        )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(id = R.string.lbl_configure),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.settings_title_desc),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Protection section — 2x2 grid of expressive cards
            RethinkAnimatedSection(index = 1) {
                Column(modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)) {
                    SectionHeader(title = stringResource(R.string.lbl_protection))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
                    ) {
                        ConfigureCard(
                            title = stringResource(id = R.string.lbl_apps),
                            subtitle = stringResource(id = R.string.apps_info_title),
                            icon = painterResource(id = R.drawable.ic_app_info_accent),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                            onClick = onAppsClick
                        )
                        ConfigureCard(
                            title = stringResource(id = R.string.lbl_dns),
                            subtitle = stringResource(id = R.string.dns_mode_info_title),
                            icon = painterResource(id = R.drawable.dns_home_screen),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            onContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                            onClick = onDnsClick
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimensions.spacingMd))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
                    ) {
                        ConfigureCard(
                            title = stringResource(id = R.string.lbl_firewall),
                            subtitle = stringResource(id = R.string.firewall_mode_info_title),
                            icon = painterResource(id = R.drawable.firewall_home_screen),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            onContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                            onClick = onFirewallClick
                        )
                        ConfigureCard(
                            title = stringResource(id = R.string.lbl_proxy),
                            subtitle = stringResource(id = R.string.cd_custom_dns_proxy_name_default),
                            icon = painterResource(id = R.drawable.ic_proxy),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                            onContainerColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            onClick = onProxyClick
                        )
                    }
                }
            }

            // System section — horizontal list items in a grouped surface
            RethinkAnimatedSection(index = 2) {
                Column(modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)) {
                    SectionHeader(title = stringResource(R.string.lbl_system))
                    Surface(
                        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = Dimensions.spacingSm)) {
                            ConfigureListItem(
                                title = stringResource(id = R.string.lbl_network),
                                subtitle = stringResource(id = R.string.firewall_act_network_monitor_tab),
                                icon = painterResource(id = R.drawable.ic_network_tunnel),
                                onClick = onNetworkClick
                            )
                            ConfigureListItem(
                                title = stringResource(id = R.string.title_settings),
                                subtitle = stringResource(id = R.string.settings_general_header),
                                icon = painterResource(id = R.drawable.ic_other_settings),
                                onClick = onOthersClick
                            )
                            ConfigureListItem(
                                title = stringResource(id = R.string.lbl_logs),
                                subtitle = stringResource(id = R.string.settings_enable_logs_desc),
                                icon = painterResource(id = R.drawable.ic_logs_accent),
                                showDivider = false,
                                onClick = onLogsClick
                            )
                        }
                    }
                }
            }

            // Advanced section
            RethinkAnimatedSection(index = 3) {
                Column(modifier = Modifier.padding(horizontal = Dimensions.screenPaddingHorizontal)) {
                    SectionHeader(title = stringResource(R.string.lbl_advanced))
                    Surface(
                        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = Dimensions.spacingSm)) {
                            ConfigureListItem(
                                title = stringResource(id = R.string.anti_censorship_title),
                                subtitle = stringResource(id = R.string.anti_censorship_desc),
                                icon = painterResource(id = R.drawable.ic_anti_dpi),
                                showDivider = isDebug,
                                onClick = onAntiCensorshipClick
                            )
                            if (isDebug) {
                                ConfigureListItem(
                                    title = stringResource(id = R.string.lbl_advanced),
                                    subtitle = stringResource(id = R.string.adv_set_experimental_desc),
                                    icon = painterResource(id = R.drawable.ic_advanced_settings),
                                    showDivider = false,
                                    onClick = onAdvancedClick
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
        }
    }
}

@Composable
private fun ConfigureCard(
    title: String,
    subtitle: String,
    icon: Painter,
    containerColor: Color,
    onContainerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        color = containerColor,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = onContainerColor.copy(alpha = 0.12f)
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = onContainerColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainerColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerColor.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun ConfigureListItem(
    title: String,
    subtitle: String,
    icon: Painter,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = Dimensions.spacingLg, vertical = Dimensions.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_right_arrow_white),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 76.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
        }
    }
}
