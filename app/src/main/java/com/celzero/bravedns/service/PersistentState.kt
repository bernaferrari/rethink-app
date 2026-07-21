/*
 * Copyright 2020 RethinkDNS and its authors
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
package com.celzero.bravedns.service

import android.content.Context
import android.os.Handler
import kotlinx.coroutines.flow.SharedFlow
import android.os.Looper
import android.widget.Toast
import com.celzero.bravedns.datastore.SyncPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.DnsCryptRelayEndpoint
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.ui.compose.settings.DialStrategies
import com.celzero.bravedns.ui.compose.settings.RetryStrategies
import com.celzero.bravedns.util.Constants
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Constants.Companion.INVALID_PORT
import com.celzero.bravedns.util.InternetProtocol
import com.celzero.bravedns.util.PcapMode
import com.celzero.bravedns.util.ResourceRecordTypes
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastR
import org.koin.core.component.KoinComponent

class PersistentState(context: Context) : KoinComponent {
    private val appContext = context.applicationContext
    private val store = SyncPreferencesStore(appContext)
    private val defaultDnsName = appContext.getString(R.string.default_dns_name)

    fun preferenceKeyChanges(): SharedFlow<String> = store.preferenceKeyChanges

    companion object {
        const val BRAVE_MODE = "brave_mode"
        const val BACKGROUND_MODE = "background_mode"
        const val ALLOW_BYPASS = "allow_bypass"
        const val LOCAL_BLOCK_LIST = "enable_local_list"
        const val LOCAL_BLOCK_LIST_STAMP = "local_block_list_stamp"
        const val LOCAL_BLOCK_LIST_UPDATE = "local_block_list_downloaded_time"
        const val PROXY_TYPE = "proxy_proxytype"
        const val NETWORK = "add_all_networks_to_vpn"
        const val NOTIFICATION_ACTION = "notification_action"
        const val DNS_CHANGE = "connected_dns_name"
        const val INTERNET_PROTOCOL = "internet_protocol"
        const val PROTOCOL_TRANSLATION = "protocol_translation"
        const val DEFAULT_DNS_SERVER = "default_dns_query"
        const val PCAP_MODE = "pcap_mode"
        const val PCAP_FILE_PATH = "pcap_file_path"
        const val REMOTE_BLOCKLIST_UPDATE = "remote_block_list_downloaded_time"
        const val DNS_ALG = "dns_alg"
        const val APP_VERSION = "app_version"
        const val PRIVATE_IPS = "private_ips"
        const val RETHINK_IN_RETHINK = "route_rethink_in_rethink"
        const val PREVENT_DNS_LEAKS = "prevent_dns_leaks"
        const val CONNECTIVITY_CHECKS = "connectivity_check"
        const val NOTIFICATION_PERMISSION = "notification_permission_request"
        const val EXCLUDE_APPS_IN_PROXY = "exclude_apps_in_proxy"
        const val BIOMETRIC_AUTH = "biometric_authentication"
        const val ANTI_CENSORSHIP_TYPE = "dial_strategy"
        const val RETRY_STRATEGY = "retry_strategy"
        const val ENDPOINT_INDEPENDENCE = "endpoint_independence"
        const val TCP_KEEP_ALIVE = "tcp_keep_alive"
        const val USE_SYSTEM_DNS_FOR_UNDELEGATED_DOMAINS = "use_system_dns_for_undelegated_domains"
        const val NETWORK_ENGINE_EXPERIMENTAL = "network_engine_experimental"
        const val USE_RPN = "rpn_state"
        const val RPN_MODE = "rpn_mode"
        const val DIAL_TIMEOUT_SEC = "dial_timeout_sec"
        const val AUTO_DIALS_PARALLEL = "auto_dials_parallel"
        const val STALL_ON_NO_NETWORK = "fail_open_on_no_network"
        const val TUN_NETWORK_POLICY = "tun_network_handling_policy"
        const val USE_MAX_MTU = "use_max_mtu"
        const val SET_VPN_BUILDER_TO_METERED = "set_vpn_builder_to_metered"
        const val PANIC_RANDOM = "panic_random"

        // SE Proxy for Anti-Censorship
        const val AUTO_PROXY_ENABLED = "auto_proxy_enabled"

        // Custom LAN IP settings for VPN tunnel
        const val CUSTOM_LAN_MODE_IPS_CHANGED = "custom_lan_mode_ip_changed"

        const val FIREWALL_BUBBLE = "pref_firewall_bubble_enabled"
    }

    // when vpn is started by the user, this is set to true; set to false when user stops
    // the vpn. In case vpn crashes, this value remains true, which is expected.
    private var _vpnEnabled by store.boolean("enabled", false)

    // OOBE (out-of-the-box experience) screens are shown if the user
    // launches the app for the very first time (after install or post clear-data)
    var firstTimeLaunch by store.boolean("is_first_time_launch", true)

    // One among AppConfig.BraveMode enum; 2's default, which is BraveMode.DNS_FIREWAL
    var braveMode by store.int("brave_mode", AppConfig.BraveMode.DNS_FIREWALL.mode)

    // enable / disable logging dns and tcp/udp connections to db
    var logsEnabled by store.boolean("local_logs", true)

    // the last-set app version; useful to detect an update where the current
    // app version is going to be greater than the one stored, and so, update flow
    // can be triggered accordingly, if any,
    var appVersion by store.int("app_version", 0)

    // Last known time when app update checks were done (successful?)
    var lastAppUpdateCheck by store.long("app_update_last_check", 0)

    // total blocklists set by the user for RethinkDNS+ (server-side dns blocking)
    private var numberOfRemoteBlocklists by store.int("remote_block_list_count", 0)

    // total blocklists set by the user (on-device dns blocking)
    var numberOfLocalBlocklists by store.int("local_block_list_count", 0)

    // whether all udp connection except dns must be dropped
    private var _udpBlocked by store.boolean("block_udp_traffic_other_than_dns", false)

    // user chosen blocklists stored custom dictionary indexed in base64
    var localBlocklistStamp by store.string("local_block_list_stamp", if (Utilities.isHeadlessFlavour()) "1:YAYBACABEDAgAA==" else "")

    // whether to drop packets when the source app originating the reqs couldn't be determined
    private var _blockUnknownConnections by store.boolean("block_unknown_connections", false)

    // whether user has enable on-device blocklists
    var blocklistEnabled by store.boolean("enable_local_list", Utilities.isHeadlessFlavour())

    // the version (which is a unix timestamp) of the current rethinkdns+ remote blocklist files
    var remoteBlocklistTimestamp by store.long("remote_block_list_downloaded_time", INIT_TIME_MS)

    // the version (which is a unix timestamp) of the current on-device blocklist files
    var localBlocklistTimestamp by store.long("local_block_list_downloaded_time", 0)

    // user set http proxy port
    var httpProxyPort by store.int("http_proxy_port", INVALID_PORT)

    // user set http proxy ip / hostname
    var httpProxyHostAddress by store.string("http_proxy_ipaddress", "http://127.0.0.1:8118")

    // whether apps subject to the RethinkDNS VPN tunnel can bypass the tunnel on-demand
    // default: false
    var allowBypass by store.boolean("allow_bypass", false)

    // user set among AppConfig.DnsType enum; RETHINK_REMOTE is default which is Rethink-DoH
    var dnsType by store.int("dns_type", if (!Utilities.isHeadlessFlavour()) AppConfig.DnsType.RETHINK_REMOTE.type
                else AppConfig.DnsType.SYSTEM_DNS.type)

    // whether the app must attempt to startup on reboot if it was running before shutdown
    var prefAutoStartBootUp by store.boolean("auto_start_on_boot", true)

    // user set preference whether firewall should block all connections when device is locked
    private var _blockWhenDeviceLocked by store.boolean("screen_state", false)

    // whether to block connections from apps not in the foreground
    private var _blockAppWhenBackground by store.boolean("background_mode", false)

    // whether to check for app updates once-a-week (on website / play-store builds)
    var checkForAppUpdate by store.boolean("check_for_app_update", true)

    // last connected dns label name and url
    var connectedDnsName by store.string("connected_dns_name", defaultDnsName)

    // the current light/dark theme; 0's the default which is "Set by System"
    var theme by store.int("app_theme", 0)

    // selected accent/color preset for compose theme, 0 = auto (legacy behavior)
    var themeColorPreset by store.int("app_theme_color_preset", 0)

    // user selected notification action type, ref: Constants#NOTIFICATION_ACTION_STOP
    var notificationActionType by store.int("notification_action", 0)

    // add all networks (say, both wifi / mobile) with internet capability to the vpn tunnel
    var useMultipleNetworks by store.boolean("add_all_networks_to_vpn", false)

    // user selected proxy type (e.g., http, socks5)
    var proxyType by store.string("proxy_proxytype", AppConfig.ProxyType.NONE.name)

    // user selected proxy provider, as of now two providers (custom, orbot)
    var proxyProvider by store.string("proxy_proxyprovider", AppConfig.ProxyProvider.NONE.name)

    // the last collected app exit info's timestamp
    var lastAppExitInfoTimestamp by store.long("prev_trace_timestamp", INIT_TIME_MS)

    // fetch fav icons for domains in dns request
    var fetchFavIcon by store.boolean("fav_icon_enabled", !Utilities.isFdroidFlavour())

    // whether to show "what's new" chip on the homescreen, usually
    // shown after a update and until the user dismisses it
    var showWhatsNewChip by store.boolean("show_whats_new_chip", true)

    // block dns which are not resolved by app
    private var _disallowDnsBypass by store.boolean("disallow_dns_bypass", false)

    // trap all packets on port 53 to be sent to a dns endpoint or just the packets sent to vpn's
    // dns-ip
    var preventDnsLeaks by store.boolean("prevent_dns_leaks", true)

    // block all newly installed apps
    private var _blockNewlyInstalledApp by store.boolean("block_new_app", false)

    // user setting to use custom download manager or android's default download manager
    // default: true, i.e., use in-build download manager, as we see lot of failures with
    // android's download manager because of the blocking nature of the app
    var useCustomDownloadManager by store.boolean("use_custom_download_managet", true)

    // custom download manager's last generated id
    var customDownloaderLastGeneratedId by store.long("custom_downloader_last_generated_id", 0)

    // android download manager's active download ids (comma-separated)
    var androidDownloadManagerIds by store.string("android_download_manager_ids", "")

    // local timestamp for which the update is available
    var newestLocalBlocklistTimestamp by store.long("local_blocklist_update_ts", INIT_TIME_MS)

    // remote timestamp for which the update is available
    var newestRemoteBlocklistTimestamp by store.long("remote_blocklist_update_ts", INIT_TIME_MS)

    // auto-check for blocklist update periodically (once in a day)
    var periodicallyCheckBlocklistUpdate by store.boolean("check_blocklist_update", false)

    // user-preferred Internet Protocol type, default IPv4
    var internetProtocolType by store.int(
        INTERNET_PROTOCOL,
        if (!Utilities.isHeadlessFlavour()) InternetProtocol.IPv4.id
        else InternetProtocol.IPv46.id,
    )

    // user-preferred 6to4 protocol translation, on IPv6 mode (default: PTMODEAUTO)
    var protocolTranslationType by store.boolean(PROTOCOL_TRANSLATION, false)

    // filter IPv6 compatible IPv4 address in custom ips
    var filterIpv4inIpv6 by store.boolean("filter_ip4_ipv6", true)

    // universal firewall settings to block all http connections
    private var _blockHttpConnections by store.boolean("block_http_connections", false)

    // universal firewall settings to block all metered connections
    private var _blockMeteredConnections by store.boolean("block_metered_connections", false)

    // universal firewall settings to lockdown all apps
    private var _universalLockdown by store.boolean("universal_lockdown", false)

    // notification permission request (Android 13 ana above)
    var shouldRequestNotificationPermission by store.boolean("notification_permission_request", true)

    // make notification persistent (Android 13 and above), default false
    var persistentNotification by store.boolean("persistent_notification", false)

    // biometric authentication TODO: remove this
    var biometricAuth by store.boolean("biometric_authentication", false)

    // bio-metric authentication type
    var biometricAuthType by store.int("biometric_authentication_type", 0)

    // enable dns alg
    var enableDnsAlg by store.boolean("dns_alg", false)

    // default dns url
    var defaultDnsUrl by store.string("default_dns_query", Constants.DEFAULT_DNS_LIST[1].url)

    // packet capture type
    var pcapMode by store.int("pcap_mode", PcapMode.NONE.id)

    // packet capture file path
    var pcapFilePath by store.string("pcap_file_path", "")

    // dns caching in tunnel
    var enableDnsCache by store.boolean("dns_cache", true)

    // private ips, default false (route private ips to tunnel)
    var privateIps by store.boolean("private_ips", false)

    // biometric last auth time
    var biometricAuthTime by store.long("biometric_auth_time", INIT_TIME_MS)

    // go logger level, default 3 -> info
    var goLoggerLevel by store.long("go_logger_level", 3)
    var includeFileTrace by store.boolean("include_file_trace", false)

    var appTestMode by store.boolean("app_test_mode", false)

    var blockFreeDns by store.string("block_free_dns", "")

    var blockFreeDnsMode by store.int("block_free_dns_mode", BlockFreeDnsMode.AUTO.mode)
    var floodWireGuard by store.boolean("flood_wireguard", false)
    var socketBufferSizeBytes by store.int("socket_buffer_size_bytes", 524288)
    var goMaxMemory by store.long("go_max_memory", -1L)

    // firewall bubble feature toggle
    var firewallBubbleEnabled by store.boolean("pref_firewall_bubble_enabled", false)

    // previous data usage check timestamp
    var prevDataUsageCheck by store.long("prev_data_usage_check", INIT_TIME_MS)

    // route rethink in rethink
    var routeRethinkInRethink by store.boolean("route_rethink_in_rethink", false)

    // perform connectivity checks
    var connectivityChecks by store.boolean("connectivity_check", Utilities.isPlayStoreFlavour())

    // proxy dns requests over proxy
    var proxyDns by store.boolean("proxy_dns", true)

    // exclude apps which are configured in proxy (socks5, http, dns proxy)
    var excludeAppsInProxy by store.boolean("exclude_apps_in_proxy", true)

    var pingv4Ips by store.string("ping_ipv4_ips", Constants.ip4probes.joinToString(","))

    var pingv6Ips by store.string("ping_ipv6_ips", Constants.ip6probes.joinToString(","))

    var pingv4Url by store.string("ping_ipv4_url", Constants.urlV4probes.joinToString(","))

    var pingv6Url by store.string("ping_ipv6_url", Constants.urlV6probes.joinToString(","))


    // anti-censorship type (auto, split_tls, split_tcp, desync)
    var dialStrategy by store.int("dial_strategy", DialStrategies.SPLIT_AUTO.mode)

    // retry strategy type (before split, after split, never)
    var retryStrategy by store.int("retry_strategy", RetryStrategies.RETRY_AFTER_SPLIT.mode)

    // bypass blocking in dns level, decision is made in flow() (see BraveVPNService#flow)
    var bypassBlockInDns by store.boolean("bypass_block_in_dns", false)

    // randomize listen port for advanced wireguard configuration, default false
    // restart of tunnel when wireguard is enabled is required to randomize the port to work properly
    // this is not a user facing option, but a developer option
    var randomizeListenPort by store.boolean("randomize_listen_port", true)

    // endpoint independent mapping/filtering
    var endpointIndependence by store.boolean("endpoint_independence", false)

    var tcpKeepAlive by store.boolean("tcp_keep_alive", false)

    // enable split dns, default on Android R and above, as we can identify app which is sending dns
    var splitDns by store.boolean("split_dns", isAtleastR())

    // use system dns for undelegatedDomains
    var useSystemDnsForUndelegatedDomains by store.boolean("use_system_dns_for_undelegated_domains", false)

    // different modes the rpn proxy can function, see enum RpnMode
    var rpnMode by store.int("rpn_mode", 1)

    var rpnState by store.int(USE_RPN, 0)

    // current rpn state, see enum RpnState
    //var rpnState by store.int("rpn_state", RpnProxyManager.RpnState.DISABLED.id)

    // subscribe product id for the current user, empty string if not subscribed
    var rpnProductId by store.string("rpn_product_id", "")
    var rpnDnsTunTypes by store.string(
        "rpn_dns_tun_types",
        RpnProxyManager.DnsMode.PRIVACY.tunType
    )
    var rpnAutoExcludedCcs by store.string("rpn_auto_excluded_ccs", "")
    var rpnConfigHandlingManual by store.boolean("rpn_config_handling_manual", false)
    var rpnAlwaysChangeIdentity by store.boolean("rpn_always_change_identity", false)
    var rpnPort by store.int("rpn_port", 0)

    var nwEngExperimentalFeatures by store.boolean("network_engine_experimental", false)

    var dialTimeoutSec by store.int("dial_timeout_sec", 0)

    // treat only mobile data as metered
    var treatOnlyMobileNetworkAsMetered by store.boolean("treat_only_mobile_nw_as_metered", false)

    var showConfettiOnRPlus by store.boolean("show_confetti_on_rplus", true)

    // Compose home onboarding; increment the version when the tour content changes.
    var guidedTourCompleted by store.boolean("guided_tour_completed", false)
    var guidedTourVersion by store.int("guided_tour_version", 0)

    var autoDialsParallel by store.boolean("auto_dials_parallel", false)

    // user setting whether to download ip info for the given ip address
    var downloadIpInfo by store.boolean("download_ip_info", Utilities.isPlayStoreFlavour())

    // user setting to allow only added packages can trigger the app
    var appTriggerPackages by store.string("app_trigger_packages", "")

    // last key rotation time
    var pipKeyRotationTime by store.long("pip_key_rotation_time", INIT_TIME_MS)

    // perform auto or manual network connectivity checks
    var performAutoNetworkConnectivityChecks by store.boolean("perform_auto_network_connectivity_checks", true)

    // stall on no network
    // TODO: add routes as normal but do not send fd to netstack
    // repopulateTrackedNetworks also fails open see isAnyNwValidated
    var stallOnNoNetwork by store.boolean("fail_open_on_no_network", false)

    // last grace period reminder time, when rethinkdns+ is enabled and user is cancelled/expired
    // this is used to show a reminder to the user to renew the subscription with grace period
    var lastGracePeriodReminderTime by store.long("last_grace_period_reminder_time", INIT_TIME_MS)

    var newSettings by store.string("new_settings", "")
    var newSettingsSeen by store.string("new_settings_seen", "")
    var appUpdateTimeTs by store.long("app_update_time_ts", INIT_TIME_MS)

    // 0 - auto, 1 - relaxed, 2 - aggressive, 3 - fixed
    var vpnBuilderPolicy by store.int("tun_network_handling_policy", 0)

    // whether to use default dns for trusted ips and domains
    var useFallbackDnsToBypass by store.boolean("use_fallback_dns_to_bypass", true)

    // Firebase error reporting enabled (only for play and website variants)
    var firebaseErrorReportingEnabled by store.boolean("firebase_error_reporting", Utilities.isPlayStoreFlavour())

    // setting to enable/disable tombstone apps feature
    var tombstoneApps by store.boolean("tombstone_apps", false)

    // Token for Firebase userId
    var firebaseUserToken by store.string("firebase_user_token", "")
    var firebaseUserTokenTimestamp by store.long("firebase_user_token_timestamp", 0L)

    // Tombstone reporting is resumable across process restarts.
    var lastReportedTombstoneFile by store.string("last_reported_tombstone_file", "")

    // experimental feature to use max mtu
    var useMaxMtu by store.boolean("use_max_mtu", false)

    // set vpn builder to metered/unmetered
    var setVpnBuilderToMetered by store.boolean("set_vpn_builder_to_metered", false)

    // debug settings, panic random
    var panicRandom by store.boolean("panic_random", false)

    // universal rule, block all non A & AAAA dns responses
    private var _blockOtherDnsRecordTypes by store.boolean("block_non_ip_dns_responses", false)

    // global lockdown for wireguard proxy
    var wgGlobalLockdown by store.boolean("wg_global_lockdown", false)

    // True after encrypted WireGuard profiles have been migrated to plain config files.
    var wireguardPlainFileMigrationDone by store.boolean("wg_plain_file_migration_done", false)

    // Last successful RPN device registration and notification state.
    var deviceRegistrationTimestamp by store.long("device_registration_timestamp", 0L)
    var showRethinkBlockNotification by store.boolean("show_rethink_block_notification", true)

    val orbotConnectionStatus = MutableStateFlow(false)
    val vpnEnabled = MutableStateFlow(false)
    val universalRulesCount = MutableStateFlow(0)
    private val proxyStatus = MutableStateFlow(-1)

    // data class to store dnscrypt relay details
    data class DnsCryptRelayDetails(val relay: DnsCryptRelayEndpoint, val added: Boolean)

    val dnsCryptRelays = MutableStateFlow<DnsCryptRelayDetails?>(null)

    val remoteBlocklistCount = MutableStateFlow(0)

    fun setVpnEnabled(isOn: Boolean) {
        vpnEnabled.value = isOn
        _vpnEnabled = isOn
    }

    fun getVpnEnabled(): Boolean {
        return _vpnEnabled
    }

    fun setRemoteBlocklistCount(c: Int) {
        numberOfRemoteBlocklists = c
        remoteBlocklistCount.value = c
    }

    fun getRemoteBlocklistCount(): Int {
        return numberOfRemoteBlocklists
    }

    private fun setUniversalRulesCount() {
        val list =
            listOf(
                _blockHttpConnections,
                _blockMeteredConnections,
                _universalLockdown,
                _blockNewlyInstalledApp,
                _disallowDnsBypass,
                _udpBlocked,
                _blockUnknownConnections,
                _blockAppWhenBackground,
                _blockWhenDeviceLocked
            )
        universalRulesCount.value = list.count { it }
    }

    private var universalRulesCountSynced = false

    fun getUniversalRulesCount(): Int {
        if (!universalRulesCountSynced) {
            setUniversalRulesCount()
            universalRulesCountSynced = true
        }
        return universalRulesCount.value
    }

    fun setBlockHttpConnections(b: Boolean) {
        _blockHttpConnections = b
        setUniversalRulesCount()
    }

    fun getBlockHttpConnections(): Boolean {
        return _blockHttpConnections
    }

    fun setBlockMeteredConnections(b: Boolean) {
        _blockMeteredConnections = b
        setUniversalRulesCount()
    }

    fun getBlockMeteredConnections(): Boolean {
        return _blockMeteredConnections
    }

    fun setUniversalLockdown(b: Boolean) {
        _universalLockdown = b
        setUniversalRulesCount()
    }

    fun getUniversalLockdown(): Boolean {
        return _universalLockdown
    }

    fun setBlockNewlyInstalledApp(b: Boolean) {
        _blockNewlyInstalledApp = b
        setUniversalRulesCount()
    }

    fun getBlockNewlyInstalledApp(): Boolean {
        return _blockNewlyInstalledApp
    }

    fun setDisallowDnsBypass(b: Boolean) {
        _disallowDnsBypass = b
        setUniversalRulesCount()
    }

    fun getDisallowDnsBypass(): Boolean {
        return _disallowDnsBypass
    }

    fun setUdpBlocked(b: Boolean) {
        _udpBlocked = b
        setUniversalRulesCount()
    }

    fun getUdpBlocked(): Boolean {
        return _udpBlocked
    }

    fun setBlockUnknownConnections(b: Boolean) {
        _blockUnknownConnections = b
        setUniversalRulesCount()
    }

    fun getBlockUnknownConnections(): Boolean {
        return _blockUnknownConnections
    }

    fun setBlockAppWhenBackground(b: Boolean) {
        _blockAppWhenBackground = b
        setUniversalRulesCount()
    }

    fun getBlockAppWhenBackground(): Boolean {
        return _blockAppWhenBackground
    }

    fun setBlockWhenDeviceLocked(b: Boolean) {
        _blockWhenDeviceLocked = b
        setUniversalRulesCount()
    }

    fun getBlockWhenDeviceLocked(): Boolean {
        return _blockWhenDeviceLocked
    }

    fun getProxyStatus(): StateFlow<Int> {
        return updateProxyStatus()
    }

    fun updateProxyStatus(): StateFlow<Int> {
        val status =
            when (AppConfig.ProxyProvider.getProxyProvider(proxyProvider)) {
                AppConfig.ProxyProvider.WIREGUARD -> {
                    R.string.lbl_wireguard
                }
                AppConfig.ProxyProvider.ORBOT -> {
                    R.string.orbot
                }
                AppConfig.ProxyProvider.TCP -> {
                    R.string.orbot_socks5
                }
                AppConfig.ProxyProvider.CUSTOM -> {
                    val type = AppConfig.ProxyType.of(proxyType)
                    when (type) {
                        AppConfig.ProxyType.SOCKS5 -> {
                            R.string.lbl_socks5
                        }
                        AppConfig.ProxyType.HTTP -> {
                            R.string.lbl_http
                        }
                        else -> {
                            R.string.lbl_http_socks5
                        }
                    }
                }
                else -> {
                    -1
                }
            }
        proxyStatus.value = status
        return proxyStatus
    }

    /**
     * Enable settings which are dependent on stability program participation.
     * Currently, only Firebase error reporting is enabled here.
     */
    fun enableStabilityDependentSettings(context: Context) {
        // Skip for fdroid flavor
        if (Utilities.isFdroidFlavour()) {
            return
        }

        // Enable Firebase error reporting for play and website variants
        if (!firebaseErrorReportingEnabled) {
            firebaseErrorReportingEnabled = true
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, context.getString(R.string.stability_program_toast), Toast.LENGTH_LONG).show()
            }
        }

        return
    }

    // Allowed DNS record types (stored as comma-separated enum names)
    // Default: A, AAAA, CNAME, HTTPS, SVCB, IPSECKEY
    internal var allowedDnsRecordTypesString by store.string("allowed_dns_record_types", setOf(
            ResourceRecordTypes.A.name,
            ResourceRecordTypes.AAAA.name,
            ResourceRecordTypes.CNAME.name,
            ResourceRecordTypes.HTTPS.name,
            ResourceRecordTypes.SVCB.name,
            ResourceRecordTypes.IPSECKEY.name
        ).joinToString(","))

    // Auto mode for DNS record types - when enabled, all record types are allowed
    // Default: true (Auto mode ON)
    var dnsRecordTypesAutoMode by store.boolean("dns_record_types_auto_mode", true)

    fun getAllowedDnsRecordTypes(): Set<String> {
        // If Auto mode is enabled, return all record types
        if (dnsRecordTypesAutoMode) {
            return ResourceRecordTypes.entries
                .filter { it != ResourceRecordTypes.UNKNOWN }
                .map { it.name }
                .toSet()
        }

        val value = allowedDnsRecordTypesString
        return if (value.isEmpty()) {
            emptySet()
        } else {
            value.split(",").filter { it.isNotEmpty() }.toSet()
        }
    }

    fun setAllowedDnsRecordTypes(types: Set<String>) {
        allowedDnsRecordTypesString = types.joinToString(",")
    }

    fun getAllowedDnsRecordTypesAsEnum(): Set<ResourceRecordTypes> {
        return getAllowedDnsRecordTypes().mapNotNull { name ->
            try {
                ResourceRecordTypes.valueOf(name)
            } catch (_: IllegalArgumentException) {
                null
            }
        }.toSet()
    }

    // SE Proxy for Anti-Censorship
    var autoProxyEnabled by store.boolean(AUTO_PROXY_ENABLED, false)

    // Custom LAN IP configuration mode: 0 = AUTO (default), 1 = MANUAL
    var customLanIpMode by store.boolean("custom_lan_ip_mode", false)

    // Custom LAN IPs. Store IP and prefix together as a single value (e.g., "10.111.222.1/24").
    // Empty string means: use defaults.
    var customLanGatewayIpv4 by store.string("custom_lan_gateway_ipv4", "10.111.222.1/24")
    var customLanGatewayIpv6 by store.string("custom_lan_gateway_ipv6", "fd66:f83a:c650::1/120")

    var customLanRouterIpv4 by store.string("custom_lan_router_ipv4", "10.111.222.2/32")
    var customLanRouterIpv6 by store.string("custom_lan_router_ipv6", "fd66:f83a:c650::2/128")

    var customLanDnsIpv4 by store.string("custom_lan_dns_ipv4", "10.111.222.3/32")
    var customLanDnsIpv6 by store.string("custom_lan_dns_ipv6", "fd66:f83a:c650::3/128")

    var customModeOrIpChanged by store.boolean("custom_lan_mode_ip_changed", false)

    fun snapshotPreferences(): Map<String, Any?> = store.snapshot()

    fun restorePreferences(snapshot: Map<String, Any?>) = store.restore(snapshot)
}
