/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.bernaferrari.bravedns.ui.components.*
import com.bernaferrari.bravedns.ui.compose.apps.*
import com.bernaferrari.bravedns.ui.compose.common.*
import com.bernaferrari.bravedns.ui.compose.configure.*
import com.bernaferrari.bravedns.ui.compose.database.*
import com.bernaferrari.bravedns.ui.compose.dns.*
import com.bernaferrari.bravedns.ui.compose.events.*
import com.bernaferrari.bravedns.ui.compose.firewall.*
import com.bernaferrari.bravedns.ui.compose.home.*
import com.bernaferrari.bravedns.ui.compose.logs.*
import com.bernaferrari.bravedns.ui.compose.rpn.*
import com.bernaferrari.bravedns.ui.compose.settings.*
import com.bernaferrari.bravedns.ui.compose.statistics.*
import com.bernaferrari.bravedns.ui.compose.theme.*
import com.bernaferrari.bravedns.ui.compose.wireguard.*

@Composable
internal fun DemoMiscSettingsScreen(
    modifier: Modifier,
    appearanceMode: RethinkAppearanceMode,
    appearancePresetId: Int,
    onAppearanceModeChange: (RethinkAppearanceMode) -> Unit,
    onAppearancePresetChange: (Int) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    var toggles by remember { mutableStateOf(demoMiscToggles) }
    var showBackupRestore by remember { mutableStateOf(false) }
    if (showBackupRestore) {
        Scaffold(
            modifier = modifier,
            topBar = { RethinkLargeTopBar("Backup and restore", "Export or restore demo settings", onBackClick = { showBackupRestore = false }) },
        ) { padding ->
            RethinkBackupRestoreSheet(
                versionText = "v0.0.0-demo · Web preview",
                strings = RethinkBackupRestoreStrings(
                    title = "Backup and restore",
                    description = "Export the current configuration or restore it from a compatible backup file.",
                    backupTitle = "Create backup",
                    backupDescription = "Save a portable copy of your settings.",
                    restoreTitle = "Restore backup",
                    restoreDescription = "Replace current settings using a backup file.",
                    backupConfirmation = RethinkBackupRestoreDialogCopy("Create backup?", "Choose where to save your backup file.", "Continue", "Cancel"),
                    restoreConfirmation = RethinkBackupRestoreDialogCopy("Restore backup?", "Restoring replaces current demo settings.", "Continue", "Cancel"),
                    backupFailure = RethinkBackupRestoreDialogCopy("Backup failed", "The backup could not be created.", "Try again", "Dismiss"),
                    restoreFailure = RethinkBackupRestoreDialogCopy("Restore failed", "The backup file could not be restored.", "Try again", "Dismiss"),
                ),
                failure = null,
                onBackup = {},
                onRestore = {},
                onFailureDismiss = {},
                modifier = Modifier.padding(padding).padding(SharedDimensions.screenPaddingHorizontal),
            )
        }
        return
    }
    RethinkMiscSettingsScreen(
        strings = RethinkMiscSettingsStrings(
            title = "General settings",
            backupSection = "Backup and restore",
            backupTitle = "Backup and restore",
            backupDescription = "Export or restore the demo's settings.",
            generalSection = "General",
            aboutSection = "About",
            websiteTitle = "Website",
            websiteDescription = "rethinkdns.com",
            aboutTitle = "About RethinkDNS",
            aboutDescription = "Version, support, and legal information.",
        ),
        toggles = toggles,
        onToggleChange = { id, checked -> toggles = toggles.map { if (it.id == id) it.copy(checked = checked) else it } },
        onBackupRestore = { showBackupRestore = true },
        onOpenWebsite = {},
        onOpenAbout = onOpenAbout,
        appearanceContent = {
            RethinkAppearanceSettingsCard(
                selectedMode = appearanceMode,
                selectedPresetId = appearancePresetId,
                presets = demoAppearancePresets,
                strings = demoAppearanceStrings,
                dynamicColor = Color(0xFF7C8BFF),
                dynamicSupported = false,
                onModeSelected = onAppearanceModeChange,
                onPresetSelected = { onAppearancePresetChange(it.id) },
            )
        },
        onBackClick = onBack,
        modifier = modifier,
    )
}
@Composable
internal fun DemoDatabaseScreen(modifier: Modifier, onBack: () -> Unit) {
    var selectedTable by remember { mutableStateOf(demoDatabasePreviews.first().table) }
    val preview = demoDatabasePreviews.firstOrNull { it.table == selectedTable }
    RethinkDatabaseScreen(
        tables = demoDatabasePreviews.map { it.table },
        selectedTable = selectedTable,
        preview = preview,
        isLoadingTables = false,
        isLoadingPreview = false,
        isCopying = false,
        errorText = null,
        strings = RethinkDatabaseStrings(
            title = "Database inspector",
            searchHint = "Search tables",
            clearSearch = "Clear search",
            copyFull = "Copy full dump",
            copying = "Copying…",
            refresh = "Refresh",
            tables = "Tables",
            noTables = "No tables found",
            rows = { "$it rows" },
            columns = { "$it columns" },
            previewTruncated = "Preview is truncated in the demo.",
        ),
        onTableSelected = { selectedTable = it },
        onRefresh = {},
        onCopy = {},
        onBackClick = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DemoRpnSettingsScreen(modifier: Modifier, onBack: () -> Unit) {
    var dnsModes by remember { mutableStateOf(setOf(RethinkRpnDnsMode.Default, RethinkRpnDnsMode.Privacy)) }
    var manual by remember { mutableStateOf(false) }
    var changeIdentity by remember { mutableStateOf(true) }
    var excluded by remember { mutableStateOf(setOf("CN")) }
    RethinkRpnServerSettingsScreen(
        selectedDnsModes = dnsModes,
        manualConfiguration = manual,
        alwaysChangeIdentity = changeIdentity,
        excludedCountries = excluded,
        countries = demoRpnCountries,
        working = false,
        message = null,
        strings = demoRpnSettingsStrings,
        onDnsModesChange = { dnsModes = it },
        onManualConfigurationChange = { manual = it },
        onAlwaysChangeIdentityChange = { changeIdentity = it },
        onExcludedCountriesChange = { excluded = it },
        onReset = { excluded = emptySet(); dnsModes = setOf(RethinkRpnDnsMode.Default) },
        onBackClick = onBack,
        modifier = modifier,
    )
}

/** Interactive web preview of the common RPN country chooser and its server details. */
@Composable
internal fun DemoRpnCountriesScreen(
    modifier: Modifier,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onServerDetails: (String) -> Unit,
) {
    var countries by remember { mutableStateOf(demoRpnServerCountries) }
    RethinkRpnCountriesScreen(
        countries = countries,
        busyKey = null,
        errorMessage = null,
        strings = RethinkRpnCountriesStrings(
            title = "Rethink locations",
            settingsDescription = "RPN server settings",
            searchHint = "Search locations",
            clearSearchDescription = "Clear search",
            favourites = "Favourites",
            refresh = "Refresh",
            refreshing = "Refreshing…",
            reset = "Reset",
            resetting = "Resetting…",
            automaticLocation = "Automatic location",
            frequentlyUsed = "Frequently used",
            addFavourite = "Add favourite",
            removeFavourite = "Remove favourite",
        ),
        onBackClick = onBack,
        onServerDetails = onServerDetails,
        onSettings = onSettings,
        onRefresh = {},
        onReset = { countries = demoRpnServerCountries },
        onEnabledChange = { country, enabled ->
            countries = countries.map { if (it.key == country.key) it.copy(isEnabled = enabled) else it }
        },
        onFavouriteClick = { country ->
            countries = countries.map { if (it.key == country.key) it.copy(isFavourite = !it.isFavourite) else it }
        },
        modifier = modifier,
    )
}

@Composable
internal fun DemoRpnServerDetailsScreen(modifier: Modifier, key: String, onBack: () -> Unit) {
    val country = demoRpnServerCountries.firstOrNull { it.key == key } ?: demoRpnServerCountries.first()
    var options by remember(key) {
        mutableStateOf(
            RethinkRpnWinServerOptions(
                hopEnabled = false,
                catchAll = true,
                lockdown = false,
                mobileOnly = false,
                ssidBased = false,
            ),
        )
    }
    RethinkRpnWinProxyDetailsScreen(
        state = RethinkRpnWinProxyDetailsState(
            countryCode = country.countryCode,
            appsCount = "12",
            domainsCount = "468",
            ipsCount = "103",
            proxyName = "${country.name} RPN",
            proxyWho = "RethinkDNS",
            proxyLatency = "24 ms",
            proxyLastConnected = "Just now",
            proxyStatus = "Connected",
            isProxyActive = country.isEnabled,
            options = options,
        ),
        strings = RethinkRpnWinProxyDetailsStrings(
            title = country.name,
            fallback = "Not available",
            proxyName = "RPN server",
            apps = "Apps",
            domains = "Domains",
            ips = "IPs",
            who = "Provider",
            error = "Error",
            latency = "Latency",
            lastConnected = "Last connected",
            status = "Status",
            serverOptions = "Server options",
            hop = "Hop routing",
            catchAll = "Route all traffic",
            lockdown = "Lockdown",
            mobileOnly = "Mobile data only",
            wifiOnly = "Specific Wi‑Fi networks",
            editWifi = "Edit Wi‑Fi networks",
            selectApps = "Select apps",
        ),
        onBackClick = onBack,
        onHopChanged = { options = options.copy(hopEnabled = it) },
        onCatchAllChanged = { options = options.copy(catchAll = it) },
        onLockdownChanged = { options = options.copy(lockdown = it) },
        onMobileOnlyChanged = { options = options.copy(mobileOnly = it) },
        onSsidChanged = { options = options.copy(ssidBased = it) },
        onEditSsids = {},
        onSelectApps = {},
        modifier = modifier,
    )
}

/** Web preview for the shared Rethink Plus account and support flows. */
@Composable
internal fun DemoRpnAccountScreen(modifier: Modifier, onBack: () -> Unit) {
    var operation by remember { mutableStateOf(RethinkRpnAccountOperation()) }
    var showSupport by remember { mutableStateOf(false) }
    RethinkRpnAccountScreen(
        modifier = modifier,
        state = RethinkRpnAccountState(
            entitlementLoading = false,
            entitlement = listOf(
                RethinkRpnAccountEntry("Status", "Active"),
                RethinkRpnAccountEntry("Client ID", "demo-client-92"),
                RethinkRpnAccountEntry("Device ID", "8f7a"),
                RethinkRpnAccountEntry("Provider", "Play Store"),
            ),
            manage = operation,
            history = listOf(
                RethinkRpnAccountEntry("Trial → Active", "Activation completed · Today"),
                RethinkRpnAccountEntry("Inactive → Trial", "Account created · Yesterday"),
            ),
            orders = listOf(RethinkRpnAccountEntry("rethink.plus.monthly", "Purchased · Renews next month")),
        ),
        strings = RethinkRpnAccountStrings(
            title = "Rethink Plus", manageTab = "Manage", historyTab = "History", ordersTab = "Orders", entitlementTab = "Entitlement",
            manageHeading = "Subscription", manageDescription = "Manage this device's subscription.", actionsHeading = "Actions", actionsDescription = "Account changes take effect across your signed-in devices.",
            cancel = "Cancel subscription", cancelDescription = "Keep access until the current billing period ends.", revoke = "Revoke this device", revokeDescription = "Remove this device from the subscription.",
            cancelConfirmation = "Cancel this subscription at the end of the current period?", revokeConfirmation = "Revoke this device's subscription access?", proceed = "Proceed", dismiss = "Cancel",
            helpHeading = "Help", helpDescription = "Send a diagnostic report to Rethink support.", support = "Contact support", supportDescription = "Include selected account diagnostics.",
            noHistory = "No subscription history.", noOrders = "No orders yet.", retry = "Retry", loading = "Loading…",
        ),
        onBackClick = onBack,
        onCancelSubscription = { operation = RethinkRpnAccountOperation(message = "Subscription will not renew.") },
        onRevokeSubscription = { operation = RethinkRpnAccountOperation(message = "This device was revoked.") },
        onSupport = { showSupport = true },
        onRetryOrders = {},
    )
    if (showSupport) {
        RethinkRpnSupportSheet(
            strings = RethinkRpnSupportStrings(
                title = "Contact Rethink support", description = "Describe the issue and choose the diagnostics to attach.",
                categories = listOf("Payment", "Activation", "Connectivity", "Refund", "Other"), reportHint = "What happened?",
                includeSubscription = "Include subscription status", includeHistory = "Include state history", includeDiagnostics = "Include diagnostics",
                createEmail = "Create email", preparing = "Preparing…", cancel = "Cancel",
            ),
            sending = false,
            onSubmit = { _, _, _, _, _ -> showSupport = false },
            onDismiss = { showSupport = false },
        )
    }
}

@Composable
internal fun DemoBlockFreeDnsScreen(modifier: Modifier, onBack: () -> Unit) {
    var activeFilterId by remember { mutableStateOf<String?>(null) }
    var selectedKey by remember { mutableStateOf("DOH::https://cloudflare-dns.com/dns-query") }
    val visibleItems = demoBlockFreeDnsItems.filter { item -> activeFilterId == null || item.type == activeFilterId }
    RethinkBlockFreeDnsScreen(
        items = visibleItems,
        filters = demoBlockFreeDnsFilters,
        activeFilterId = activeFilterId,
        selectedKey = selectedKey,
        strings = RethinkBlockFreeDnsStrings(
            title = "Trusted DNS endpoint",
            heading = "Resolver for trusted traffic",
            description = "This endpoint is used when traffic must bypass DNS blocking.",
            selectedDescription = "Selected trusted DNS endpoint",
        ),
        onFilterSelected = { activeFilterId = it },
        onItemSelected = { selectedKey = it.key },
        onBackClick = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DemoDnsListScreen(modifier: Modifier, onBack: () -> Unit) {
    var selectedProtocolId by remember { mutableStateOf(1) }
    RethinkDnsListScreen(
        protocols = demoDnsProtocols,
        selectedProtocolId = selectedProtocolId,
        selectedProtocolWorking = true,
        strings = RethinkDnsListStrings(
            title = "DNS servers",
            subtitle = "Choose how RethinkDNS resolves your requests.",
            configure = "Configure",
            fast = "Fast",
            private = "Private",
            secure = "Secure",
            anonymous = "Anonymous",
        ),
        onProtocolClick = { selectedProtocolId = it.id },
        onBackClick = onBack,
        modifier = modifier,
    )
}

/** Web preview for the common tunnel-settings renderer; system actions remain intentionally inert. */
@Composable
internal fun DemoTunnelSettingsScreen(modifier: Modifier, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    var showCustomLanEditor by remember { mutableStateOf(false) }
    var showReachabilityEditor by remember { mutableStateOf(false) }
    val lanDefaults = remember {
        RethinkCustomLanIpConfiguration(
            manual = false,
            gatewayV4 = RethinkLanIpAddress("10.111.222.1", "24"),
            gatewayV6 = RethinkLanIpAddress("fd66:f83a:c650::1", "120"),
            routerV4 = RethinkLanIpAddress("10.111.222.2", "32"),
            routerV6 = RethinkLanIpAddress("fd66:f83a:c650::2", "128"),
            dnsV4 = RethinkLanIpAddress("10.111.222.3", "32"),
            dnsV6 = RethinkLanIpAddress("fd66:f83a:c650::3", "128"),
        )
    }
    var lanConfiguration by remember { mutableStateOf(lanDefaults) }
    val reachabilityDefaults = remember {
        RethinkNetworkReachabilityConfiguration(
            automatic = true,
            ipv4Ips = listOf("1.1.1.1", "8.8.8.8"),
            ipv4Urls = listOf("https://one.one.one.one", "https://dns.google"),
            ipv6Ips = listOf("2606:4700:4700::1111", "2001:4860:4860::8888"),
            ipv6Urls = listOf("https://one.one.one.one", "https://dns.google"),
        )
    }
    var reachabilityConfiguration by remember { mutableStateOf(reachabilityDefaults) }
    var values by remember {
        mutableStateOf(
            mapOf(
                "network_allow_bypass" to false,
                "network_fail_open" to true,
                "network_allow_lan" to true,
                "network_all_networks" to false,
                "network_exclude_apps_proxy" to true,
                "network_protocol_translation" to false,
                "network_mobile_metered" to false,
                "network_wg_listen_port" to true,
                "network_wg_lockdown" to false,
                "network_endpoint_independence" to true,
                "network_allow_incoming_wg" to false,
                "network_tcp_keep_alive" to true,
                "network_jumbo_packets" to false,
                "network_vpn_metered" to false,
            )
        )
    }
    var timeoutMinutes by remember { mutableStateOf(30) }
    val coreRows = listOf(
        RethinkTunnelSettingRow("network_allow_bypass", "Allow bypass", "Let selected apps bypass VPN protection.", RethinkTunnelSettingKind.Toggle, values.getValue("network_allow_bypass")),
        RethinkTunnelSettingRow("network_fail_open", "Fail open", "Keep connectivity available while a network changes.", RethinkTunnelSettingKind.Toggle, values.getValue("network_fail_open"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_allow_lan", "Allow LAN traffic", "Permit traffic to devices on the local network.", RethinkTunnelSettingKind.Toggle, values.getValue("network_allow_lan"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_all_networks", "Use all networks", "Use available network paths for the VPN.", RethinkTunnelSettingKind.Toggle, values.getValue("network_all_networks"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_exclude_apps_proxy", "Exclude apps in proxy", "Keep selected apps outside proxy routing.", RethinkTunnelSettingKind.Toggle, values.getValue("network_exclude_apps_proxy"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_protocol_translation", "Protocol translation", "Translate IPv4 and IPv6 when DNS is active.", RethinkTunnelSettingKind.Toggle, values.getValue("network_protocol_translation"), icon = RethinkTunnelSettingIcon.Tune),
    )
    val advancedRows = listOf(
        RethinkTunnelSettingRow("network_default_dns", "Default DNS", "Pick the resolver used before custom DNS is selected.", RethinkTunnelSettingKind.Action),
        RethinkTunnelSettingRow("network_vpn_policy", "VPN policy", "Automatic", RethinkTunnelSettingKind.Action),
        RethinkTunnelSettingRow("network_ip_protocol", "IP protocol", "IPv4 + IPv6", RethinkTunnelSettingKind.Action),
        RethinkTunnelSettingRow("network_connectivity_checks", "Connectivity checks", "Verify network availability before routing.", RethinkTunnelSettingKind.Action),
        RethinkTunnelSettingRow("network_ping_ips", "Ping IPs", kind = RethinkTunnelSettingKind.Action, icon = RethinkTunnelSettingIcon.NetworkCheck),
        RethinkTunnelSettingRow("network_mobile_metered", "Treat mobile as metered", "Apply metered rules to cellular data.", RethinkTunnelSettingKind.Toggle, values.getValue("network_mobile_metered"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_wg_listen_port", "Fixed WireGuard listen port", "Keep the port stable between connections.", RethinkTunnelSettingKind.Toggle, values.getValue("network_wg_listen_port"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_wg_lockdown", "WireGuard lockdown", "Block traffic outside active WireGuard tunnels.", RethinkTunnelSettingKind.Toggle, values.getValue("network_wg_lockdown"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_endpoint_independence", "Endpoint independence", "Keep mappings stable across remote endpoints.", RethinkTunnelSettingKind.Toggle, values.getValue("network_endpoint_independence"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_allow_incoming_wg", "Allow incoming WireGuard packets", "Accept packets addressed directly to this device.", RethinkTunnelSettingKind.Toggle, values.getValue("network_allow_incoming_wg"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_tcp_keep_alive", "TCP keep alive", "Keep long-running TCP connections active.", RethinkTunnelSettingKind.Toggle, values.getValue("network_tcp_keep_alive"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_jumbo_packets", "Jumbo packets", "Use a larger MTU when available.", RethinkTunnelSettingKind.Toggle, values.getValue("network_jumbo_packets"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_vpn_metered", "VPN is metered", "Report the VPN network as metered to apps.", RethinkTunnelSettingKind.Toggle, values.getValue("network_vpn_metered"), icon = RethinkTunnelSettingIcon.Tune),
        RethinkTunnelSettingRow("network_custom_lan_ip", "Custom LAN IP", "Configure local subnet handling.", RethinkTunnelSettingKind.Action),
    )
    RethinkTunnelSettingsScreen(
        listState = listState,
        strings = RethinkTunnelSettingsStrings("Network", "VPN policy: Automatic", "VPN lockdown mode is managed by the system.", "Advanced", "Dial timeout"),
        showLockdown = false,
        coreRows = coreRows,
        advancedRows = advancedRows,
        dialTimeoutMinutes = timeoutMinutes,
        dialTimeoutDescription = if (timeoutMinutes == 0) "Disabled" else "$timeoutMinutes min",
        onBackClick = onBack,
        onLockdownClick = {},
        onActionClick = { id ->
            when (id) {
                "network_custom_lan_ip" -> showCustomLanEditor = true
                "network_ping_ips" -> showReachabilityEditor = true
            }
        },
        onToggleChange = { id, checked -> values = values + (id to checked) },
        onDialTimeoutChange = { timeoutMinutes = it },
        modifier = modifier,
    )
    if (showCustomLanEditor) {
        RethinkModalBottomSheet(
            onDismissRequest = { showCustomLanEditor = false },
            modifier = Modifier.verticalScroll(rememberScrollState()),
            includeBottomSpacer = false,
            expandOnShow = true,
        ) {
            RethinkCustomLanIpEditor(
                initialConfiguration = lanConfiguration,
                defaultConfiguration = lanDefaults,
                strings = RethinkCustomLanIpEditorStrings(
                    automatic = "Automatic",
                    manual = "Manual",
                    automaticDescription = "Use RethinkDNS's private local addresses automatically.",
                    manualDescription = "Set the private gateway, router, and DNS addresses used on your LAN.",
                    gateway = "Gateway",
                    router = "Router",
                    dns = "DNS",
                    ipv4 = "IPv4 address",
                    ipv6 = "IPv6 address",
                    prefix = "Prefix",
                    save = "Save",
                    reset = "Reset",
                ),
                onSave = { lanConfiguration = it; showCustomLanEditor = false; null },
                onReset = {},
            )
        }
    }
    if (showReachabilityEditor) {
        RethinkModalBottomSheet(
            onDismissRequest = { showReachabilityEditor = false },
            modifier = Modifier.verticalScroll(rememberScrollState()),
            includeBottomSpacer = false,
            expandOnShow = true,
        ) {
            RethinkNetworkReachabilityEditor(
                initialConfiguration = reachabilityConfiguration,
                defaultConfiguration = reachabilityDefaults,
                ipv4Supported = true,
                ipv6Supported = true,
                strings = RethinkNetworkReachabilityEditorStrings(
                    automatic = "Automatic",
                    manual = "Manual",
                    description = "Check how this network reaches the internet before starting traffic routing.",
                    ipv4 = "IPv4",
                    ipv6 = "IPv6",
                    restoreDefaults = "Restore defaults",
                    save = "Save",
                    test = "Test",
                    automaticIpv4Probe = "Automatic IPv4 probe",
                    automaticIpv6Probe = "Automatic IPv6 probe",
                ),
                onTest = {
                    RethinkReachabilityProbeField.entries.associateWith { field ->
                        if (field.name.contains("Url")) RethinkReachabilityProbeStatus.Wifi else RethinkReachabilityProbeStatus.Success
                    }
                },
                onSave = { reachabilityConfiguration = it; showReachabilityEditor = false; null },
            )
        }
    }
}

/** Web preview for the Android proxy-settings shell; service and app-launch actions stay local to Android. */
@Composable
internal fun DemoProxySettingsScreen(modifier: Modifier, onBack: () -> Unit) {
    val listState = rememberLazyListState()
    var socksEnabled by remember { mutableStateOf(false) }
    var httpEnabled by remember { mutableStateOf(false) }
    var orbotEnabled by remember { mutableStateOf(false) }
    var refreshCount by remember { mutableStateOf(0) }
    var configurationKind by remember { mutableStateOf<RethinkProxyConfigurationKind?>(null) }
    var showOrbotModePicker by remember { mutableStateOf(false) }
    var orbotMode by remember { mutableStateOf("SOCKS5") }
    var proxyConfiguration by remember {
        mutableStateOf(
            RethinkProxyConfigurationState(
                host = "127.0.0.1",
                port = "9050",
                selectedAppId = "browser",
                apps = listOf(
                    RethinkProxyAppOption("browser", "Browser"),
                    RethinkProxyAppOption("mail", "Mail"),
                    RethinkProxyAppOption("", "Default app"),
                ),
            ),
        )
    }

    RethinkProxySettingsScreen(
        modifier = modifier,
        listState = listState,
        state = RethinkProxySettingsState(
            canEnableProxy = true,
            isRefreshing = false,
            wireguardAvailable = true,
            wireguardDescription = if (refreshCount == 0) "No active WireGuard tunnels" else "WireGuard status refreshed",
            socks5Enabled = socksEnabled,
            socks5Description = if (socksEnabled) "127.0.0.1:9050" else "No SOCKS5 proxy configured",
            httpEnabled = httpEnabled,
            httpDescription = if (httpEnabled) "http://127.0.0.1:8118" else "No HTTP proxy configured",
            orbotEnabled = orbotEnabled,
            orbotConnecting = false,
            orbotDescription = if (orbotEnabled) "Tor routes selected apps through SOCKS5." else "Connect selected apps through Orbot.",
            orbotAppCount = 4,
        ),
        strings = RethinkProxySettingsStrings(
            title = "Proxy",
            refresh = "Refresh",
            warning = "Proxy cannot be enabled while VPN lockdown is active.",
            wireguard = "WireGuard",
            socks5 = "SOCKS5",
            http = "HTTP proxy",
            orbot = "Orbot",
            active = "Active",
            inactive = "Inactive",
            waiting = "Connecting",
            apps = "Apps",
            openApp = "Open app",
            info = "Info",
        ),
        onBackClick = onBack,
        onRefresh = { refreshCount++ },
        onWireguardClick = {},
        onSocksRowClick = { configurationKind = RethinkProxyConfigurationKind.Socks5 },
        onSocksChange = { socksEnabled = it },
        onHttpRowClick = { configurationKind = RethinkProxyConfigurationKind.Http },
        onHttpChange = { httpEnabled = it },
        onOrbotClick = { showOrbotModePicker = true },
        onOrbotChange = { orbotEnabled = it },
        onOrbotAppsClick = {},
        onOpenOrbotApp = {},
        onOrbotInfo = {},
    )
    configurationKind?.let { kind ->
        RethinkProxyConfigurationDialog(
            kind = kind,
            state = proxyConfiguration,
            strings = RethinkProxyConfigurationStrings(
                title = if (kind == RethinkProxyConfigurationKind.Socks5) "Configure SOCKS5" else "Configure HTTP proxy",
                description = if (kind == RethinkProxyConfigurationKind.Http) "Route selected apps through an HTTP proxy." else null,
                host = "Host",
                port = "Port",
                username = "Username (optional)",
                password = "Password (optional)",
                appDescription = "Choose which app provides this proxy connection.",
                udpBlocked = "Block UDP traffic",
                includeProxyApps = "Include proxy apps in VPN routing",
                lockdownDescription = "VPN lockdown manages this setting.",
                save = "Save",
                cancel = "Cancel",
            ),
            onStateChange = { proxyConfiguration = it },
            onCancel = { configurationKind = null },
            onConfirm = {
                if (kind == RethinkProxyConfigurationKind.Socks5) socksEnabled = true else httpEnabled = true
                configurationKind = null
            },
        )
    }
    if (showOrbotModePicker) {
        RethinkProxyModeDialog(
            title = "Orbot mode",
            options = listOf(
                RethinkProxyModeOption("SOCKS5", "SOCKS5"),
                RethinkProxyModeOption("HTTP", "HTTP"),
                RethinkProxyModeOption("HTTP_SOCKS5", "HTTP + SOCKS5"),
                RethinkProxyModeOption("NONE", "Disabled"),
            ),
            selectedId = orbotMode,
            save = "Save",
            cancel = "Cancel",
            onSelected = { orbotMode = it },
            onDismiss = { showOrbotModePicker = false },
            onConfirm = { orbotEnabled = orbotMode != "NONE"; showOrbotModePicker = false },
        )
    }
}

@Composable
internal fun DemoDnsSettingsScreen(modifier: Modifier, onBack: () -> Unit, onCustomDns: () -> Unit) {
    var state by remember { mutableStateOf(demoDnsSettingsState) }
    fun update(transform: (RethinkDnsSettingsState) -> RethinkDnsSettingsState) { state = transform(state) }
    RethinkDnsSettingsScreen(
        state = state,
        strings = demoDnsSettingsStrings,
        onRefresh = {},
        onSystemDns = { update { it.copy(isSystemDnsEnabled = true, isSmartDnsEnabled = false, isRethinkDnsConnected = false) } },
        onSystemDnsInfo = {},
        onCustomDns = { update { it.copy(isSystemDnsEnabled = false, isSmartDnsEnabled = false, isRethinkDnsConnected = false) }; onCustomDns() },
        onRethinkDns = { update { it.copy(isSystemDnsEnabled = false, isSmartDnsEnabled = false, isRethinkDnsConnected = true) } },
        onSmartDns = { update { it.copy(isSystemDnsEnabled = false, isSmartDnsEnabled = true, isRethinkDnsConnected = false) } },
        onSmartDnsInfo = {},
        onLocalBlocklists = {},
        onCustomDownloaderChange = { value -> update { it.copy(useCustomDownloadManager = value) } },
        onPeriodicUpdateChange = { value -> update { it.copy(periodicallyCheckBlocklistUpdate = value) } },
        onDnsAlgChange = { value -> update { it.copy(enableDnsAlg = value, showSplitDns = value, showBypassDnsBlock = value) } },
        onSplitDnsChange = { value -> update { it.copy(splitDns = value) } },
        onRulesAsFirewallChange = { value -> update { it.copy(bypassBlockInDns = value) } },
        onRecordTypes = { update { it.copy(dnsRecordTypesAutoMode = !it.dnsRecordTypesAutoMode) } },
        onFaviconsChange = { value -> update { it.copy(fetchFavIcon = value) } },
        onDnsCacheChange = { value -> update { it.copy(enableDnsCache = value) } },
        onProxyDnsChange = { value -> update { it.copy(proxyDns = value) } },
        onUndelegatedDomainsChange = { value -> update { it.copy(useSystemDnsForUndelegatedDomains = value) } },
        onFallbackDnsChange = { value -> update { it.copy(useFallbackDnsToBypass = value) } },
        onBlockFreeModeChange = { value -> update { it.copy(blockFreeDnsMode = value) } },
        onTrustedEndpoint = {},
        onPreventLeaksChange = { value -> update { it.copy(preventDnsLeaks = value) } },
        onBackClick = onBack,
        modifier = modifier,
    )
}
