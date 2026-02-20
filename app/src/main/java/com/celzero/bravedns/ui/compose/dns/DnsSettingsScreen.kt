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
package com.celzero.bravedns.ui.compose.dns

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion

@Composable
fun DnsSettingsScreen(
    uiState: DnsSettingsUiState,
    onRefreshClick: () -> Unit,
    onSystemDnsClick: () -> Unit,
    onSystemDnsInfoClick: () -> Unit,
    onCustomDnsClick: () -> Unit,
    onRethinkPlusDnsClick: () -> Unit,
    onSmartDnsClick: () -> Unit,
    onSmartDnsInfoClick: () -> Unit,
    onLocalBlocklistClick: () -> Unit,
    onCustomDownloaderChange: (Boolean) -> Unit,
    onPeriodicUpdateChange: (Boolean) -> Unit,
    onDnsAlgChange: (Boolean) -> Unit,
    onSplitDnsChange: (Boolean) -> Unit,
    onBypassDnsBlockChange: (Boolean) -> Unit,
    onAllowedRecordTypesClick: () -> Unit,
    onFavIconChange: (Boolean) -> Unit,
    onDnsCacheChange: (Boolean) -> Unit,
    onProxyDnsChange: (Boolean) -> Unit,
    onUndelegatedDomainsChange: (Boolean) -> Unit,
    onFallbackChange: (Boolean) -> Unit,
    onPreventLeaksChange: (Boolean) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimensions.screenPaddingHorizontal)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            Spacer(modifier = Modifier.height(Dimensions.spacingSm))

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ),
                        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge)
                    )
                    .padding(horizontal = Dimensions.spacingXl, vertical = Dimensions.spacing2xl)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(id = R.string.lbl_dns),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.dns_mode_info_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // DNS Modes
            RethinkAnimatedSection(index = 0) {
                DnsHeader(
                    title = stringResource(id = R.string.dc_other_dns_heading),
                    isRefreshing = uiState.isRefreshing,
                    onRefreshClick = onRefreshClick
                )

                RethinkListGroup {
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.network_dns),
                        selected = uiState.isSystemDnsEnabled,
                        onClick = onSystemDnsClick,
                        onInfoClick = onSystemDnsInfoClick,
                        iconId = R.drawable.ic_network
                    )
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.dc_custom_dns_radio),
                        selected = !uiState.isSystemDnsEnabled && !uiState.isRethinkDnsConnected && !uiState.isSmartDnsEnabled,
                        onClick = onCustomDnsClick,
                        iconId = R.drawable.ic_filter
                    )
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.dc_rethink_dns_radio),
                        selected = uiState.isRethinkDnsConnected,
                        onClick = onRethinkPlusDnsClick,
                        iconId = R.drawable.ic_rethink_plus
                    )
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.smart_dns),
                        selected = uiState.isSmartDnsEnabled,
                        onClick = onSmartDnsClick,
                        onInfoClick = onSmartDnsInfoClick,
                        iconId = R.drawable.ic_dns_cache,
                        showDivider = false
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimensions.spacingXl, vertical = Dimensions.spacingSm),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier
                            .padding(horizontal = Dimensions.spacingXl, vertical = Dimensions.spacingSm)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.connectedDnsName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = uiState.dnsLatency,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Blocklists
            RethinkAnimatedSection(index = 1) {
                SectionHeader(title = stringResource(id = R.string.dc_block_heading))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.dc_local_block_heading),
                        supporting = if (uiState.blocklistEnabled)
                            stringResource(id = R.string.settings_local_blocklist_in_use, uiState.numberOfLocalBlocklists)
                            else stringResource(id = R.string.dc_local_block_desc_1),
                        leadingIconPainter = painterResource(id = R.drawable.ic_local_blocklist),
                        onClick = onLocalBlocklistClick,
                        trailing = {
                           Text(
                               text = if (uiState.blocklistEnabled) stringResource(id = R.string.dc_local_block_enabled) else stringResource(id = R.string.lbl_disabled),
                               style = MaterialTheme.typography.labelMedium,
                               color = if (uiState.blocklistEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                               fontWeight = FontWeight.Bold
                           )
                        }
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.settings_custom_downloader_heading),
                        description = stringResource(id = R.string.settings_custom_downloader_desc),
                        iconId = R.drawable.ic_update,
                        checked = uiState.useCustomDownloadManager,
                        onCheckedChange = onCustomDownloaderChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_check_update_heading),
                        description = stringResource(id = R.string.dc_check_update_desc),
                        iconId = R.drawable.ic_blocklist_update_check,
                        checked = uiState.periodicallyCheckBlocklistUpdate,
                        onCheckedChange = onPeriodicUpdateChange,
                        showDivider = false
                    )
                }
            }

            // Filtering
            RethinkAnimatedSection(index = 2) {
                SectionHeader(title = stringResource(id = R.string.dc_filtering_heading))
                RethinkListGroup {
                    ToggleListItem(
                        title = stringResource(id = R.string.cd_dns_alg_heading),
                        description = stringResource(id = R.string.cd_dns_alg_desc),
                        iconId = R.drawable.ic_adv_dns_filter,
                        checked = uiState.enableDnsAlg,
                        onCheckedChange = onDnsAlgChange
                    )
                    if (uiState.isShowSplitDns) {
                        ToggleListItem(
                            title = stringResource(id = R.string.cd_split_dns_heading),
                            description = stringResource(id = R.string.cd_split_dns_desc),
                            iconId = R.drawable.ic_split_dns,
                            checked = uiState.splitDns,
                            onCheckedChange = onSplitDnsChange
                        )
                    }
                    if (uiState.isShowBypassDnsBlock) {
                        ToggleListItem(
                            title = stringResource(id = R.string.cd_treat_dns_rules_firewall_heading),
                            description = stringResource(id = R.string.cd_treat_dns_rules_firewall_desc),
                            iconId = R.drawable.ic_dns_rules_as_firewall,
                            checked = uiState.bypassBlockInDns,
                            onCheckedChange = onBypassDnsBlockChange
                        )
                    }
                    RethinkListItem(
                        headline = stringResource(id = R.string.cd_allowed_dns_record_types_heading),
                        supporting = stringResource(id = R.string.cd_allowed_dns_record_types_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_allow_dns_records),
                        onClick = onAllowedRecordTypesClick,
                        showDivider = false,
                        trailing = {
                            Text(
                                text = if (uiState.dnsRecordTypesAutoMode) {
                                    stringResource(id = R.string.dns_record_types_auto_mode_status)
                                } else {
                                    "${uiState.allowedDnsRecordTypesSize}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            // Advanced
            RethinkAnimatedSection(index = 3) {
                SectionHeader(title = stringResource(id = R.string.lbl_advanced))
                RethinkListGroup {
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_dns_website_heading),
                        description = stringResource(id = R.string.dc_dns_website_desc),
                        iconId = R.drawable.ic_fav_icon,
                        checked = uiState.fetchFavIcon,
                        onCheckedChange = onFavIconChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_setting_dns_cache_heading),
                        description = stringResource(id = R.string.dc_setting_dns_cache_desc),
                        iconId = R.drawable.ic_auto_start,
                        checked = uiState.enableDnsCache,
                        onCheckedChange = onDnsCacheChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_proxy_dns_heading),
                        description = stringResource(id = R.string.dc_proxy_dns_desc),
                        iconId = R.drawable.ic_proxy,
                        checked = !uiState.proxyDns,
                        onCheckedChange = { onProxyDnsChange(!it) }
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_use_sys_dns_undelegated_heading),
                        description = stringResource(id = R.string.dc_use_sys_dns_undelegated_desc),
                        iconId = R.drawable.ic_split_dns,
                        checked = uiState.useSystemDnsForUndelegatedDomains,
                        onCheckedChange = onUndelegatedDomainsChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.use_fallback_dns_to_bypass),
                        description = stringResource(id = R.string.use_fallback_dns_to_bypass_desc),
                        iconId = R.drawable.ic_use_fallback_bypass,
                        checked = uiState.useFallbackDnsToBypass,
                        onCheckedChange = onFallbackChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_dns_leaks_heading),
                        description = stringResource(id = R.string.dc_dns_leaks_desc),
                        iconId = R.drawable.ic_prevent_dns_leaks,
                        checked = uiState.preventDnsLeaks,
                        onCheckedChange = onPreventLeaksChange,
                        showDivider = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacing3xl))
        }
    }
}

@Composable
fun DnsHeader(
    title: String,
    isRefreshing: Boolean = false,
    onRefreshClick: (() -> Unit)? = null
) {
    val reducedMotion = rememberReducedMotion()
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing && !reducedMotion) 360f else 0f,
        animationSpec = if (isRefreshing && !reducedMotion) {
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(0)
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimensions.spacingMd,
                end = Dimensions.spacingSm,
                top = Dimensions.spacingLg,
                bottom = Dimensions.spacingSm
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
        if (onRefreshClick != null) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh_white),
                    contentDescription = stringResource(id = R.string.rules_load_failure_reload),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(rotation).size(18.dp)
                )
            }
        }
    }
}

@Composable
fun DnsRadioButtonItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconId: Int,
    onInfoClick: (() -> Unit)? = null,
    showDivider: Boolean = true
) {
    RethinkListItem(
        headline = title,
        leadingIconPainter = painterResource(id = iconId),
        onClick = onClick,
        showDivider = showDivider,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_info_white_16),
                            contentDescription = stringResource(id = R.string.lbl_info),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                RadioButton(
                    selected = selected,
                    onClick = onClick
                )
            }
        }
    )
}

@Composable
fun ToggleListItem(
    title: String,
    description: String,
    iconId: Int,
    checked: Boolean,
    showDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIconPainter = painterResource(id = iconId),
        showDivider = showDivider,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
