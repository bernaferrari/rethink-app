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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.CardPosition
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SectionHeader
import com.celzero.bravedns.ui.compose.theme.rememberReducedMotion

@OptIn(ExperimentalMaterial3Api::class)
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
    val reducedMotion = rememberReducedMotion()
    val refreshRotation by animateFloatAsState(
        targetValue = if (uiState.isRefreshing && !reducedMotion) 360f else 0f,
        animationSpec = if (uiState.isRefreshing && !reducedMotion) {
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "dnsRefreshRotation"
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RethinkLargeTopBar(
                title = stringResource(id = R.string.lbl_dns),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onRefreshClick) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh_white),
                            contentDescription = stringResource(id = R.string.rules_load_failure_reload),
                            modifier = Modifier.rotate(refreshRotation)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacing3xl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLg)
        ) {
            item {
                SectionHeader(title = stringResource(id = R.string.dc_other_dns_heading))
                RethinkListGroup {
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.network_dns),
                        selected = uiState.isSystemDnsEnabled,
                        onClick = onSystemDnsClick,
                        onInfoClick = onSystemDnsInfoClick,
                        iconId = R.drawable.ic_network,
                        position = CardPosition.First
                    )
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.dc_custom_dns_radio),
                        selected = !uiState.isSystemDnsEnabled && !uiState.isRethinkDnsConnected && !uiState.isSmartDnsEnabled,
                        onClick = onCustomDnsClick,
                        iconId = R.drawable.ic_filter,
                        position = CardPosition.Middle
                    )
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.dc_rethink_dns_radio),
                        selected = uiState.isRethinkDnsConnected,
                        onClick = onRethinkPlusDnsClick,
                        iconId = R.drawable.ic_rethink_plus,
                        position = CardPosition.Middle
                    )
                    DnsRadioButtonItem(
                        title = stringResource(id = R.string.smart_dns),
                        selected = uiState.isSmartDnsEnabled,
                        onClick = onSmartDnsClick,
                        onInfoClick = onSmartDnsInfoClick,
                        iconId = R.drawable.ic_dns_cache,
                        position = CardPosition.Last
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = Dimensions.spacingXl,
                            vertical = Dimensions.spacingSm
                        ),
                        thickness = Dimensions.dividerThickness,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = Dimensions.spacingXl,
                                vertical = Dimensions.spacingSm
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.connectedDnsName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.spacingSm))
                        Text(
                            text = uiState.dnsLatency.ifEmpty { "--" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                SectionHeader(title = stringResource(id = R.string.dc_block_heading))
                RethinkListGroup {
                    RethinkListItem(
                        headline = stringResource(id = R.string.dc_local_block_heading),
                        supporting = if (uiState.blocklistEnabled) {
                            stringResource(
                                id = R.string.settings_local_blocklist_in_use,
                                uiState.numberOfLocalBlocklists
                            )
                        } else {
                            stringResource(id = R.string.dc_local_block_desc_1)
                        },
                        leadingIconPainter = painterResource(id = R.drawable.ic_local_blocklist),
                        position = CardPosition.First,
                        onClick = onLocalBlocklistClick,
                        trailing = {
                            Text(
                                text = if (uiState.blocklistEnabled) {
                                    stringResource(id = R.string.dc_local_block_enabled)
                                } else {
                                    stringResource(id = R.string.lbl_disabled)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (uiState.blocklistEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.settings_custom_downloader_heading),
                        description = stringResource(id = R.string.settings_custom_downloader_desc),
                        iconId = R.drawable.ic_update,
                        checked = uiState.useCustomDownloadManager,
                        position = CardPosition.Middle,
                        onCheckedChange = onCustomDownloaderChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_check_update_heading),
                        description = stringResource(id = R.string.dc_check_update_desc),
                        iconId = R.drawable.ic_blocklist_update_check,
                        checked = uiState.periodicallyCheckBlocklistUpdate,
                        position = CardPosition.Last,
                        onCheckedChange = onPeriodicUpdateChange
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(id = R.string.dc_filtering_heading))
                RethinkListGroup {
                    ToggleListItem(
                        title = stringResource(id = R.string.cd_dns_alg_heading),
                        description = stringResource(id = R.string.cd_dns_alg_desc),
                        iconId = R.drawable.ic_adv_dns_filter,
                        checked = uiState.enableDnsAlg,
                        position = CardPosition.First,
                        onCheckedChange = onDnsAlgChange
                    )
                    if (uiState.isShowSplitDns) {
                        ToggleListItem(
                            title = stringResource(id = R.string.cd_split_dns_heading),
                            description = stringResource(id = R.string.cd_split_dns_desc),
                            iconId = R.drawable.ic_split_dns,
                            checked = uiState.splitDns,
                            position = CardPosition.Middle,
                            onCheckedChange = onSplitDnsChange
                        )
                    }
                    if (uiState.isShowBypassDnsBlock) {
                        ToggleListItem(
                            title = stringResource(id = R.string.cd_treat_dns_rules_firewall_heading),
                            description = stringResource(id = R.string.cd_treat_dns_rules_firewall_desc),
                            iconId = R.drawable.ic_dns_rules_as_firewall,
                            checked = uiState.bypassBlockInDns,
                            position = CardPosition.Middle,
                            onCheckedChange = onBypassDnsBlockChange
                        )
                    }
                    RethinkListItem(
                        headline = stringResource(id = R.string.cd_allowed_dns_record_types_heading),
                        supporting = stringResource(id = R.string.cd_allowed_dns_record_types_desc),
                        leadingIconPainter = painterResource(id = R.drawable.ic_allow_dns_records),
                        position = CardPosition.Last,
                        onClick = onAllowedRecordTypesClick,
                        trailing = {
                            Text(
                                text = if (uiState.dnsRecordTypesAutoMode) {
                                    stringResource(id = R.string.dns_record_types_auto_mode_status)
                                } else {
                                    uiState.allowedDnsRecordTypesSize.toString()
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }

            item {
                SectionHeader(title = stringResource(id = R.string.lbl_advanced))
                RethinkListGroup {
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_dns_website_heading),
                        description = stringResource(id = R.string.dc_dns_website_desc),
                        iconId = R.drawable.ic_fav_icon,
                        checked = uiState.fetchFavIcon,
                        position = CardPosition.First,
                        onCheckedChange = onFavIconChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_setting_dns_cache_heading),
                        description = stringResource(id = R.string.dc_setting_dns_cache_desc),
                        iconId = R.drawable.ic_auto_start,
                        checked = uiState.enableDnsCache,
                        position = CardPosition.Middle,
                        onCheckedChange = onDnsCacheChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_proxy_dns_heading),
                        description = stringResource(id = R.string.dc_proxy_dns_desc),
                        iconId = R.drawable.ic_proxy,
                        checked = !uiState.proxyDns,
                        position = CardPosition.Middle,
                        onCheckedChange = { onProxyDnsChange(!it) }
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_use_sys_dns_undelegated_heading),
                        description = stringResource(id = R.string.dc_use_sys_dns_undelegated_desc),
                        iconId = R.drawable.ic_split_dns,
                        checked = uiState.useSystemDnsForUndelegatedDomains,
                        position = CardPosition.Middle,
                        onCheckedChange = onUndelegatedDomainsChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.use_fallback_dns_to_bypass),
                        description = stringResource(id = R.string.use_fallback_dns_to_bypass_desc),
                        iconId = R.drawable.ic_use_fallback_bypass,
                        checked = uiState.useFallbackDnsToBypass,
                        position = CardPosition.Middle,
                        onCheckedChange = onFallbackChange
                    )
                    ToggleListItem(
                        title = stringResource(id = R.string.dc_dns_leaks_heading),
                        description = stringResource(id = R.string.dc_dns_leaks_desc),
                        iconId = R.drawable.ic_prevent_dns_leaks,
                        checked = uiState.preventDnsLeaks,
                        position = CardPosition.Last,
                        onCheckedChange = onPreventLeaksChange
                    )
                }
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
    position: CardPosition = CardPosition.Middle
) {
    RethinkListItem(
        headline = title,
        leadingIconPainter = painterResource(id = iconId),
        onClick = onClick,
        position = position,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_info_white_16),
                            contentDescription = stringResource(id = R.string.lbl_info),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
    position: CardPosition = CardPosition.Middle,
    onCheckedChange: (Boolean) -> Unit
) {
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIconPainter = painterResource(id = iconId),
        position = position,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
