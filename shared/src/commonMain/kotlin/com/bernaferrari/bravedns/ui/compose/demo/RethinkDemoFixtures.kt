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

internal val demoNavigationItems = listOf(
    RethinkNavigationItem(
        RethinkRootDestination.Home.name,
        "Home",
        MaterialSymbols.Filled.Home,
        MaterialSymbols.Outlined.Home,
    ),
    RethinkNavigationItem(
        RethinkRootDestination.Statistics.name,
        "Statistics",
        MaterialSymbols.Filled.Star,
        MaterialSymbols.Outlined.Star,
    ),
    RethinkNavigationItem(
        RethinkRootDestination.Configure.name,
        "Settings",
        MaterialSymbols.Filled.Settings,
        MaterialSymbols.Outlined.Settings,
    ),
)

internal val demoHomeStrings = RethinkHomeStrings(
    productName = "Rethink",
    protected = "Protected",
    notActive = "Not active",
    start = "Start",
    stop = "Stop",
    protectedSubtitle = "Traffic is being filtered on this device",
    inactiveSubtitle = "Turn on protection to secure your traffic",
    failingSubtitle = "Rethink is trying to restore your connection",
    connections = "Connections",
    dnsQueries = "DNS queries",
)

internal val demoConfigureStrings = RethinkConfigureStrings(
    title = "Settings", searchHint = "Search settings", openSearch = "Search settings",
    closeSearch = "Close search", clearSearch = "Clear search",
    noResultsTitle = "No settings found", noResultsSubtitle = "Try another search term.",
)

internal val demoAboutStrings = RethinkAboutStrings(
    appName = "RethinkDNS",
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
    settings = "Settings",
    generalSettings = "General settings",
    appInfo = "App info",
    vpnProfile = "VPN profile",
    notifications = "Notifications",
    diagnostics = "Diagnostics",
    statistics = "Statistics",
    databaseDump = "Database dump",
    flightRecorder = "Flight recorder",
    eventLogs = "Event logs",
    supportedBy = "Supported by Mozilla",
)

internal val demoAppearanceStrings = RethinkAppearanceStrings(
    heading = "Appearance",
    system = "System",
    light = "Light",
    dark = "Dark",
)

internal val demoAppearancePresets = listOf(
    RethinkAppearancePreset(1, "Dynamic", null, isDynamic = true),
    RethinkAppearancePreset(2, "Coral", Color(0xFF804136)),
    RethinkAppearancePreset(11, "Rose", Color(0xFFB23A5E)),
    RethinkAppearancePreset(6, "Orange", Color(0xFFC76519)),
    RethinkAppearancePreset(8, "Amber", Color(0xFFA86800)),
    RethinkAppearancePreset(7, "Green", Color(0xFF27854A)),
    RethinkAppearancePreset(3, "Teal", Color(0xFF167D70)),
    RethinkAppearancePreset(9, "Cyan", Color(0xFF007C91)),
    RethinkAppearancePreset(4, "Blue", Color(0xFF3568B7)),
    RethinkAppearancePreset(10, "Indigo", Color(0xFF545CC9)),
    RethinkAppearancePreset(5, "Purple", Color(0xFF7855B4)),
)

internal val demoLogsStrings = RethinkLogsStrings(
    title = "Logs",
    network = "Network",
    dns = "DNS",
    moreActions = "More actions",
    refresh = "Refresh",
    clear = "Clear logs",
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
    shareDescription = "Share logs",
    clearDescription = "Clear console logs",
    reportLabel = "Report a bug",
    confirmLabel = "Done",
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
) = RethinkSummaryStatisticsRow(
    id = id,
    headline = name,
    supporting = supporting,
    metric = connections,
    countryFlag = flag,
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
    RethinkDnsProtocol(1, "DoH", "DNS over HTTPS", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure), MaterialSymbols.Filled.Language),
    RethinkDnsProtocol(6, "DoT", "DNS over TLS", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure), MaterialSymbols.Filled.Shield),
    RethinkDnsProtocol(2, "DNSCrypt", "DNSCrypt", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure, RethinkDnsCapability.Anonymous), MaterialSymbols.Filled.VpnKey),
    RethinkDnsProtocol(3, "DP", "DNS Proxy", listOf(RethinkDnsCapability.Fast), MaterialSymbols.Filled.Dns),
    RethinkDnsProtocol(7, "ODoH", "Oblivious DoH", listOf(RethinkDnsCapability.Private, RethinkDnsCapability.Secure, RethinkDnsCapability.Anonymous), MaterialSymbols.Filled.ShieldMoon),
    RethinkDnsProtocol(4, "Rethink", "RethinkDNS", listOf(RethinkDnsCapability.Fast, RethinkDnsCapability.Private, RethinkDnsCapability.Secure), MaterialSymbols.Filled.Security),
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
    cancel = "Done",
    enabled = "Enabled",
    disabled = "Disabled",
    bulkDescription = "Apply a rule to all apps currently shown in this demo.",
    selectedApps = { count -> "Apply to $count apps" },
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
            "842 MB down · 71 MB up", "194 MB down · 28 MB up", "96 MB down · 12 MB up", "46 MB down · 8 MB up",
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
)

internal val demoMiscToggles = listOf(
    RethinkMiscToggle("general_logs", "Enable logs", "Keep local diagnostic logs for troubleshooting.", true, RethinkMiscSettingIcon.Logs),
    RethinkMiscToggle("general_autostart", "Start on boot", "Start protection after the device restarts.", false, RethinkMiscSettingIcon.AutoStart),
    RethinkMiscToggle("general_tombstone", "Remember removed apps", "Keep rules for apps that are temporarily uninstalled.", true, RethinkMiscSettingIcon.Tombstone),
    RethinkMiscToggle("general_firewall_bubble", "Firewall bubble", "Show quick firewall controls over other apps.", false, RethinkMiscSettingIcon.FirewallBubble),
    RethinkMiscToggle("general_ip_info", "Download IP information", "Enrich connection rows with IP ownership details.", true, RethinkMiscSettingIcon.IpInfo),
    RethinkMiscToggle("general_custom_downloader", "Custom downloader", "Use RethinkDNS's downloader for configuration files.", false, RethinkMiscSettingIcon.Downloader),
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
    RethinkEventRow("event-1", "Now", RethinkEventSeverity.Low, "VPN", "VPN start", "Protection connected", "The VPN service established its tunnel.", true),
    RethinkEventRow("event-2", "2m", RethinkEventSeverity.Medium, "DNS", "DNS server change", "Resolver changed", "RethinkDNS selected the preferred encrypted resolver."),
    RethinkEventRow("event-3", "8m", RethinkEventSeverity.High, "Firewall", "Firewall block", "Blocked a tracking connection", "ads.example.net matched a universal block rule."),
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

internal val demoDnsLogs = listOf(
    RethinkLogRowModel(
        id = "dns-1", destination = "api.github.com", appLabel = "GitHub", typeLabel = "DoH", timeLabel = "Now",
        isBlocked = false, allowedLabel = "Allowed", blockedLabel = "Blocked", latencyMs = 24,
        icon = { RethinkWebDemoAppIcon("com.demo.github", "GitHub", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "DoH"),
            RethinkLogDetail("Response", "140.82.112.5", monospace = true),
            RethinkLogDetail("Resolver", "1.1.1.1", monospace = true),
        ),
    ),
    RethinkLogRowModel(
        id = "dns-2", destination = "tracker.example.org", appLabel = "Browser", typeLabel = "DoH", timeLabel = "2m",
        isBlocked = true, allowedLabel = "Allowed", blockedLabel = "Blocked", latencyMs = 6,
        icon = { RethinkWebDemoAppIcon("com.demo.browser", "Browser", Modifier.size(34.dp)) },
        details = listOf(
            RethinkLogDetail("Transport", "DoH"),
            RethinkLogDetail("Status", "Blocked by tracker list", isError = true),
        ),
        blocklistsLabel = "3 blocklists matched",
    ),
)

internal val demoConsoleLogs = listOf(
    RethinkLogRowModel(
        id = "console-1", destination = "VPN service connected", appLabel = "RethinkDNS", typeLabel = "INFO", timeLabel = "Now",
        isBlocked = false, allowedLabel = "Info", blockedLabel = "Error",
        details = listOf(RethinkLogDetail("Component", "VpnController"), RethinkLogDetail("Thread", "main")),
    ),
    RethinkLogRowModel(
        id = "console-2", destination = "Resolver latency 24ms", appLabel = "DNS", typeLabel = "DEBUG", timeLabel = "1m",
        isBlocked = false, allowedLabel = "Info", blockedLabel = "Error",
        details = listOf(RethinkLogDetail("Resolver", "1.1.1.1", monospace = true)),
    ),
)

internal fun demoConfigureSections(
    onOpenDetail: (DemoDetail) -> Unit,
) = listOf(
    RethinkConfigureSection(
        "Protection",
        Color(0xFF804136),
        listOf(
            demoConfigureEntry("apps", "Apps", Color(0xFF74C5FF), MaterialSymbols.Filled.Apps, DemoDetail.FirewallApps, onOpenDetail),
            demoConfigureEntry("dns", "DNS", Color(0xFFC5ACFF), MaterialSymbols.Filled.Dns, DemoDetail.DnsList, onOpenDetail),
            demoConfigureEntry("firewall", "Firewall", Color(0xFFFF907F), MaterialSymbols.Filled.Security, DemoDetail.FirewallSettings, onOpenDetail),
            demoConfigureEntry("proxy", "Proxy", Color(0xFF46EBC8), MaterialSymbols.Filled.VpnKey, DemoDetail.ProxySettings, onOpenDetail),
        ),
        RethinkConfigureLayout.GridFour,
    ),
    RethinkConfigureSection(
        "System",
        Color(0xFF755A54),
        listOf(
            demoConfigureEntry("network", "Network", Color(0xFFA3BCFF), MaterialSymbols.Filled.VpnKey, DemoDetail.TunnelSettings, onOpenDetail),
            demoConfigureEntry("rpn", "Rethink Plus", Color(0xFF9B8CFF), MaterialSymbols.Filled.Star, DemoDetail.RpnAccount, onOpenDetail, "Account and support"),
            demoConfigureEntry("settings", "App preferences", Color(0xFFFFD878), MaterialSymbols.Filled.Settings, DemoDetail.MiscSettings, onOpenDetail, "Appearance, device, and app behavior"),
            demoConfigureEntry("logs", "Logs", Color(0xFF7EED92), MaterialSymbols.Filled.Subject, DemoDetail.Logs, onOpenDetail, "Network and DNS logging"),
            demoConfigureEntry("rpn-countries", "Rethink locations", Color(0xFF7AB6FF), MaterialSymbols.Filled.Public, DemoDetail.RpnCountries, onOpenDetail, "Select RPN servers"),
        ),
        RethinkConfigureLayout.GridTriad,
    ),
    RethinkConfigureSection(
        "Diagnostics",
        Color(0xFF796A96),
        listOf(
            demoConfigureEntry("console", "Console logs", Color(0xFFC5ACFF), MaterialSymbols.Filled.Subject, DemoDetail.ConsoleLogs, onOpenDetail, "Engine diagnostics"),
            demoConfigureEntry("ping", "Connectivity checks", Color(0xFF73D6FF), MaterialSymbols.Filled.Dns, DemoDetail.PingTest, onOpenDetail, "Test RPN reachability"),
            demoConfigureEntry("anti-censorship", "Anti-censorship", Color(0xFFFFBC77), MaterialSymbols.Filled.Security, DemoDetail.AntiCensorship, onOpenDetail, "Connection splitting and retries"),
            demoConfigureEntry("events", "Event logs", Color(0xFFFF83B1), MaterialSymbols.Filled.Subject, DemoDetail.EventLogs, onOpenDetail, "System and debug events"),
            demoConfigureEntry("database", "Database inspector", Color(0xFF84C8FF), MaterialSymbols.Filled.Storage, DemoDetail.Database, onOpenDetail, "Browse local data"),
            demoConfigureEntry("dns-settings", "DNS settings", Color(0xFF8AA5FF), MaterialSymbols.Filled.Dns, DemoDetail.DnsSettings, onOpenDetail, "Resolver, filtering, and advanced options"),
            demoConfigureEntry("blocklists", "Blocklists", Color(0xFFB5A3FF), MaterialSymbols.Filled.Security, DemoDetail.Blocklists, onOpenDetail, "Choose privacy and security sources"),
            demoConfigureEntry("wireguard", "WireGuard", Color(0xFF67D4D4), MaterialSymbols.Filled.VpnKey, DemoDetail.Wireguard, onOpenDetail, "Create, import, and manage tunnels"),
        ),
        RethinkConfigureLayout.List,
    ),
)

private fun demoConfigureEntry(
    id: String,
    title: String,
    accent: Color,
    icon: ImageVector,
    destination: DemoDetail,
    onOpenDetail: (DemoDetail) -> Unit,
    subtitle: String? = null,
) = RethinkConfigureEntry(
    id = id,
    title = title,
    icon = { Icon(icon, null) },
    accent = accent,
    onClick = { onOpenDetail(destination) },
    subtitle = subtitle,
    keywords = listOf(title),
)
