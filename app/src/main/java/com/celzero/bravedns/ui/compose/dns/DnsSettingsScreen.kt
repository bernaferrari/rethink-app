/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.service.BlockFreeDnsMode

/** Android preference/resolver adapter for the common DNS-settings renderer. */
@Composable
fun DnsSettingsScreen(
    uiState: DnsSettingsUiState,
    initialFocusKey: String? = null,
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
    onBlockFreeDnsModeChange: (BlockFreeDnsMode) -> Unit,
    onBlockFreeDnsClick: () -> Unit,
    onPreventLeaksChange: (Boolean) -> Unit,
) {
    RethinkDnsSettingsScreen(
        state = uiState.toRethinkDnsSettingsState(),
        strings = androidDnsSettingsStrings(),
        onRefresh = onRefreshClick,
        onSystemDns = onSystemDnsClick,
        onSystemDnsInfo = onSystemDnsInfoClick,
        onCustomDns = onCustomDnsClick,
        onRethinkDns = onRethinkPlusDnsClick,
        onSmartDns = onSmartDnsClick,
        onSmartDnsInfo = onSmartDnsInfoClick,
        onLocalBlocklists = onLocalBlocklistClick,
        onCustomDownloaderChange = onCustomDownloaderChange,
        onPeriodicUpdateChange = onPeriodicUpdateChange,
        onDnsAlgChange = onDnsAlgChange,
        onSplitDnsChange = onSplitDnsChange,
        onRulesAsFirewallChange = onBypassDnsBlockChange,
        onRecordTypes = onAllowedRecordTypesClick,
        onFaviconsChange = onFavIconChange,
        onDnsCacheChange = onDnsCacheChange,
        onProxyDnsChange = onProxyDnsChange,
        onUndelegatedDomainsChange = onUndelegatedDomainsChange,
        onFallbackDnsChange = onFallbackChange,
        onBlockFreeModeChange = { onBlockFreeDnsModeChange(it.toAndroidBlockFreeDnsMode()) },
        onTrustedEndpoint = onBlockFreeDnsClick,
        onPreventLeaksChange = onPreventLeaksChange,
        focusedSettingId = initialFocusKey,
    )
}

private fun DnsSettingsUiState.toRethinkDnsSettingsState() = RethinkDnsSettingsState(
    connectedDnsName = connectedDnsName,
    dnsLatency = dnsLatency,
    dnsType = dnsType.toRethinkDnsType(),
    isSmartDnsEnabled = isSmartDnsEnabled,
    isSystemDnsEnabled = isSystemDnsEnabled,
    isRethinkDnsConnected = isRethinkDnsConnected,
    fetchFavIcon = fetchFavIcon,
    preventDnsLeaks = preventDnsLeaks,
    enableDnsAlg = enableDnsAlg,
    periodicallyCheckBlocklistUpdate = periodicallyCheckBlocklistUpdate,
    useCustomDownloadManager = useCustomDownloadManager,
    enableDnsCache = enableDnsCache,
    proxyDns = proxyDns,
    useSystemDnsForUndelegatedDomains = useSystemDnsForUndelegatedDomains,
    useFallbackDnsToBypass = useFallbackDnsToBypass,
    blockFreeDnsMode = blockFreeDnsMode.toRethinkBlockFreeDnsMode(),
    blocklistEnabled = blocklistEnabled,
    numberOfLocalBlocklists = numberOfLocalBlocklists,
    bypassBlockInDns = bypassBlockInDns,
    splitDns = splitDns,
    dnsRecordTypesAutoMode = dnsRecordTypesAutoMode,
    allowedDnsRecordTypesSize = allowedDnsRecordTypesSize,
    showSplitDns = isShowSplitDns,
    showBypassDnsBlock = isShowBypassDnsBlock,
    isRefreshing = isRefreshing,
)

private fun AppConfig.DnsType.toRethinkDnsType() = when (this) {
    AppConfig.DnsType.DOH -> RethinkDnsType.Doh
    AppConfig.DnsType.RETHINK_REMOTE -> RethinkDnsType.RethinkRemote
    AppConfig.DnsType.SMART_DNS -> RethinkDnsType.SmartDns
    AppConfig.DnsType.DOT -> RethinkDnsType.Dot
    AppConfig.DnsType.ODOH -> RethinkDnsType.Odoh
    AppConfig.DnsType.DNSCRYPT -> RethinkDnsType.DnsCrypt
    AppConfig.DnsType.DNS_PROXY -> RethinkDnsType.DnsProxy
    AppConfig.DnsType.SYSTEM_DNS -> RethinkDnsType.System
}

private fun BlockFreeDnsMode.toRethinkBlockFreeDnsMode() = when (this) {
    BlockFreeDnsMode.AUTO -> RethinkBlockFreeDnsMode.Auto
    BlockFreeDnsMode.GLOBAL -> RethinkBlockFreeDnsMode.Global
    BlockFreeDnsMode.FALLBACK -> RethinkBlockFreeDnsMode.Fallback
}

private fun RethinkBlockFreeDnsMode.toAndroidBlockFreeDnsMode() = when (this) {
    RethinkBlockFreeDnsMode.Auto -> BlockFreeDnsMode.AUTO
    RethinkBlockFreeDnsMode.Global -> BlockFreeDnsMode.GLOBAL
    RethinkBlockFreeDnsMode.Fallback -> BlockFreeDnsMode.FALLBACK
}

@Composable
private fun androidDnsSettingsStrings() = RethinkDnsSettingsStrings(
    title = stringResource(R.string.lbl_dns),
    refresh = stringResource(R.string.rules_load_failure_reload),
    modesSection = stringResource(R.string.dc_other_dns_heading),
    systemDns = stringResource(R.string.network_dns),
    systemDnsDescription = stringResource(R.string.dns_mode_system_desc),
    customDns = stringResource(R.string.dc_custom_dns_radio),
    customDnsDescription = stringResource(R.string.dns_mode_other_desc),
    rethinkDns = stringResource(R.string.dc_rethink_dns_radio),
    rethinkDnsDescription = stringResource(R.string.dns_mode_rethink_desc),
    smartDns = stringResource(R.string.smart_dns),
    smartDnsDescription = stringResource(R.string.dns_mode_smart_desc),
    connectedDescription = stringResource(R.string.rethink_sky_desc),
    blockSection = stringResource(R.string.dc_block_heading),
    localBlocklists = stringResource(R.string.dc_local_block_heading),
    localBlocklistsDescription = { stringResource(R.string.settings_local_blocklist_in_use, it) },
    localBlocklistsDisabledDescription = stringResource(R.string.dc_local_block_desc_1),
    enabled = stringResource(R.string.dc_local_block_enabled),
    disabled = stringResource(R.string.lbl_disabled),
    customDownloader = stringResource(R.string.settings_custom_downloader_heading),
    customDownloaderDescription = stringResource(R.string.settings_custom_downloader_desc),
    periodicUpdates = stringResource(R.string.dc_check_update_heading),
    periodicUpdatesDescription = stringResource(R.string.dc_check_update_desc_compact),
    filteringSection = stringResource(R.string.dc_filtering_heading),
    dnsAlg = stringResource(R.string.cd_dns_alg_heading),
    dnsAlgDescription = stringResource(R.string.cd_dns_alg_desc),
    splitDns = stringResource(R.string.cd_split_dns_heading),
    splitDnsDescription = stringResource(R.string.cd_split_dns_desc),
    rulesAsFirewall = stringResource(R.string.cd_treat_dns_rules_firewall_heading),
    rulesAsFirewallDescription = stringResource(R.string.cd_treat_dns_rules_firewall_desc),
    recordTypes = stringResource(R.string.cd_allowed_dns_record_types_heading),
    recordTypesDescription = stringResource(R.string.cd_allowed_dns_record_types_desc),
    auto = stringResource(R.string.dns_record_types_auto_mode_status),
    blockFreeSection = stringResource(R.string.block_free_dns_mode_title),
    blockFreeLabel = { mode -> stringResource(mode.blockFreeLabelRes()) },
    blockFreeDescription = { mode -> stringResource(mode.blockFreeDescriptionRes()) },
    trustedEndpoint = "Choose trusted DNS endpoint",
    trustedEndpointDescription = "Select the resolver used for trusted-DNS bypass",
    advancedSection = stringResource(R.string.lbl_advanced),
    favicons = stringResource(R.string.dc_dns_website_heading),
    faviconsDescription = stringResource(R.string.dc_dns_website_desc),
    dnsCache = stringResource(R.string.dc_setting_dns_cache_heading),
    dnsCacheDescription = stringResource(R.string.dc_setting_dns_cache_desc),
    proxyDns = stringResource(R.string.dc_proxy_dns_heading),
    proxyDnsDescription = stringResource(R.string.dc_proxy_dns_desc),
    undelegatedDomains = stringResource(R.string.dc_use_sys_dns_undelegated_heading),
    undelegatedDomainsDescription = stringResource(R.string.dc_use_sys_dns_undelegated_desc),
    fallbackDns = stringResource(R.string.use_fallback_dns_to_bypass),
    fallbackDnsDescription = stringResource(R.string.use_fallback_dns_to_bypass_desc),
    preventLeaks = stringResource(R.string.dc_dns_leaks_heading),
    preventLeaksDescription = stringResource(R.string.dc_dns_leaks_desc),
)

private fun RethinkBlockFreeDnsMode.blockFreeLabelRes() = when (this) {
    RethinkBlockFreeDnsMode.Auto -> R.string.bfdm_option_auto_label
    RethinkBlockFreeDnsMode.Global -> R.string.bfdm_option_global_label
    RethinkBlockFreeDnsMode.Fallback -> R.string.bfdm_option_fallback_label
}

private fun RethinkBlockFreeDnsMode.blockFreeDescriptionRes() = when (this) {
    RethinkBlockFreeDnsMode.Auto -> R.string.bfdm_option_auto_desc
    RethinkBlockFreeDnsMode.Global -> R.string.bfdm_option_global_desc
    RethinkBlockFreeDnsMode.Fallback -> R.string.bfdm_option_fallback_desc
}
