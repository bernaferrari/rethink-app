/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose

import com.celzero.bravedns.ui.icons.MaterialSymbols
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
import com.celzero.bravedns.ui.components.*
import com.celzero.bravedns.ui.compose.apps.*
import com.celzero.bravedns.ui.compose.common.*
import com.celzero.bravedns.ui.compose.configure.*
import com.celzero.bravedns.ui.compose.database.*
import com.celzero.bravedns.ui.compose.dns.*
import com.celzero.bravedns.ui.compose.events.*
import com.celzero.bravedns.ui.compose.firewall.*
import com.celzero.bravedns.ui.compose.home.*
import com.celzero.bravedns.ui.compose.logs.*
import com.celzero.bravedns.ui.compose.rpn.*
import com.celzero.bravedns.ui.compose.settings.*
import com.celzero.bravedns.ui.compose.statistics.*
import com.celzero.bravedns.ui.compose.theme.*
import com.celzero.bravedns.ui.compose.wireguard.*

private data class DemoOtherDnsEndpoint(val id: String, val name: String, val url: String, val selected: Boolean)

private data class DemoWireguardConfig(
    val id: String,
    val name: String,
    val endpoint: String,
    val active: Boolean,
)

/** Web preview for the same common WireGuard landing screen used by Android. */
@Composable
internal fun DemoWireguardScreen(modifier: Modifier, onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(RethinkWgTab.One) }
    var pendingTab by remember { mutableStateOf(RethinkWgTab.One) }
    var confirmDisable by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf(RethinkWireguardEditorState(interfaceName = "New WireGuard tunnel", mtu = "1420")) }
    var selectedConfig by remember { mutableStateOf<DemoWireguardConfig?>(null) }
    var editorReturnConfigId by remember { mutableStateOf<String?>(null) }
    var showAppsPicker by remember { mutableStateOf(false) }
    var showPeerEditor by remember { mutableStateOf(false) }
    var includedAppIds by remember { mutableStateOf(setOf("browser", "mail")) }
    var configs by remember {
        mutableStateOf(
            listOf(
                DemoWireguardConfig("wg-1", "Mullvad São Paulo", "br-sao-wireguard.example", true),
                DemoWireguardConfig("wg-2", "Private relay", "relay.rethinkdns.example", false),
            )
        )
    }
    fun appendConfig(name: String, endpoint: String): DemoWireguardConfig {
        val index = configs.size + 1
        val config = DemoWireguardConfig("wg-$index", name, endpoint, true)
        configs = configs.map { it.copy(active = false) } + config
        return config
    }
    selectedConfig?.let { selected ->
        var catchAllEnabled by remember(selected.id) { mutableStateOf(false) }
        var useMobileEnabled by remember(selected.id) { mutableStateOf(false) }
        var ssidEnabled by remember(selected.id) { mutableStateOf(false) }
        var showHopPicker by remember(selected.id) { mutableStateOf(false) }
        var selectedHopId by remember(selected.id) { mutableStateOf<String?>(null) }
        var showSsidEditor by remember(selected.id) { mutableStateOf(false) }
        var ssidRules by remember(selected.id) {
            mutableStateOf(listOf(RethinkWireguardSsidRule("Home Wi-Fi", RethinkWireguardSsidType.EqualWildcard)))
        }
        if (showHopPicker) {
            val hops = listOf(
                RethinkWireguardHopItem(
                    id = "wg-hop-1",
                    name = "Privacy relay",
                    status = if (selectedHopId == "wg-hop-1") "Ready to relay traffic" else "Active",
                    isActive = true,
                    hasIpv4 = true,
                    hasIpv6 = true,
                    isAlreadyHop = selectedHopId == "wg-hop-1",
                ),
                RethinkWireguardHopItem(
                    id = "wg-hop-2",
                    name = "Fallback exit",
                    status = "Not active",
                    isActive = false,
                    hasIpv4 = true,
                    isSplitTunnel = true,
                ),
            )
            RethinkWireguardHopPicker(
                items = hops,
                selectedId = selectedHopId,
                strings = RethinkWireguardHopPickerStrings(
                    title = "Manage hops",
                    done = "Done",
                    ipv4 = "IPv4",
                    ipv6 = "IPv6",
                    splitTunnel = "Split tunnel",
                    amnezia = "Amnezia",
                    hopSource = "Hopping",
                    alreadyHop = "Relay",
                ),
                onToggle = { item, checked ->
                    RethinkWireguardHopToggleResult(succeeded = true, selectedId = if (checked) item.id else null)
                },
                onSelectedIdChange = { selectedHopId = it },
                onDone = { showHopPicker = false },
                modifier = modifier.fillMaxSize().padding(SharedDimensions.screenPaddingHorizontal),
            )
            return
        }
        if (showAppsPicker) {
            var query by remember { mutableStateOf("") }
            var filter by remember { mutableStateOf(RethinkIncludeAppsFilter.All) }
            val allApps = remember {
                listOf(
                    RethinkIncludeAppsPickerItem("browser", "Browser", "B"),
                    RethinkIncludeAppsPickerItem("camera", "Camera", "C"),
                    RethinkIncludeAppsPickerItem("mail", "Mail", "M"),
                    RethinkIncludeAppsPickerItem("messenger", "Messenger", "M"),
                    RethinkIncludeAppsPickerItem("music", "Music", "M"),
                    RethinkIncludeAppsPickerItem("notes", "Notes", "N"),
                    RethinkIncludeAppsPickerItem("weather", "Weather", "W"),
                    RethinkIncludeAppsPickerItem("youtube", "YouTube", "Y"),
                )
            }
            val visibleApps = allApps.filter { app ->
                (filter == RethinkIncludeAppsFilter.All || includedAppIds.contains(app.id)) &&
                    app.title.contains(query, ignoreCase = true)
            }
            RethinkIncludeAppsPicker(
                title = selected.name,
                items = visibleApps,
                query = query,
                selectedFilter = filter,
                allItemsSelected = allApps.all { includedAppIds.contains(it.id) },
                isRefreshing = false,
                strings = RethinkIncludeAppsPickerStrings(
                    search = "Search apps",
                    clearSearch = "Clear search",
                    all = "All",
                    selected = "Selected",
                    refresh = "Refresh",
                    loading = "Loading",
                    selectAll = "Select all",
                    unselectAll = "Unselect all",
                    done = "Done",
                    empty = "No apps match this filter",
                    more = "More actions",
                ),
                onBackClick = { showAppsPicker = false },
                onQueryChange = { query = it },
                onFilterChange = { filter = it },
                onRefresh = {},
                onToggleAll = {
                    includedAppIds = if (allApps.all { includedAppIds.contains(it.id) }) emptySet() else allApps.map { it.id }.toSet()
                },
                onDone = { showAppsPicker = false },
                modifier = modifier,
                itemContent = { app, position ->
                    RethinkIncludeAppRow(
                        state = RethinkIncludeAppState(
                            id = app.id,
                            title = app.title,
                            isIncluded = includedAppIds.contains(app.id),
                            isProxyExcluded = false,
                            hasInternetPermission = true,
                        ),
                        position = position,
                        onIncludedChange = { checked ->
                            includedAppIds = if (checked) includedAppIds + app.id else includedAppIds - app.id
                        },
                        appIcon = { Icon(MaterialSymbols.Filled.Apps, null, modifier = Modifier.size(28.dp)) },
                    )
                },
            )
            return
        }
        RethinkWireguardDetail(
            state = RethinkWireguardDetailState(
                name = selected.name,
                status = if (selected.active) "Connected" else "Not active",
                statusColor = if (selected.active) MaterialTheme.colorScheme.tertiary else null,
                isOneWireguard = selectedTab == RethinkWgTab.One,
                appsCount = 12,
                catchAllEnabled = catchAllEnabled,
                useMobileEnabled = useMobileEnabled,
                ssidEnabled = ssidEnabled,
                ssidSupported = true,
                ssidSummary = "Use only on selected Wi-Fi networks\nHome Wi-Fi",
            ),
            strings = RethinkWireguardDetailStrings(
                title = "WireGuard",
                configure = "Configure",
                addPeer = "Add",
                peer = "Peer",
                edit = "Edit",
                delete = "Delete",
                deleteDescription = "Remove this WireGuard configuration",
                apps = "Apps",
                manageApps = { "$it apps included" },
                manageHops = "Manage hops",
                advanced = "Advanced",
                catchAll = "Route all apps",
                catchAllDescription = "Use this tunnel for every app that is not explicitly excluded.",
                useMobile = "Use on mobile networks",
                useMobileDescription = "Only activate this configuration on mobile data.",
                ssid = "Use on selected Wi-Fi",
                editSsids = "Wi-Fi networks",
                peers = "Peers",
                oneWireguardNotice = "Simple mode routes the device through one active tunnel.",
            ),
            onBackClick = { selectedConfig = null },
            onAddPeer = { showPeerEditor = true },
            onEdit = {
                editorState = editorState.copy(interfaceName = selected.name, addresses = selected.endpoint)
                editorReturnConfigId = selected.id
                selectedConfig = null
                showEditor = true
            },
            onDelete = { configs = configs.filterNot { it.id == selected.id }; selectedConfig = null },
            onManageApps = { showAppsPicker = true },
            onManageHops = { showHopPicker = true },
            onCatchAllChange = { catchAllEnabled = it },
            onUseMobileChange = { useMobileEnabled = it },
            onSsidChange = { ssidEnabled = it },
            onEditSsids = { showSsidEditor = true },
            modifier = modifier,
        ) {
            RethinkWireguardPeerRow(
                item = RethinkWireguardPeerItem(
                    publicKey = "D8bC2qL5mR9sT1vW4yZ6aE3gH7jK0nP2uX5cF8iO1rQ=",
                    allowedIps = "0.0.0.0/0, ::/0",
                    endpoint = selected.endpoint,
                    persistentKeepalive = "25 seconds",
                ),
                strings = RethinkWireguardPeerRowStrings(
                    peer = "Peer",
                    publicKey = "Public key",
                    allowedIps = "Allowed IPs",
                    endpoint = "Endpoint",
                    persistentKeepalive = "Persistent keepalive",
                    editDescription = "Edit peer",
                    deleteDescription = "Delete peer",
                    deleteTitle = "Delete peer",
                    deleteMessage = "Remove this peer from ${selected.name}?",
                    deleteConfirm = "Delete",
                    cancel = "Cancel",
                ),
                onEdit = { showPeerEditor = true },
                onDelete = { showPeerEditor = false },
            )
        }
        if (showPeerEditor) {
            RethinkModalBottomSheet(
                onDismissRequest = { showPeerEditor = false },
                includeBottomSpacer = false,
            ) {
                RethinkWireguardPeerEditor(
                    initialState = RethinkWireguardPeerState(endpoint = selected.endpoint, allowedIps = "0.0.0.0/0, ::/0"),
                    strings = RethinkWireguardPeerEditorStrings(
                        title = "Add peer",
                        publicKey = "Public key",
                        presharedKey = "Preshared key",
                        persistentKeepalive = "Persistent keepalive",
                        endpoint = "Endpoint",
                        allowedIps = "Allowed IPs",
                        save = "Save",
                        dismiss = "Dismiss",
                    ),
                    onSave = { showPeerEditor = false },
                    onDismiss = { showPeerEditor = false },
                )
            }
        }
        if (showSsidEditor) {
            RethinkModalBottomSheet(
                onDismissRequest = { showSsidEditor = false },
                includeBottomSpacer = false,
            ) {
                RethinkWireguardSsidEditor(
                    initialRules = ssidRules,
                    strings = RethinkWireguardSsidEditorStrings(
                        title = "Wi-Fi networks",
                        action = "Action",
                        criteria = "Criteria",
                        ssid = "Wi-Fi identifier",
                        connect = "Connect",
                        pause = "Pause",
                        exact = "Matches exactly",
                        wildcard = "Matches partially",
                        add = "Add",
                        save = "Save",
                        cancel = "Cancel",
                        delete = "Delete",
                        invalidName = "Wi-Fi identifiers must be 32 characters or less.",
                        description = { action, criteria -> "$action this WireGuard VPN when the active Wi-Fi identifier $criteria." },
                    ),
                    onSave = { ssidRules = it; showSsidEditor = false },
                    onDismiss = { showSsidEditor = false },
                    onValidationError = {},
                )
            }
        }
        return
    }
    if (showEditor) {
        RethinkWireguardEditor(
            state = editorState,
            strings = RethinkWireguardEditorStrings(
                title = "Configure WireGuard",
                configuration = "Configuration",
                setup = "WireGuard keys",
                network = "Network",
                advanced = "Advanced",
                name = "Configuration name",
                addresses = "Addresses",
                dnsServers = "DNS servers",
                privateKey = "Private key",
                publicKey = "Public key",
                listenPort = "Listen port",
                mtu = "MTU",
                generateKeys = "Generate keys",
                copyPublicKey = "Copy public key",
                cancel = "Cancel",
                save = "Save configuration",
            ),
            actionBottomInset = 0.dp,
            onStateChange = { editorState = it },
            onBackClick = {
                showEditor = false
                selectedConfig = editorReturnConfigId?.let { id -> configs.firstOrNull { it.id == id } }
                editorReturnConfigId = null
            },
            onGenerateKeys = {
                editorState = editorState.copy(
                    privateKey = "yKpN2lR6s8aG9dQ4wT7eM3bF5hJ1kL0pV2xC6zA9uE=",
                    publicKey = "D8bC2qL5mR9sT1vW4yZ6aE3gH7jK0nP2uX5cF8iO1rQ=",
                )
            },
            onCopyPublicKey = {},
            onSaveClick = {
                val editedId = editorReturnConfigId
                if (editedId == null) {
                    selectedConfig = appendConfig(
                        editorState.interfaceName.ifBlank { "Custom WireGuard tunnel" },
                        editorState.addresses.ifBlank { "custom-wireguard.example" },
                    )
                } else {
                    val updated = configs.firstOrNull { it.id == editedId }?.copy(
                        name = editorState.interfaceName.ifBlank { "Custom WireGuard tunnel" },
                        endpoint = editorState.addresses.ifBlank { "custom-wireguard.example" },
                    )
                    if (updated != null) {
                        configs = configs.map { if (it.id == updated.id) updated else it }
                        selectedConfig = updated
                    }
                }
                showEditor = false
                editorReturnConfigId = null
            },
            modifier = modifier,
        )
        return
    }
    val activeName = configs.firstOrNull { it.active }?.name
    RethinkWireguardScreen(
        selectedTab = selectedTab,
        overview = if (activeName == null) "No WireGuard tunnel is active." else "$activeName is protecting this device.",
        isEmpty = configs.isEmpty(),
        configs = RethinkInMemoryEndpointFeed(configs),
        strings = RethinkWireguardStrings(
            title = "WireGuard",
            oneTab = "Simple",
            generalTab = "Advanced",
            create = "Create",
            import = "Import configuration",
            qrCode = "Scan QR code",
            noConfigurations = "Create or import a WireGuard configuration to get started.",
            disableTitle = "Disable active tunnel?",
            disableMessage = "Switching modes turns off the current WireGuard tunnel.",
            disableConfirm = "Disable",
            cancel = "Cancel",
        ),
        bottomInset = 0.dp,
        confirmDisable = confirmDisable,
        onBackClick = onBack,
        onTabClick = { tab ->
            if (tab != selectedTab && configs.any { it.active }) {
                pendingTab = tab
                confirmDisable = true
            } else {
                selectedTab = tab
            }
        },
        onDismissDisable = { confirmDisable = false },
        onConfirmDisable = {
            configs = configs.map { it.copy(active = false) }
            selectedTab = pendingTab
            confirmDisable = false
        },
        onCreate = {
            editorReturnConfigId = null
            editorState = RethinkWireguardEditorState(interfaceName = "New WireGuard tunnel", mtu = "1420")
            showEditor = true
        },
        onImport = { appendConfig("Imported configuration", "imported-wireguard.example") },
        onQrCode = { appendConfig("QR configuration", "qr-wireguard.example") },
        modifier = modifier,
    ) { config, tab ->
        RethinkWireguardConfigCard(
            state = RethinkWireguardConfigCardState(
                name = config.name,
                identifier = "(${config.id})",
                chips = if (config.active) listOf("IPv4", "IPv6", if (tab == RethinkWgTab.General) "Split tunnel" else "Full device") else emptyList(),
                isChecked = config.active,
                statusText = if (config.active) "Connected · ${config.endpoint}" else "Disabled",
                appsText = if (tab == RethinkWgTab.General) "12 apps included" else null,
                uptimeText = if (config.active) "Connected now" else null,
                rxTxText = if (config.active) "↓ 6.4 MB · ↑ 1.2 MB" else null,
                accentBorderColor = if (config.active) MaterialTheme.colorScheme.tertiary else null,
                accentBorderWidth = if (config.active) SharedDimensions.dividerThicknessBold else 0.dp,
            ),
            control = if (tab == RethinkWgTab.One) RethinkWireguardConfigControl.Checkbox else RethinkWireguardConfigControl.Switch,
            onOpen = { selectedConfig = config },
            onCheckedChange = { selected ->
                configs = configs.map { item -> item.copy(active = selected && item.id == config.id) }
            },
        )
    }
}
/** Web preview for the same common endpoint list/editor used by Android's custom-DNS flow. */
@Composable
internal fun DemoOtherDnsScreen(modifier: Modifier, onBack: () -> Unit) {
    var endpoints by remember {
        mutableStateOf(
            listOf(
                DemoOtherDnsEndpoint("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", true),
                DemoOtherDnsEndpoint("quad9", "Quad9", "https://dns.quad9.net/dns-query", false),
            )
        )
    }
    var showEditor by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = { RethinkLargeTopBar("Custom DNS", "DoH, DoT, DNSCrypt and ODoH providers", onBack) },
    ) { padding ->
        RethinkEndpointListWithAdd(
            feed = RethinkInMemoryEndpointFeed(endpoints),
            createLabel = "Add resolver",
            onCreate = { showEditor = true },
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        ) { endpoint ->
            DnsEndpointRow(
                title = endpoint.name,
                supporting = endpoint.url,
                selected = endpoint.selected,
                action = DnsRowAction.Info,
                onActionClick = {},
                onSelectionChange = { endpoints = endpoints.map { it.copy(selected = it.id == endpoint.id) } },
            )
        }
    }
    if (showEditor) {
        RethinkEndpointEditorDialog(onDismiss = { showEditor = false }) {
            RethinkUrlEndpointEditor(
                title = "Add DNS-over-HTTPS",
                nameLabel = "Resolver name",
                endpointLabel = "Resolver URL",
                defaultName = "Custom resolver",
                initialEndpoint = "https://",
                insecureLabel = "Allow insecure resolver",
                strings = RethinkEndpointEditorStrings("Cancel", "Add"),
                onSubmit = { name, url, _ ->
                    if (!url.startsWith("https://") || url.removePrefix("https://").isBlank()) {
                        "Enter a valid HTTPS resolver URL"
                    } else {
                        endpoints = endpoints.map { it.copy(selected = false) } + DemoOtherDnsEndpoint(url, name.ifBlank { url }, url, true)
                        showEditor = false
                        null
                    }
                },
                onDismiss = { showEditor = false },
            )
        }
    }
}

@Composable
internal fun DemoBlocklistEditorScreen(modifier: Modifier, onBack: () -> Unit) {
    var packs by remember { mutableStateOf(demoBlocklistPacks) }
    var tags by remember { mutableStateOf(demoBlocklistTags) }
    var view by remember { mutableStateOf(RethinkBlocklistEditorView.Packs) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RethinkBlocklistSelectionFilter.All) }
    var groups by remember { mutableStateOf(emptySet<String>()) }
    val shownPacks = packs.filter { groups.isEmpty() || it.group.id in groups }
    val shownTags = tags.filter { tag ->
        (query.isBlank() || tag.name.contains(query, true) || tag.subgroup.contains(query, true)) &&
            (filter == RethinkBlocklistSelectionFilter.All || tag.selected) &&
            (groups.isEmpty() || tag.group.id in groups)
    }
    Scaffold(
        modifier = modifier,
        topBar = { RethinkLargeTopBar("Blocklists", onBackClick = onBack) },
    ) { padding ->
        RethinkBlocklistEditor(
            packs = RethinkInMemoryBlocklistFeed(shownPacks), fileTags = RethinkInMemoryBlocklistFeed(shownTags), activeView = view,
            query = query, selectionFilter = filter, selectedSubgroups = groups, availableSubgroups = (packs.map { it.group.id } + tags.map { it.group.id }).distinct(),
            showDownload = false, showEditor = true, isDownloading = false,
            strings = RethinkBlocklistEditorStrings("Download a fresh blocklist before choosing filters.", "Download", "Cancel", "Packs", "Advanced", "Search blocklists", "Clear search", "All", "Selected", "Filter groups", "Filter the visible blocklists", "Apply", "Discard", { "$it blocklists" }, { "$it entries" }),
            onViewChange = { view = it }, onQueryChange = { query = it }, onSelectionFilterChange = { filter = it }, onSubgroupsChange = { groups = it },
            onDownload = {}, onCancelDownload = onBack,
            onPackToggle = { pack, selected -> packs = packs.map { if (it.id == pack.id) it.copy(selected = selected) else it } },
            onFileTagToggle = { tag, selected -> tags = tags.map { if (it.id == tag.id) it.copy(selected = selected) else it } },
            onOpenUrl = {}, onApply = onBack, onDiscard = onBack, modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun DemoPingTestScreen(modifier: Modifier, onBack: () -> Unit) {
    var customIp by remember { mutableStateOf("216.239.32.27:443") }
    var customHost by remember { mutableStateOf("brave.com:443") }
    var tested by remember { mutableStateOf(false) }
    fun status(success: Boolean) = if (tested) RethinkPingStatus.Result(success) else RethinkPingStatus.Idle
    RethinkPingTestScreen(
        ipChecks = listOf(
            RethinkPingCheck("ip1", "1.1.1.1:53", false, status(true)),
            RethinkPingCheck("ip2", "8.8.8.8:53", false, status(true)),
            RethinkPingCheck("ip3", customIp, true, status(true)),
        ),
        hostChecks = listOf(
            RethinkPingCheck("host1", "cloudflare.com:443", false, status(true)),
            RethinkPingCheck("host2", "google.com:443", false, status(true)),
            RethinkPingCheck("host3", customHost, true, status(false)),
        ),
        strength = if (tested) 4 else null,
        maxStrength = 5,
        vpnActive = true,
        strings = RethinkPingTestStrings(
            title = "Connectivity checks", subtitle = "Check whether the current connection can reach RPN endpoints.",
            noVpnTitle = "VPN is not active", noVpnDescription = "Start protection before running these checks.", dismiss = "Dismiss",
            ipSection = "IP and port", hostSection = "Host and port", test = "Test", strength = "Strength",
            strengthValue = { value, max -> "$value / $max" },
        ),
        onValueChange = { id, value -> if (id == "ip3") customIp = value else if (id == "host3") customHost = value },
        onTest = { tested = true },
        onBackClick = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DemoAntiCensorshipScreen(modifier: Modifier, onBack: () -> Unit) {
    var dialId by remember { mutableStateOf("SPLIT_AUTO") }
    var retryId by remember { mutableStateOf("RETRY_WITH_SPLIT") }
    val retryOptions = demoRetryOptions.map { option ->
        option.copy(enabled = dialId != "NEVER_SPLIT" || option.id == "RETRY_NEVER")
    }
    RethinkAntiCensorshipScreen(
        dialOptions = demoDialOptions,
        retryOptions = retryOptions,
        selectedDialId = dialId,
        selectedRetryId = retryId,
        strings = RethinkAntiCensorshipStrings(
            title = "Anti-censorship", split = "Split", retryHeading = "Retry strategy",
            retryDescription = "Choose how RethinkDNS should retry connections when censorship is detected.",
        ),
        onDialSelected = { id ->
            dialId = id
            retryId = if (id == "NEVER_SPLIT") "RETRY_NEVER" else "RETRY_WITH_SPLIT"
        },
        onRetrySelected = { retryId = it },
        onRetryDisabled = {},
        onBackClick = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DemoEventLogsScreen(modifier: Modifier, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(RethinkEventFilters(RethinkEventFilterMode.All)) }
    var events by remember { mutableStateOf(demoEvents) }
    val visibleEvents = events.filter { event ->
        (query.isBlank() || event.message.contains(query, ignoreCase = true) || event.details.orEmpty().contains(query, ignoreCase = true)) &&
            (filters.severity == null || event.severity == filters.severity) &&
            (filters.sources.isEmpty() || event.sourceLabel in filters.sources.map(RethinkEventSource::label))
    }
    RethinkEventsScreen(
        query = query,
        filters = filters,
        strings = RethinkEventsStrings(
            title = "Event logs", searchHint = "Search events and messages", clearSearch = "Clear search", refresh = "Refresh", delete = "Delete",
            all = "All", severity = "Severity", source = "Source", low = "Low", medium = "Medium", high = "High", critical = "Critical",
            deleteDialogTitle = "Clear event logs?", deleteDialogDescription = "This demo will remove all event log rows.", cancel = "Cancel",
            noEventsTitle = "No events recorded yet", noEventsDescription = "Event logs will appear here once the app starts recording system activities.", copy = "Copy",
        ),
        isLoading = false,
        isEmpty = visibleEvents.isEmpty(),
        sourceLabel = RethinkEventSource::label,
        onQueryChange = { query = it },
        onFiltersChange = { filters = it },
        onRefresh = {},
        onDelete = { events = emptyList() },
        onBackClick = onBack,
        modifier = modifier,
    ) { contentModifier, activeQuery ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(visibleEvents, key = { _, event -> event.id }) { index, event ->
                RethinkEventCard(
                    event = event,
                    query = activeQuery,
                    position = when {
                        visibleEvents.size == 1 -> RethinkEventCardPosition.Single
                        index == 0 -> RethinkEventCardPosition.First
                        index == visibleEvents.lastIndex -> RethinkEventCardPosition.Last
                        else -> RethinkEventCardPosition.Middle
                    },
                    copyDescription = "Copy",
                    onCopy = {},
                )
            }
        }
    }
}

@Composable
internal fun DemoConsoleLogsScreen(modifier: Modifier, onBack: () -> Unit) {
    RethinkConsoleLogsScreen(
        strings = demoConsoleLogStrings,
        initialLogLevel = 3,
        onFilterChange = {},
        onLogLevelSelected = {},
        onShareClick = {},
        onClearClick = {},
        onBackClick = onBack,
        modifier = modifier,
    ) {
        RethinkLogList(
            state = RethinkLogListState.Content(demoConsoleLogs),
            strings = demoLogListStrings,
        )
    }
}
