package com.celzero.bravedns.ui.compose.dns

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.RethinkAnimatedSection
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            RethinkAnimatedSection(index = 0) {
                DnsHeader(
                    title = stringResource(id = R.string.dc_other_dns_heading),
                    isRefreshing = uiState.isRefreshing,
                    onRefreshClick = onRefreshClick
                )

                RethinkListGroup {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        DnsRadioButtonItem(
                            title = stringResource(id = R.string.network_dns),
                            selected = uiState.isSystemDnsEnabled,
                            onClick = onSystemDnsClick,
                            onInfoClick = onSystemDnsInfoClick
                        )
                        DnsRadioButtonItem(
                            title = stringResource(id = R.string.dc_custom_dns_radio),
                            selected = !uiState.isSystemDnsEnabled && !uiState.isRethinkDnsConnected && !uiState.isSmartDnsEnabled,
                            onClick = onCustomDnsClick,
                            showArrow = true
                        )
                        DnsRadioButtonItem(
                            title = stringResource(id = R.string.dc_rethink_dns_radio),
                            selected = uiState.isRethinkDnsConnected,
                            onClick = onRethinkPlusDnsClick,
                            showArrow = true
                        )
                        DnsRadioButtonItem(
                            title = stringResource(id = R.string.smart_dns),
                            selected = uiState.isSmartDnsEnabled,
                            onClick = onSmartDnsClick,
                            onInfoClick = onSmartDnsInfoClick
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
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
            }

            RethinkAnimatedSection(index = 1) {
                DnsHeader(title = stringResource(id = R.string.dc_block_heading))
                RethinkListGroup {
                    Column {
                        SettingsNavigationItem(
                            title = stringResource(id = R.string.dc_local_block_heading),
                            description = if (uiState.blocklistEnabled)
                                stringResource(id = R.string.settings_local_blocklist_in_use, uiState.numberOfLocalBlocklists)
                                else stringResource(id = R.string.dc_local_block_desc_1),
                            iconId = R.drawable.ic_local_blocklist,
                            onClick = onLocalBlocklistClick,
                            statusText = if (uiState.blocklistEnabled) stringResource(id = R.string.dc_local_block_enabled) else stringResource(id = R.string.lbl_disabled),
                            statusColor = if (uiState.blocklistEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.settings_custom_downloader_heading),
                            description = stringResource(id = R.string.settings_custom_downloader_desc),
                            iconId = R.drawable.ic_update,
                            checked = uiState.useCustomDownloadManager,
                            onCheckedChange = onCustomDownloaderChange
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.dc_check_update_heading),
                            description = stringResource(id = R.string.dc_check_update_desc),
                            iconId = R.drawable.ic_blocklist_update_check,
                            checked = uiState.periodicallyCheckBlocklistUpdate,
                            onCheckedChange = onPeriodicUpdateChange
                        )
                    }
                }
            }

            RethinkAnimatedSection(index = 2) {
                DnsHeader(title = stringResource(id = R.string.dc_filtering_heading))
                RethinkListGroup {
                    Column {
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.cd_dns_alg_heading),
                            description = stringResource(id = R.string.cd_dns_alg_desc),
                            iconId = R.drawable.ic_adv_dns_filter,
                            checked = uiState.enableDnsAlg,
                            onCheckedChange = onDnsAlgChange
                        )
                        if (uiState.isShowSplitDns) {
                            SettingsSwitchItem(
                                title = stringResource(id = R.string.cd_split_dns_heading),
                                description = stringResource(id = R.string.cd_split_dns_desc),
                                iconId = R.drawable.ic_split_dns,
                                checked = uiState.splitDns,
                                onCheckedChange = onSplitDnsChange
                            )
                        }
                        if (uiState.isShowBypassDnsBlock) {
                            SettingsSwitchItem(
                                title = stringResource(id = R.string.cd_treat_dns_rules_firewall_heading),
                                description = stringResource(id = R.string.cd_treat_dns_rules_firewall_desc),
                                iconId = R.drawable.ic_dns_rules_as_firewall,
                                checked = uiState.bypassBlockInDns,
                                onCheckedChange = onBypassDnsBlockChange
                            )
                        }
                        SettingsNavigationItem(
                            title = stringResource(id = R.string.cd_allowed_dns_record_types_heading),
                            description = stringResource(id = R.string.cd_allowed_dns_record_types_desc),
                            iconId = R.drawable.ic_allow_dns_records,
                            onClick = onAllowedRecordTypesClick,
                            statusText = if (uiState.dnsRecordTypesAutoMode) {
                                stringResource(id = R.string.dns_record_types_auto_mode_status)
                            } else {
                                "${uiState.allowedDnsRecordTypesSize} selected"
                            }
                        )
                    }
                }
            }

            RethinkAnimatedSection(index = 3) {
                DnsHeader(title = stringResource(id = R.string.lbl_advanced))
                RethinkListGroup {
                    Column {
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.dc_dns_website_heading),
                            description = stringResource(id = R.string.dc_dns_website_desc),
                            iconId = R.drawable.ic_fav_icon,
                            checked = uiState.fetchFavIcon,
                            onCheckedChange = onFavIconChange
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.dc_setting_dns_cache_heading),
                            description = stringResource(id = R.string.dc_setting_dns_cache_desc),
                            iconId = R.drawable.ic_auto_start,
                            checked = uiState.enableDnsCache,
                            onCheckedChange = onDnsCacheChange
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.dc_proxy_dns_heading),
                            description = stringResource(id = R.string.dc_proxy_dns_desc),
                            iconId = R.drawable.ic_proxy,
                            checked = !uiState.proxyDns, // Inverted in original code
                            onCheckedChange = { onProxyDnsChange(!it) }
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.dc_use_sys_dns_undelegated_heading),
                            description = stringResource(id = R.string.dc_use_sys_dns_undelegated_desc),
                            iconId = R.drawable.ic_split_dns,
                            checked = uiState.useSystemDnsForUndelegatedDomains,
                            onCheckedChange = onUndelegatedDomainsChange
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.use_fallback_dns_to_bypass),
                            description = stringResource(id = R.string.use_fallback_dns_to_bypass_desc),
                            iconId = R.drawable.ic_use_fallback_bypass,
                            checked = uiState.useFallbackDnsToBypass,
                            onCheckedChange = onFallbackChange
                        )
                        SettingsSwitchItem(
                            title = stringResource(id = R.string.dc_dns_leaks_heading),
                            description = stringResource(id = R.string.dc_dns_leaks_desc),
                            iconId = R.drawable.ic_prevent_dns_leaks,
                            checked = uiState.preventDnsLeaks,
                            onCheckedChange = onPreventLeaksChange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
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
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
        if (onRefreshClick != null) {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh_white),
                    contentDescription = stringResource(id = R.string.rules_load_failure_reload),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(rotation)
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
    onInfoClick: (() -> Unit)? = null,
    showArrow: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // handled by Row clickable
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onInfoClick != null) {
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_info_white_16),
                    contentDescription = stringResource(id = R.string.lbl_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        if (showArrow) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    iconId: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null // handled by Row clickable
        )
    }
}

@Composable
fun SettingsNavigationItem(
    title: String,
    description: String,
    iconId: Int,
    onClick: () -> Unit,
    statusText: String? = null,
    statusColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
