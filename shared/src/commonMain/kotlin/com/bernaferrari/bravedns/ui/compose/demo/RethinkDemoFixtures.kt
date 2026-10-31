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
import com.bernaferrari.bravedns.ui.compose.about.*
import com.bernaferrari.bravedns.ui.compose.apps.*
import com.bernaferrari.bravedns.ui.compose.common.*
import com.bernaferrari.bravedns.ui.compose.configure.*
import com.bernaferrari.bravedns.ui.compose.database.*
import com.bernaferrari.bravedns.ui.compose.dns.*
import com.bernaferrari.bravedns.ui.compose.events.*
import com.bernaferrari.bravedns.ui.compose.firewall.*
import com.bernaferrari.bravedns.ui.compose.home.*
import com.bernaferrari.bravedns.ui.compose.logs.*
import com.bernaferrari.bravedns.ui.compose.navigation.*
import com.bernaferrari.bravedns.ui.compose.rpn.*
import com.bernaferrari.bravedns.ui.compose.settings.*
import com.bernaferrari.bravedns.ui.compose.statistics.*
import com.bernaferrari.bravedns.ui.compose.theme.*
import com.bernaferrari.bravedns.ui.compose.wireguard.*
import com.bernaferrari.bravedns.ui.icons.keepout.KeepoutIcons

internal val demoNavigationItems = listOf(
    RethinkNavigationItem(
        id = RethinkRootDestination.Home.name,
        label = "Home",
        selectedIcon = MaterialSymbols.Filled.Home,
        unselectedIcon = MaterialSymbols.Outlined.Home,
    ),
    RethinkNavigationItem(
        id = RethinkRootDestination.Apps.name,
        label = "Apps",
        selectedIcon = MaterialSymbols.Filled.Apps,
        unselectedIcon = MaterialSymbols.Outlined.Apps,
    ),
    RethinkNavigationItem(
        id = RethinkRootDestination.Activity.name,
        label = "Activity",
        selectedIcon = MaterialSymbols.Filled.Monitoring,
        unselectedIcon = MaterialSymbols.Outlined.Monitoring,
    ),
    RethinkNavigationItem(
        id = RethinkRootDestination.Configure.name,
        label = "Settings",
        selectedIcon = MaterialSymbols.Filled.Settings,
        unselectedIcon = MaterialSymbols.Outlined.Settings,
    ),
)

internal val demoHomeStrings = RethinkHomeStrings(
    productName = "Keepout",
    protected = "Protected",
    notActive = "Not active",
    start = "Start",
    stop = "Stop",
    protectedSubtitle = "Traffic is being filtered on this device",
    inactiveSubtitle = "Turn on protection to secure your traffic",
    failingSubtitle = "Rethink is trying to restore your connection",
    connections = "Connections",
    dnsQueries = "DNS queries",
    networkProtection = "Network protection",
    live = "Live",
    offline = "Off",
    reconnecting = "Reconnecting",
    activityTitle = "Activity",
    activitySubtitle = "Since logs were last cleared",
)

internal val demoConfigureStrings = RethinkConfigureStrings(
    title = "Settings", searchHint = "Search settings", openSearch = "Search settings",
    closeSearch = "Close search", clearSearch = "Clear search",
    noResultsTitle = "No settings found", noResultsSubtitle = "Try another search term.",
)

internal val demoAboutStrings = RethinkAboutStrings(
    appName = "Keepout",
    about = "About",
    app = "App",
    whatsNew = "What's new",
    checkForUpdates = "Check for updates",
    joinTelegram = "Join Telegram",
    reportABug = "Report a bug",
    collectingLogs = "Collecting logs",
    web = "Web",
    website = "Website",
    github = "GitHub",
    faq = "FAQ",
    docs = "Documentation",
    privacyPolicy = "Privacy policy",
    terms = "Terms of service",
    license = "License",
    connect = "Connect",
    twitter = "Twitter",
    email = "Email",
    reddit = "Reddit",
    element = "Element",
    mastodon = "Mastodon",
    appInfo = "App info",
    vpnProfile = "VPN profile",
    notifications = "Notifications",
    diagnostics = "Diagnostics",
    statistics = "Statistics",
    databaseDump = "Database dump",
    flightRecorder = "Flight recorder",
    eventLogs = "Event logs",
    supportedBy = "Supported by Mozilla",
    partnerMozilla = "Mozilla",
    partnerFoss = "FOSS United",
    partnerFlossFunds = "FLOSS/fund",
)

internal val demoLogsStrings = RethinkLogsStrings(
    title = "Logs",
    network = "Network",
    dns = "DNS",
    moreActions = "More actions",
    refresh = "Refresh",
    clear = "Clear logs",
    connections = "Connections",
    lookups = "DNS lookups",
)

internal val demoLogListStrings = RethinkLogListStrings(
    all = "All",
    allowed = "Allowed",
    blocked = "Blocked",
    loading = "Loading logs",
    empty = "No logs",
    error = "Couldn't load logs",
    retry = "Retry",
)

internal fun demoLogAppFilterStrings(appCount: Int) = RethinkLogAppFilterStrings(
    all = "All",
    searchPlaceholder = "Search $appCount apps",
    clear = "Clear",
    clearSearchDescription = "Clear search",
    dismissDescription = "Dismiss",
    loading = "Loading apps",
)

internal val demoLogRules = listOf(
    RethinkLogRuleOption("tracking", "Tracking protection", androidx.compose.ui.text.AnnotatedString("Block known tracking endpoints.")),
    RethinkLogRuleOption("malware", "Malware protection", androidx.compose.ui.text.AnnotatedString("Block suspicious and malicious destinations.")),
    RethinkLogRuleOption("proxy", "Proxy errors", androidx.compose.ui.text.AnnotatedString("Show failed proxy connections.")),
)

internal val demoConsoleLogStrings = RethinkConsoleLogStrings(
    title = "Console logs",
    searchHint = "Search console logs",
    filterDescription = "Filter log level",
    clearDescription = "Delete all console logs",
    clearSearchDescription = "Clear search",
    deleteDialogTitle = "Delete console logs?",
    deleteDialogMessage = "All console logs stored on this device will be permanently deleted.",
    deleteLabel = "Delete",
    reportLabel = "Report a bug",
    cancelLabel = "Cancel",
    filterOptions = listOf("Verbose", "Debug", "Info", "Warn", "Error", "Fatal"),
)

internal val demoRpnSettingsStrings = RethinkRpnServerSettingsStrings(
    title = "RPN server settings",
    dnsFilteringTitle = "DNS filtering",
    dnsFilteringDescription = "Choose the RPN DNS policies this device can use.",
    defaultDnsMode = "Default",
    privacyDnsMode = "Privacy",
    parentalDnsMode = "Parental",
    securityDnsMode = "Security",
    configurationTitle = "Configuration",
    configurationDescription = "Tune how RPN selects and rotates servers.",
    manualTitle = "Manual configuration",
    manualDescription = "Keep selected RPN settings instead of applying automatic changes.",
    changeIdentityTitle = "Change identity",
    changeIdentityDescription = "Rotate the RPN identity as connections change.",
    exclusionsTitle = "Automatic exclusions",
    noExclusions = "No locations excluded",
    exclusionsCount = { "$it locations excluded" },
    maintenanceTitle = "Maintenance",
    maintenanceDescription = "Refresh RPN server configuration.",
    resetTitle = "Reset configuration",
    resetDescription = "Fetch a fresh RPN configuration.",
    resetConfirmationTitle = "Reset RPN configuration?",
    resetConfirmationDescription = "This demo will restore the default selection.",
    excludeLocationsTitle = "Exclude locations",
    save = "Save",
    cancel = "Cancel",
)

internal fun demoCountryStatistic(
    id: String,
    name: String,
    flag: String,
    connections: String,
    supporting: String,
    appConnections: List<Pair<String, String>>,
    /** Largest country count in the fixture; the ranking bar is relative to it. */
    maxConnections: Float = 1240f,
) = RethinkSummaryStatisticsRow(
    id = id,
    headline = name,
    supporting = supporting,
    metric = connections,
    countryFlag = flag,
    share = connections.filter(Char::isDigit).toFloatOrNull()?.div(maxConnections)?.coerceIn(0f, 1f),
    expandedContent = {
        RethinkCountryBreakdown(
            title = "Most active apps",
            emptyMessage = "No app activity in this country",
            accentColor = MaterialTheme.colorScheme.tertiary,
            apps = appConnections.map { (app, count) ->
                RethinkCountryBreakdownItem(
                    id = "$id-$app",
                    headline = app,
                    metric = "$count connections",
                )
            },
        )
    },
)

internal val demoRpnCountries = listOf(
    RethinkRpnCountryOption("BR", "Brazil"),
    RethinkRpnCountryOption("CA", "Canada"),
    RethinkRpnCountryOption("CN", "China"),
    RethinkRpnCountryOption("DE", "Germany"),
    RethinkRpnCountryOption("US", "United States"),
)

internal val demoRpnServerCountries = listOf(
    RethinkRpnCountry("auto", "auto", "Automatic location", "", "Best available location", isEnabled = true, isFavourite = false, isFrequent = true, isAutomatic = true),
    RethinkRpnCountry("br", "br-sao-paulo", "Brazil", "BR", "São Paulo", isEnabled = true, isFavourite = true, isFrequent = true, isAutomatic = false),
    RethinkRpnCountry("de", "de-frankfurt", "Germany", "DE", "Frankfurt", isEnabled = true, isFavourite = false, isFrequent = true, isAutomatic = false),
    RethinkRpnCountry("nl", "nl-amsterdam", "Netherlands", "NL", "Amsterdam", isEnabled = false, isFavourite = false, isFrequent = false, isAutomatic = false),
    RethinkRpnCountry("us", "us-new-york", "United States", "US", "New York", isEnabled = true, isFavourite = true, isFrequent = false, isAutomatic = false),
)

internal val demoBlockFreeDnsFilters = listOf(
    RethinkBlockFreeDnsFilter(null, "All"),
    RethinkBlockFreeDnsFilter("RETHINK", "Rethink"),
    RethinkBlockFreeDnsFilter("DOH", "DoH"),
    RethinkBlockFreeDnsFilter("DOT", "DoT"),
    RethinkBlockFreeDnsFilter("DNSCRYPT", "DNSCrypt"),
)

internal val demoBlockFreeDnsItems = listOf(
    RethinkBlockFreeDnsItem("RETHINK::https://basic.rethinkdns.com/", "RethinkDNS Basic", "https://basic.rethinkdns.com/", "RETHINK"),
    RethinkBlockFreeDnsItem("DOH::https://cloudflare-dns.com/dns-query", "Cloudflare", "https://cloudflare-dns.com/dns-query", "DOH"),
    RethinkBlockFreeDnsItem("DOH::https://dns.google/dns-query", "Google", "https://dns.google/dns-query", "DOH"),
    RethinkBlockFreeDnsItem("DOT::one.one.one.one", "Cloudflare DoT", "one.one.one.one", "DOT"),
    RethinkBlockFreeDnsItem("DNSCRYPT::cloudflare", "Cloudflare DNSCrypt", "cloudflare", "DNSCRYPT"),
)

internal val demoDnsProtocols = listOf(
    RethinkDnsProtocol(1, "DoH", "DNS over HTTPS", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure), MaterialSymbols.Filled.Public),
    RethinkDnsProtocol(6, "DoT", "DNS over TLS", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure), MaterialSymbols.Filled.Shield),
    RethinkDnsProtocol(2, "DNSCrypt", "Authenticated DNS", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure, RethinkDnsCapability.Anonymous), MaterialSymbols.Filled.VpnKey),
    RethinkDnsProtocol(3, "DP", "DNS Proxy", listOf(RethinkDnsCapability.Fast), MaterialSymbols.Filled.Dns),
    RethinkDnsProtocol(7, "ODoH", "Oblivious DoH", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure, RethinkDnsCapability.Anonymous), MaterialSymbols.Filled.ShieldMoon),
    RethinkDnsProtocol(4, "Rethink", "Blocklist-aware DoH", listOf(RethinkDnsCapability.Fast, RethinkDnsCapability.Private, RethinkDnsCapability.Secure), MaterialSymbols.Filled.Shield),
)

internal val demoDialOptions = listOf(
    RethinkAntiCensorshipOption("SPLIT_AUTO", "Auto", "Automatically split connections when needed."),
    RethinkAntiCensorshipOption("SPLIT_TCP", "TCP", "Apply splitting to TCP connections."),
    RethinkAntiCensorshipOption("SPLIT_TCP_TLS", "TLS", "Apply splitting to TCP and TLS traffic."),
    RethinkAntiCensorshipOption("DESYNC", "Desync", "Use desynchronization for supported networks."),
    RethinkAntiCensorshipOption("NEVER_SPLIT", "Never", "Do not split connections."),
    RethinkAntiCensorshipOption("TCP_PROXY", "TCP proxy", "Route compatible traffic through a TCP proxy."),
)

internal val demoRetryOptions = listOf(
    RethinkAntiCensorshipOption("RETRY_NEVER", "Never", "Do not retry with anti-censorship settings."),
    RethinkAntiCensorshipOption("RETRY_WITH_SPLIT", "Auto", "Retry using the selected splitting strategy."),
    RethinkAntiCensorshipOption("RETRY_AFTER_SPLIT", "Always", "Always retry after applying the splitting strategy."),
)

internal val demoFirewallAppListStrings = RethinkFirewallAppListStrings(
    title = "Apps",
    searchHint = { count -> "Search $count apps" },
    refresh = "Refresh",
    filter = "Filter",
    rules = "Rules",
    clearSearch = "Clear search",
    emptyTitle = "No apps found",
    emptyDescription = "Try another filter or search term.",
    view = "View",
    installed = "Installed",
    system = "System",
    all = "All",
    status = "Status",
    categories = "Categories",
    clear = "Clear",
    noCategories = "No categories available.",
    apply = "Apply",
    cancel = "Cancel",
    close = "Close",
    enabled = "Enabled",
    disabled = "Disabled",
    proxy = "Proxy",
    wifiActionLabel = { blocked -> if (blocked) "Allow on Wi-Fi" else "Block on Wi-Fi" },
    mobileActionLabel = { blocked -> if (blocked) "Allow on mobile data" else "Block on mobile data" },
    bulkDescription = "Apply a rule to all apps currently shown in this demo.",
    selectedApps = { count -> "$count apps" },
    actionLabel = { action -> when (action) {
        RethinkFirewallBulkAction.Wifi -> "Wi-Fi"
        RethinkFirewallBulkAction.Mobile -> "Mobile data"
        RethinkFirewallBulkAction.Bypass -> "Bypass firewall"
        RethinkFirewallBulkAction.BypassDns -> "Bypass DNS + firewall"
        RethinkFirewallBulkAction.Exclude -> "Exclude"
        RethinkFirewallBulkAction.Lockdown -> "Lockdown"
    } },
    actionDescription = { action -> when (action) {
        RethinkFirewallBulkAction.Wifi -> "Allow or block this group on Wi-Fi."
        RethinkFirewallBulkAction.Mobile -> "Allow or block this group on mobile data."
        RethinkFirewallBulkAction.Bypass -> "Ignore universal firewall rules."
        RethinkFirewallBulkAction.BypassDns -> "Ignore DNS and firewall rules."
        RethinkFirewallBulkAction.Exclude -> "Exclude this group from DNS and firewall handling."
        RethinkFirewallBulkAction.Lockdown -> "Block all traffic except trusted addresses."
    } },
    bulkDialogCopy = { action, active ->
        val verb = if (active) "Disable" else "Enable"
        RethinkFirewallBulkDialogCopy("$verb ${demoFirewallActionName(action)}?", "This updates the demo state only.")
    },
    filterLabel = { filter -> when (filter) {
        RethinkFirewallFilter.All -> "All"
        RethinkFirewallFilter.Allowed -> "Allowed"
        RethinkFirewallFilter.Blocked -> "Blocked"
        RethinkFirewallFilter.Bypass -> "Bypass"
        RethinkFirewallFilter.Excluded -> "Excluded"
        RethinkFirewallFilter.Lockdown -> "Lockdown"
        RethinkFirewallFilter.BlockedWifi -> "Wi-Fi blocked"
        RethinkFirewallFilter.BlockedMobile -> "Mobile blocked"
    } },
    statusLabel = { status -> when (status) {
        RethinkFirewallAppStatus.Allowed -> ""
        RethinkFirewallAppStatus.Blocked -> "Blocked"
        RethinkFirewallAppStatus.Bypass -> "Bypass"
        RethinkFirewallAppStatus.Excluded -> "Excluded"
        RethinkFirewallAppStatus.Lockdown -> "Lockdown"
        RethinkFirewallAppStatus.Unknown -> "Unknown"
    } },
)

internal fun demoFirewallActionName(action: RethinkFirewallBulkAction) = when (action) {
    RethinkFirewallBulkAction.Wifi -> "Wi-Fi rule"
    RethinkFirewallBulkAction.Mobile -> "mobile rule"
    RethinkFirewallBulkAction.Bypass -> "firewall bypass"
    RethinkFirewallBulkAction.BypassDns -> "DNS + firewall bypass"
    RethinkFirewallBulkAction.Exclude -> "exclusion"
    RethinkFirewallBulkAction.Lockdown -> "lockdown"
}

internal val demoFirewallApps =
    RethinkWebDemoAppCatalog.entries.mapIndexed { index, app ->
        val isBlocked = app.packageName in setOf("com.demo.github", "com.demo.reader", "com.netflix.mediaclient", "com.zhiliaoapp.musically")
        val isBypassed = app.packageName in setOf("com.demo.messenger", "org.telegram.messenger", "com.discord")
        val status = when {
            app.packageName == "android.systemui" || app.packageName == "com.google.android.gms" -> RethinkFirewallAppStatus.Excluded
            isBlocked -> RethinkFirewallAppStatus.Blocked
            isBypassed -> RethinkFirewallAppStatus.Bypass
            else -> RethinkFirewallAppStatus.Allowed
        }
        val traffic = listOf(
            "↓ 842 MB · ↑ 71 MB", "↓ 194 MB · ↑ 28 MB", "↓ 96 MB · ↑ 12 MB", "↓ 46 MB · ↑ 8 MB",
            "↓ 31 MB · ↑ 4 MB", "↓ 22 MB · ↑ 3 MB", "↓ 17 MB · ↑ 2 MB", "↓ 8 MB · ↑ 1 MB",
        )[index % 8]
        when (app.packageName) {
            "android.systemui", "com.google.android.gms" -> RethinkFirewallApp(app.packageName, app.uid, app.appName, app.packageName, status, false, false, hasInternetPermission = false)
            else -> RethinkFirewallApp(
                id = app.packageName,
                uid = app.uid,
                appName = app.appName,
                packageName = app.packageName,
                status = status,
                wifiBlocked = isBlocked,
                mobileBlocked = isBlocked && app.packageName in setOf("com.demo.reader", "com.zhiliaoapp.musically"),
                dataUsage = traffic,
                proxyEnabled = isBypassed,
            )
        }
    }

internal val demoCustomRulesStrings = RethinkCustomRulesStrings(
    universalTitle = "IP & port rules",
    appWiseTitle = "App-wise rules",
    appRulesTitle = "App IP and domain rules",
    ipRules = "IP rules",
    domainRules = "Domain rules",
    ip = "IP",
    domain = "Domain",
    universal = "Universal",
    appWise = "App-wise",
    search = "Search",
    clearSearch = "Clear search",
    loading = "Loading…",
    noIpRules = "No IP rules found",
    noDomainRules = "No domain rules found",
    add = "Add",
    cancel = "Cancel",
    delete = "Delete",
    block = "Blocked",
    trust = "Trusted",
    bypass = "Bypass",
    noRule = "No rule",
    uid = { "UID $it" },
)

internal val demoFirewallRules = listOf(
    RethinkFirewallRule("universal-ip-1", -1000, "203.0.113.0/24", RethinkFirewallRuleStatus.Block, port = 443),
    RethinkFirewallRule("universal-ip-2", -1000, "2001:db8::/32", RethinkFirewallRuleStatus.Trust),
    RethinkFirewallRule("universal-domain-1", -1000, "ads.example.net", RethinkFirewallRuleStatus.Block, tab = RethinkRulesTab.DOMAIN),
    RethinkFirewallRule("universal-domain-2", -1000, "cdn.example.org", RethinkFirewallRuleStatus.Trust, tab = RethinkRulesTab.DOMAIN),
    RethinkFirewallRule("browser-ip-1", 1001, "198.51.100.42", RethinkFirewallRuleStatus.Block, port = 80, ownerName = "Browser"),
    RethinkFirewallRule("browser-domain-1", 1001, "telemetry.example.com", RethinkFirewallRuleStatus.Block, tab = RethinkRulesTab.DOMAIN, ownerName = "Browser"),
    RethinkFirewallRule("messenger-domain-1", 1004, "media.example.net", RethinkFirewallRuleStatus.Bypass, tab = RethinkRulesTab.DOMAIN, ownerName = "Messenger"),
)

internal val demoUniversalFirewallSettings = listOf(
    RethinkUniversalFirewallSetting("device-lock", "Block when device is locked", true, RethinkUniversalFirewallIcon.DeviceLock, 14),
    RethinkUniversalFirewallSetting("background", "Block apps in the background", false, RethinkUniversalFirewallIcon.Background, 6),
    RethinkUniversalFirewallSetting("unknown", "Block unknown connections", true, RethinkUniversalFirewallIcon.Unknown, 27),
    RethinkUniversalFirewallSetting("udp", "Block UDP connections", false, RethinkUniversalFirewallIcon.Udp, 3),
    RethinkUniversalFirewallSetting("dns", "Disallow DNS bypass", true, RethinkUniversalFirewallIcon.Dns, 8),
    RethinkUniversalFirewallSetting("new-app", "Block newly installed apps", false, RethinkUniversalFirewallIcon.NewApp, 0),
    RethinkUniversalFirewallSetting("metered", "Block metered connections", false, RethinkUniversalFirewallIcon.Metered, 2),
    RethinkUniversalFirewallSetting("http", "Block HTTP connections", false, RethinkUniversalFirewallIcon.Http, 9),
    RethinkUniversalFirewallSetting("lockdown", "Universal lockdown", false, RethinkUniversalFirewallIcon.Lockdown, 0),
)

internal val demoBlocklistPrivacy = RethinkBlocklistGroup("privacy", "Privacy", "Block trackers and analytics.")
internal val demoBlocklistSecurity = RethinkBlocklistGroup("security", "Security", "Block malware and phishing hosts.")
internal val demoBlocklistPacks = listOf(
    RethinkBlocklistPack("privacy-standard", demoBlocklistPrivacy, "standard privacy", 7, true),
    RethinkBlocklistPack("privacy-strict", demoBlocklistPrivacy, "strict privacy", 5, false),
    RethinkBlocklistPack("security-core", demoBlocklistSecurity, "core security", 4, true),
)
internal val demoBlocklistTags = listOf(
    RethinkBlocklistFileTag("easyprivacy", demoBlocklistPrivacy, "Tracking", "EasyPrivacy", 18_449, 1, selected = true),
    RethinkBlocklistFileTag("oisd", demoBlocklistPrivacy, "Comprehensive", "OISD", 95_123, 0, selected = false),
    RethinkBlocklistFileTag("phishing", demoBlocklistSecurity, "Threats", "Phishing Army", 32_451, 2, selected = true),
)

internal val demoDnsSettingsState = RethinkDnsSettingsState(
    connectedDnsName = "RethinkDNS, https://sky.rethinkdns.com",
    dnsLatency = "(24 ms)",
    dnsType = RethinkDnsType.RethinkRemote,
    isRethinkDnsConnected = true,
    fetchFavIcon = true,
    enableDnsAlg = true,
    periodicallyCheckBlocklistUpdate = true,
    enableDnsCache = true,
    blocklistEnabled = true,
    numberOfLocalBlocklists = 9,
    allowedDnsRecordTypesSize = 5,
    showSplitDns = true,
    showBypassDnsBlock = true,
)

internal val demoDnsSettingsStrings = RethinkDnsSettingsStrings(
    title = "DNS settings", refresh = "Refresh DNS", modesSection = "Resolver", systemDns = "System DNS", systemDnsDescription = "Use the network's configured DNS server.",
    customDns = "Custom DNS", customDnsDescription = "Choose a private resolver and protocol.", rethinkDns = "RethinkDNS", rethinkDnsDescription = "Use RethinkDNS blocklists and resolver.",
    smartDns = "Smart DNS", smartDnsDescription = "Choose DNS automatically for the current network.", connectedDescription = "Connected through the selected RethinkDNS endpoint.",
    blockSection = "Blocklists", localBlocklists = "Local blocklists", localBlocklistsDescription = { "$it local blocklists are active." }, localBlocklistsDisabledDescription = "No local blocklist is active.",
    enabled = "Enabled", disabled = "Disabled", customDownloader = "Custom downloader", customDownloaderDescription = "Use the download manager selected by the system.",
    periodicUpdates = "Periodic updates", periodicUpdatesDescription = "Check for blocklist updates regularly.", filteringSection = "Filtering", dnsAlg = "DNS ALG", dnsAlgDescription = "Apply DNS-aware filtering rules.",
    splitDns = "Split DNS", splitDnsDescription = "Use different resolvers where appropriate.", rulesAsFirewall = "Treat DNS rules as firewall rules", rulesAsFirewallDescription = "Apply matching DNS rules to connection decisions.",
    recordTypes = "Allowed DNS record types", recordTypesDescription = "Control which DNS records the resolver accepts.", auto = "Automatic", blockFreeSection = "Trusted DNS bypass",
    blockFreeLabel = { mode -> when (mode) { RethinkBlockFreeDnsMode.Auto -> "Automatic"; RethinkBlockFreeDnsMode.Global -> "Global"; RethinkBlockFreeDnsMode.Fallback -> "Fallback" } },
    blockFreeDescription = { mode -> when (mode) { RethinkBlockFreeDnsMode.Auto -> "Choose trusted DNS when needed."; RethinkBlockFreeDnsMode.Global -> "Always use trusted DNS for bypass."; RethinkBlockFreeDnsMode.Fallback -> "Use trusted DNS only after a failure." } },
    trustedEndpoint = "Choose trusted DNS endpoint", trustedEndpointDescription = "Select the resolver used for trusted-DNS bypass.", advancedSection = "Advanced",
    favicons = "Website favicons", faviconsDescription = "Fetch icons for domains in logs.", dnsCache = "DNS cache", dnsCacheDescription = "Cache recent DNS answers locally.",
    proxyDns = "Proxy DNS", proxyDnsDescription = "Resolve DNS through the configured proxy.", undelegatedDomains = "System DNS for undelegated domains", undelegatedDomainsDescription = "Ask the system resolver for local domains.",
    fallbackDns = "Fallback DNS bypass", fallbackDnsDescription = "Use fallback DNS to bypass blocked queries.", preventLeaks = "Prevent DNS leaks", preventLeaksDescription = "Keep DNS traffic inside the protected tunnel.",
    aboutItem = { "About $it" },
)

internal val demoMiscToggles = listOf(
    RethinkMiscToggle("general_logs", "Enable logs", "Keep local diagnostic logs for troubleshooting.", true, RethinkMiscSettingIcon.Logs),
    RethinkMiscToggle("general_autostart", "Start on boot", "Start protection after the device restarts.", false, RethinkMiscSettingIcon.AutoStart),
    RethinkMiscToggle("general_tombstone", "Remember removed apps", "Keep rules for apps that are temporarily uninstalled.", true, RethinkMiscSettingIcon.Tombstone),
    RethinkMiscToggle("general_firewall_bubble", "Firewall bubble", "Show quick firewall controls over other apps.", false, RethinkMiscSettingIcon.FirewallBubble),
    RethinkMiscToggle("general_ip_info", "Download IP information", "Enrich connection rows with IP ownership details.", true, RethinkMiscSettingIcon.IpInfo),
)

internal val demoDatabasePreviews = listOf(
    RethinkDatabaseTablePreview(
        table = "AppInfo",
        rowCount = 120,
        columnCount = 17,
        dumpPreview = "Table: AppInfo\nuid\tpackageName\tappName\tfirewallStatus\n10001\tcom.demo.browser\tBrowser\t5\n10002\tcom.demo.messenger\tMessenger\t2\n",
        isTruncated = false,
    ),
    RethinkDatabaseTablePreview(
        table = "ConnectionTracker",
        rowCount = 4320,
        columnCount = 21,
        dumpPreview = "Table: ConnectionTracker\nuid\tipAddress\tport\tblocked\n10001\t1.1.1.1\t443\t0\n10005\t203.0.113.14\t443\t1\n…\n",
        isTruncated = true,
    ),
    RethinkDatabaseTablePreview(
        table = "DnsLog",
        rowCount = 1284,
        columnCount = 16,
        dumpPreview = "Table: DnsLog\nuid\tqueryStr\tresponse\tblocked\n10001\tapi.github.com\t140.82.112.5\t0\n10005\tads.example.net\t\t1\n…\n",
        isTruncated = true,
    ),
)

internal val demoEvents = listOf(
    RethinkEventRow("event-1", "Now", RethinkEventSeverity.Low, "VPN", "VPN start", "Protection connected", "The VPN service established its tunnel.", true, "Low"),
    RethinkEventRow("event-2", "2m", RethinkEventSeverity.Medium, "DNS", "DNS server change", "Resolver changed", "RethinkDNS selected the preferred encrypted resolver.", severityLabel = "Medium"),
    RethinkEventRow("event-3", "8m", RethinkEventSeverity.High, "Firewall", "Firewall block", "Blocked a tracking connection", "ads.example.net matched a universal block rule.", severityLabel = "High"),
)

internal fun RethinkEventSource.label() = when (this) {
    RethinkEventSource.Ui -> "UI"
    RethinkEventSource.Vpn -> "VPN"
    RethinkEventSource.Dns -> "DNS"
    RethinkEventSource.Firewall -> "Firewall"
    RethinkEventSource.System -> "System"
    RethinkEventSource.Service -> "Service"
    RethinkEventSource.Worker -> "Worker"
    RethinkEventSource.Manager -> "Manager"
    RethinkEventSource.Proxy -> "Proxy"
}

internal val demoNetworkLogs = listOf(
    RethinkLogRowModel(
        id = "network-1", destination = "api.github.com", appLabel = "GitHub", typeLabel = "HTTPS", timeLabel = "Now",
        isBlocked = false, allowedLabel = "Allowed", blockedLabel = "Blocked",
        icon = { RethinkWebDemoAppIcon("com.demo.github", "GitHub", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "HTTPS"),
            RethinkLogDetail("Endpoint", "140.82.112.5:443", monospace = true),
            RethinkLogDetail("Usage", "↓ 1.2 MB · ↑ 84 KB"),
            RethinkLogDetail("Latency", "24 ms"),
        ),
    ),
    RethinkLogRowModel(
        id = "network-2", destination = "ads.example.net", appLabel = "Reader", typeLabel = "HTTPS", timeLabel = "1m",
        isBlocked = true, allowedLabel = "Allowed", blockedLabel = "Blocked",
        icon = { RethinkWebDemoAppIcon("com.demo.reader", "Reader", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "HTTPS"),
            RethinkLogDetail("Rule", "Block tracking domains", isError = true),
            RethinkLogDetail("Endpoint", "203.0.113.14:443", monospace = true),
        ),
    ),
    RethinkLogRowModel(
        id = "network-3", destination = "cdn.rethinkdns.com", appLabel = "RethinkDNS", typeLabel = "QUIC", timeLabel = "3m",
        isBlocked = false, allowedLabel = "Allowed", blockedLabel = "Blocked",
        icon = { RethinkWebDemoAppIcon("com.rethinkdns.app", "RethinkDNS", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "QUIC"),
            RethinkLogDetail("Endpoint", "198.51.100.7:443", monospace = true),
            RethinkLogDetail("Duration", "2m 14s"),
        ),
    ),
)

internal fun List<RethinkLogRowModel>.toDemoLogAppOptions() =
    groupBy { it.appLabel }
        .map { (label, rows) -> RethinkLogAppOption(id = label, label = label, count = rows.size) }
        .sortedBy { it.label }

private val demoLatencyStrings = RethinkLogLatencyStrings(title = "Latency", fast = "fast", ok = "ok", slow = "slow")

internal val demoDnsLogs = listOf(
    RethinkLogRowModel(
        id = "dns-1", destination = "api.github.com", appLabel = "GitHub", typeLabel = "DoH", timeLabel = "Now",
        isBlocked = false, allowedLabel = "Allowed", blockedLabel = "Blocked", latencyMs = 24, latencyStrings = demoLatencyStrings,
        icon = { RethinkWebDemoAppIcon("com.demo.github", "GitHub", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "DoH"),
            RethinkLogDetail("Response", "140.82.112.5", monospace = true),
            RethinkLogDetail("Resolver", "1.1.1.1", monospace = true),
        ),
    ),
    RethinkLogRowModel(
        id = "dns-2", destination = "tracker.example.org", appLabel = "Browser", typeLabel = "DoH", timeLabel = "2m",
        isBlocked = true, allowedLabel = "Allowed", blockedLabel = "Blocked", latencyMs = 6, latencyStrings = demoLatencyStrings,
        icon = { RethinkWebDemoAppIcon("com.demo.browser", "Browser", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "DoH"),
            RethinkLogDetail("Status", "Blocked by tracker list", isError = true),
        ),
        blocklistsLabel = "3 blocklists matched",
    ),
)

internal val demoConsoleLogs = listOf(
    RethinkConsoleLogItem('I', "09:41:02", "VpnController: VPN service connected"),
    RethinkConsoleLogItem('D', "09:41:03", "DnsTransport: resolver 1.1.1.1 latency 24 ms"),
    RethinkConsoleLogItem('W', "09:41:15", "ConnectionMonitor: network changed, re-binding sockets"),
    RethinkConsoleLogItem('E', "09:42:40", "WireGuard: handshake with peer timed out"),
)

internal fun demoConfigureSections(
    onOpenDetail: (RethinkRoute) -> Unit,
) = listOf(
    RethinkConfigureSection(
        "Protection",
        listOf(
            demoConfigureEntry("apps", "Apps", MaterialSymbols.Filled.Apps, RethinkRoute.AppList, onOpenDetail, accent = ConfigureAccents.apps),
            demoConfigureEntry("dns", "DNS", MaterialSymbols.Filled.Dns, RethinkRoute.DnsList, onOpenDetail, accent = ConfigureAccents.dns),
            demoConfigureEntry("firewall", "Firewall", KeepoutIcons.Firewall, RethinkRoute.FirewallSettings(), onOpenDetail, accent = ConfigureAccents.firewall),
            demoConfigureEntry("proxy", "Proxy", MaterialSymbols.Filled.ConversionPath, RethinkRoute.ProxySettings(), onOpenDetail, accent = ConfigureAccents.proxy),
        ),
        RethinkConfigureLayout.GridFour,
    ),
    RethinkConfigureSection(
        "System",
        listOf(
            demoConfigureEntry("network", "Network", MaterialSymbols.Filled.Wifi, RethinkRoute.TunnelSettings(), onOpenDetail, accent = ConfigureAccents.network),
            demoConfigureEntry("rpn", "Rethink Plus", MaterialSymbols.Filled.Star, RethinkRoute.RpnAccount, onOpenDetail, accent = ConfigureAccents.plus),
            demoConfigureEntry("settings", "App preferences", MaterialSymbols.Filled.Tune, RethinkRoute.MiscSettings(), onOpenDetail, "Backup, device, and app behavior", ConfigureAccents.settings),
            demoConfigureEntry("logs", "Logs", MaterialSymbols.Filled.FormatListBulleted, RethinkRoute.NetworkLogs(), onOpenDetail, "Network and DNS logging", ConfigureAccents.logs),
        ),
        RethinkConfigureLayout.GridTriad,
    ),
    RethinkConfigureSection(
        "Routing and DNS",
        listOf(
            demoConfigureEntry("dns-settings", "DNS settings", MaterialSymbols.Filled.Tune, RethinkRoute.DnsSettings, onOpenDetail, "Resolver, filtering, and advanced options", ConfigureAccents.dns),
            demoConfigureEntry("blocklists", "Blocklists", KeepoutIcons.Blocklists, RethinkRoute.Blocklists, onOpenDetail, "Choose privacy and security sources", ConfigureAccents.firewall),
            demoConfigureEntry("wireguard", "WireGuard", MaterialSymbols.Filled.VpnKey, RethinkRoute.WireGuard, onOpenDetail, "Create, import, and manage tunnels", ConfigureAccents.proxy),
            demoConfigureEntry("rpn-countries", "Rethink locations", MaterialSymbols.Filled.Public, RethinkRoute.RpnCountries, onOpenDetail, "Select RPN servers", ConfigureAccents.network),
            demoConfigureEntry("anti-censorship", "Anti-censorship", MaterialSymbols.Filled.DominoMask, RethinkRoute.AntiCensorship, onOpenDetail, "Connection splitting and retries", ConfigureAccents.antiCensorship),
        ),
        RethinkConfigureLayout.List,
    ),
    RethinkConfigureSection(
        "Diagnostics",
        listOf(
            demoConfigureEntry("console", "Console logs", MaterialSymbols.Filled.Terminal, RethinkRoute.ConsoleLogs, onOpenDetail, "Engine diagnostics", ConfigureAccents.logs),
            demoConfigureEntry("events", "Event logs", MaterialSymbols.Filled.History, RethinkRoute.Events, onOpenDetail, "System and debug events", ConfigureAccents.settings),
            demoConfigureEntry("ping", "Connectivity checks", MaterialSymbols.Filled.NetworkCheck, RethinkRoute.PingTest, onOpenDetail, "Test RPN reachability", ConfigureAccents.network),
            demoConfigureEntry("database", "Database inspector", MaterialSymbols.Filled.Database, RethinkRoute.Database, onOpenDetail, "Browse local data", ConfigureAccents.advanced),
        ),
        RethinkConfigureLayout.List,
    ),
)

private fun demoConfigureEntry(
    id: String,
    title: String,
    icon: ImageVector,
    destination: RethinkRoute,
    onOpenDetail: (RethinkRoute) -> Unit,
    subtitle: String? = null,
    accent: Color = Color.Unspecified,
) = RethinkConfigureEntry(
    id = id,
    title = title,
    icon = { Icon(icon, null) },
    onClick = { onOpenDetail(destination) },
    subtitle = subtitle,
    keywords = listOf(title),
    accent = accent,
)
