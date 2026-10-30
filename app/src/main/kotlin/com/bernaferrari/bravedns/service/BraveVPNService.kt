/*
 * Copyright 2019 Jigsaw Operations LLC
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
package com.bernaferrari.bravedns.service

import Logger
import Logger.LOG_BATCH_LOGGER
import Logger.LOG_TAG_CONNECTION
import Logger.LOG_TAG_VPN
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.InvalidForegroundServiceTypeException
import android.app.KeyguardManager
import android.app.MissingForegroundServiceTypeException
import android.app.Notification
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock.elapsedRealtime
import android.system.OsConstants.AF_INET
import android.system.OsConstants.AF_INET6
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.customdownloader.IpInfoDownloader
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.data.ConnTrackerMetaData
import com.bernaferrari.bravedns.data.ConnectionSummary
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.database.ConsoleLog
import com.bernaferrari.bravedns.database.EventSource
import com.bernaferrari.bravedns.database.EventType
import com.bernaferrari.bravedns.database.RefreshDatabase
import com.bernaferrari.bravedns.net.go.GoVpnAdapter
import com.bernaferrari.bravedns.net.manager.ConnectionTracer
import com.bernaferrari.bravedns.receiver.UserPresentReceiver
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager
import com.bernaferrari.bravedns.rpnproxy.RpnProxyManager.RpnType
import com.bernaferrari.bravedns.scheduler.RpnProxyUpdateWorker
import com.bernaferrari.bravedns.service.ProxyManager.ID_WG_BASE
import com.bernaferrari.bravedns.service.ProxyManager.isNotLocalAndRpnProxy
import com.bernaferrari.bravedns.ui.HomeScreenActivity
import com.bernaferrari.bravedns.util.AndroidUidConfig
import com.bernaferrari.bravedns.util.BackgroundAccessibilityService
import com.bernaferrari.bravedns.util.BioMetricType
import com.bernaferrari.bravedns.util.CoFactory
import com.bernaferrari.bravedns.util.ConnectivityCheckHelper
import com.bernaferrari.bravedns.util.Constants
import com.bernaferrari.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.bernaferrari.bravedns.util.Constants.Companion.INVALID_UID
import com.bernaferrari.bravedns.util.Constants.Companion.PRIMARY_USER
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.Daemons
import com.bernaferrari.bravedns.util.IPUtil
import com.bernaferrari.bravedns.util.InternetProtocol
import com.bernaferrari.bravedns.util.KnownPorts
import com.bernaferrari.bravedns.util.OrbotHelper
import com.bernaferrari.bravedns.util.Protocol
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.Utilities.isAtleastQ
import com.bernaferrari.bravedns.util.Utilities.isAtleastR
import com.bernaferrari.bravedns.util.Utilities.isAtleastS
import com.bernaferrari.bravedns.util.Utilities.isAtleastU
import com.bernaferrari.bravedns.util.Utilities.isMissingOrInvalidUid
import com.bernaferrari.bravedns.util.Utilities.isUnspecifiedIp
import com.bernaferrari.bravedns.wireguard.WgHopManager
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.Client
import com.celzero.firestack.backend.DNSOpts
import com.celzero.firestack.backend.DNSSummary
import com.celzero.firestack.backend.DNSTransport
import com.celzero.firestack.backend.NetStat
import com.celzero.firestack.backend.Proxy
import com.celzero.firestack.backend.RDNS
import com.celzero.firestack.backend.RouterStats
import com.celzero.firestack.backend.RpnEntitlement
import com.celzero.firestack.backend.RpnServers
import com.celzero.firestack.backend.ServerSummary
import com.celzero.firestack.backend.Tab
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.intra.Controller
import com.celzero.firestack.intra.FlowSummary
import com.celzero.firestack.intra.Mark
import com.celzero.firestack.intra.PreMark
import com.bernaferrari.bravedns.util.ExpiringCache
import inet.ipaddr.HostName
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.IOException
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class BraveVPNService : VpnService(), ConnectionMonitor.NetworkListener, Bridge {

    private val vpnScope = MainScope()
    private val teardown = VpnTeardown(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        disconnect = { stopVpnAdapter() },
        drain = { netLogTracker.stop(vpnScope) },
        cancelServiceWork = {
            vpnScope.cancel("vpnDestroy")
            VpnController.onVpnTeardownComplete(this)
        },
        stopService = { withContext(Dispatchers.Main) { stopSelf() } },
    )

    // used mostly for service to adapter creation and updates
    private var serializer: CoroutineDispatcher = Daemons.make("vpnser")

    private var connectionMonitor: ConnectionMonitor = ConnectionMonitor(this, this, serializer, vpnScope)
    private val connTrackRepository by inject<ConnectionTrackerRepository>()
    private val eventLogger by inject<EventLogger>()

    private var userPresentReceiver: UserPresentReceiver = UserPresentReceiver()

    // multiple coroutines call both signalStopService and makeOrUpdateVpnAdapter and so
    // set and unset this variable on the serializer thread
    @Volatile
    private var vpnAdapter: GoVpnAdapter? = null

    private val flowDispatcher by lazy { Daemons.ioDispatcher("flow", Mark(),  vpnScope) }
    private val inflowDispatcher by lazy { Daemons.ioDispatcher("inflow", Mark(), vpnScope) }
    private val preflowDispatcher by lazy { Daemons.ioDispatcher("preflow", PreMark(), vpnScope) }
    private val dnsQueryDispatcher by lazy { Daemons.ioDispatcher("onQuery", DNSOpts(), vpnScope) }
    private val bind4Dispatcher by lazy { Daemons.ioDispatcher("bind4", Unit, vpnScope) }
    private val bind6Dispatcher by lazy { Daemons.ioDispatcher("bind6", Unit, vpnScope) }
    private val protectDispatcher by lazy { Daemons.ioDispatcher("protect", Unit, vpnScope) }
    private val proxyAddedDispatcher by lazy { Daemons.ioDispatcher("proxyAdded", Unit, vpnScope) }

    // TODO: remove volatile
    @Volatile
    private var builderStats: String = ""
    @Volatile
    private var tunUnderlyingNetworks: String? = null
    @Volatile
    private var lastRxTrafficTime: Long = elapsedRealtime() // tracks rx from onSocketClosed()
    private var testFd: AtomicInteger = AtomicInteger(-1)

    companion object {
        private const val TAG = "VpnService;"
        const val SERVICE_ID = 1 // Only has to be unique within this app.
        const val MEMORY_NOTIFICATION_ID = 29001
        const val NW_ENGINE_NOTIFICATION_ID = 29002

        const val VPN_INTERFACE_MTU: Int = 1500
        // TODO: should be different for IPv4 and IPv6, but for now it is same
        // IPv4: 576, IPv6: 1280
        const val MIN_MTU: Int = 1280
        private const val MAX_MTU: Int = 10000

        // route v4 in v6 only networks?
        const val ROUTE4IN6 = true

        private const val DATA_STALL_THRESHOLD_MS = 30 * 1000L // 30 seconds

        // vpnRoutes are only used for diagnostics, the current implementation will taken
        // into account the vpn routes are handled properly, case: do not route private ips
        private const val RECONCILE_WITH_VPN_ROUTES = false

        const val FIRESTACK_MUST_DUP_TUNFD = true
    }

    private var isLockDownPrevious = AtomicBoolean(false)

    private lateinit var connTracer: ConnectionTracer

    private val rand: Random = Random

    private val appConfig by inject<AppConfig>()
    private val orbotHelper by inject<OrbotHelper>()
    private val persistentState by inject<PersistentState>()
    private val rdb by inject<RefreshDatabase>()
    private val netLogTracker by inject<NetLogTracker>()

    @Volatile
    private var isAccessibilityServiceFunctional: Boolean = false

    @Volatile
    var accessibilityHearbeatTimestamp: Long = INIT_TIME_MS
    private var settingUpOrbot: AtomicBoolean = AtomicBoolean(false)

    private lateinit var activityManager: ActivityManager
    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var cm: ConnectivityManager
    private var keyguardManager: KeyguardManager? = null

    // Modular service managers
    private lateinit var vpnNotificationManager: VpnNotificationManager
    private lateinit var bubbleController: BubbleController
    private lateinit var vpnTunRoutes: VpnTunRoutes
    private lateinit var vpnTunAppPolicy: VpnTunAppPolicy
    private lateinit var vpnDnsTransportController: VpnDnsTransportController
    private lateinit var vpnPrefObservers: VpnPrefObservers
    private lateinit var vpnServiceDiagnostics: VpnServiceDiagnostics
    private lateinit var dnsConfigurationManager: DnsConfigurationManager
    private lateinit var underlyingNetworkManager: UnderlyingNetworkManager
    private lateinit var proxyStateManager: ProxyStateManager
    private lateinit var appsStatsManager: AppsStatsManager
    private lateinit var logsCountManager: LogsCountManager
    private lateinit var firewallStatsManager: FirewallStatsManager
    private lateinit var vpnDnsQueryRouter: VpnDnsQueryRouter

    private var overlayNetworkRefresher: OverlayNetworkRefresher? = null

    private val pcapSession by lazy {
        PcapSessionController(
            applyNative = { path -> vpnAdapter?.setPcapMode(path) ?: path.isEmpty() },
            persist = { mode, path -> appConfig.setPcap(mode, path) },
            latestPath = { appConfig.getPcapFilePath() },
        )
    }

    private val dnsAnswerReasons = DnsAnswerReasons()

    private fun dnsQuestionKey(summary: DNSSummary) = DnsQuestionKey(
        summary.fid, summary.uid, summary.qName.orEmpty(), summary.qType, summary.start,
    )

    private val connectionFirewall by lazy {
        ConnectionFirewall(
            domainRule = ::getDomainRule,
            ipRule = ::ipStatus,
            allowOrbot = ::allowOrbot,
            onUntrackedApp = { uid -> io("addNewApp") { rdb.addNewApp(uid) } },
            resolveNewAppBlocked = ::waitAndCheckIfUidBlocked,
            resolveBackgroundBlocked = ::blockBackgroundData,
            resolveIsUidSystemApp = { FirewallManager.isUidSystemApp(it) },
            resolveDeviceLocked = {
                if (keyguardManager == null) {
                    keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                }
                keyguardManager?.isKeyguardLocked == true
            }
        )
    }

    private var rethinkUid: Int = INVALID_UID

    // used to store the conn-ids that are allowed and active, to show in network logs
    // as active connections. removed when the connection is closed (onSummary)
    private var activeCids = Collections.newSetFromMap(ConcurrentHashMap<CidKey, Boolean>())

    // used to store the ConnTrackerMetaData that has multiple proxy ids associated with it
    // waiting for the connection to be established, the call from postFlow/socketClosed will
    // remove the entry from this map
    private val trackedConnMetaData: ExpiringCache<String, ConnTrackerMetaData> =
        ExpiringCache(
            maxSize = 10_000,
            expireAfterWriteMs = TimeUnit.SECONDS.toMillis(300),
            onRemoval = { _, cm, cause -> handleExpiredConnMetaData(cm, cause) },
        )

    // used to store the conn-ids that need to be closed when device is locked,
    // this is used to close the connections when the device is locked
    // list will exclude bypassed apps, domains and ip rules
    private var activeClosableCids = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // data class to store the connection summary
    data class CidKey(val cid: String, val uid: Int)

    private var excludedApps: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // post underlying networks as live data
    @Volatile
    var underlyingNetworks: ConnectionMonitor.UnderlyingNetworks? = null

    @Volatile
    var overlayNetworks: OverlayNetworks = OverlayNetworks()

    // marks whether the uid is included in the dns requests. universal firewall rules are enforced
    // only when this flag is true, ensuring unknown app DNS requests are blocked and avoiding
    // issues when Android omits uid in dns requests
    private var isUidPresentInAnyDnsRequest: Boolean = false

    private var accessibilityListener: AccessibilityManager.AccessibilityStateChangeListener? = null

    // state-flow to store the region received from onResponse
    val regionFlow: MutableStateFlow<String> = MutableStateFlow("")

    data class OverlayNetworks(
        val has4: Boolean = false,
        val has6: Boolean = false,
        val failOpen: Boolean = true,
        val mtu: Int = Int.MAX_VALUE,
        val proxyGeneration: Long = -1,
    )

    data class Networks(
        val underlyingNws: ConnectionMonitor.UnderlyingNetworks?,
        val overlayNws: OverlayNetworks
    )

    enum class State {
        NEW,
        WORKING,
        FAILING,
        PAUSED,
        NO_INTERNET,
        DNS_SERVER_DOWN,
        DNS_ERROR,
        APP_ERROR
    }

    private fun logd(msg: String) {
        Logger.d(LOG_TAG_VPN, "$TAG $msg")
    }

    override fun bind4(who: String, addrPort: String, fid: Long) = go2kt(bind4Dispatcher) {
        var v4Net = underlyingNetworks?.ipv4Net
        val isAuto = InternetProtocol.isAuto(persistentState.internetProtocolType)
        if (ROUTE4IN6 && isAuto && v4Net.isNullOrEmpty()) {
            v4Net = underlyingNetworks?.ipv6Net
        }

        return@go2kt bindAny(who, addrPort, fid, v4Net ?: emptyList())
    }

    override fun bind6(who: String, addrPort: String, fid: Long) = go2kt(bind6Dispatcher) {
        return@go2kt bindAny(who, addrPort, fid, underlyingNetworks?.ipv6Net ?: emptyList())
    }

    private suspend fun bindAny(
        who: String,
        addrPort: String,
        fid: Long,
        nws: List<ConnectionMonitor.NetworkProperties>
    ) {
        val rinr = persistentState.routeRethinkInRethink
        val curnet = underlyingNetworks

        logd("bind: who: $who, addr: $addrPort, fd: $fid, rinr? $rinr")
        if (rinr) {
            val isRethinkBypassedFromProxy = FirewallManager.getAppInfoByUid(rethinkUid)?.isProxyExcluded ?: false
            if (!isRethinkBypassedFromProxy) {
                // let user set proxy proceed to protect & bind, as rethink is not bypassing proxies
                if (!ProxyManager.isAnyUserSetProxy(who) && who != Backend.Exit) {
                    // do not proceed if rethink is bypassed and proxyId(who) is not user-set proxy
                    // or Exit, this includes Base or any other go related proxies
                    Logger.vv(LOG_TAG_VPN, "bind: rinr, bypassed rethink, who: $who, fd: $fid, addr: $addrPort")
                    return
                }
            } else if (who != Backend.Exit) {
                // do not proceed if rethink within rethink is enabled and proxyId(who) is not exit
                Logger.vv(LOG_TAG_VPN, "bind: rinr, within rethink, who: $who, fd: $fid, addr: $addrPort")
                return
            }
        }

        this.protect(fid.toInt())

        if (nws.isEmpty()) {
            Logger.w(LOG_TAG_VPN, "no network to bind, who: $who, fd: $fid, addr: $addrPort")
            return
        }

        var pfd: ParcelFileDescriptor? = null
        try {
            // split the addrPort to get the IP address and convert it to InetAddress
            val dest = IpRulesManager.splitHostPort(addrPort)
            val destIp = IPAddressString(dest.first).address
            val destPort = dest.second.toIntOrNull()
            val destAddr = destIp.toInetAddress()

            // in case of zero, bind only for wg connections, wireguard tries to bind to
            // network with zero addresses
            if (
                (destIp.isZero && who.startsWith(ID_WG_BASE)) ||
                destIp.isZero ||
                destIp.isLoopback
            ) {
                logd("bind: invalid destIp: $destIp, who: $who, addr: $addrPort")
                return
            }

            pfd = ParcelFileDescriptor.adoptFd(fid.toInt())

            // check if the destination port is DNS port, if so bind to the network where the dns
            // belongs to, else bind to the available network
            val net = if (KnownPorts.isDns(destPort)) curnet?.dnsServers?.get(destAddr) else null
            if (net != null) {
                val ok = bindToNw(net, pfd, fid)
                if (!ok) {
                    Logger.e(LOG_TAG_VPN, "bind failed, who: $who, addr: $addrPort, fd: $fid, handle: ${net.networkHandle}, netid:${netid(net.networkHandle)}")
                } else {
                    logd("bind: dns, who: $who, addr: $addrPort, fd: $fid, handle: ${net.networkHandle}, netid:${netid(net.networkHandle)}, ok: true")
                }
                return
            }

            // who is not used, but kept for future use
            // binding to the underlying network is not working.
            // no need to bind if use active network is true
            if (curnet?.useActive == true) {
                logd("bind: use active network is true, who: $who, addr: $addrPort, fd: $fid")
                return
            }

            nws.forEach {
                if (bindToNw(it.network, pfd, fid)) {
                    logd("bind: nw, who: $who, addr: $addrPort, fd: $fid, handle: ${it.network.networkHandle}, netid:${netid(it.network.networkHandle)}")
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Logger.e(LOG_TAG_VPN, "err bind: who: $who, addr: $addrPort, fd: $fid, ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            Logger.e(LOG_TAG_VPN, "err bind: who: $who, addr: $addrPort, fd: $fid, ${e.message}", e)
        } catch (e: inet.ipaddr.AddressStringException) {
            Logger.e(LOG_TAG_VPN, "err bind: who: $who, addr: $addrPort, fd: $fid, ${e.message}", e)
        } finally {
            pfd?.detachFd()
        }
        Logger.e(LOG_TAG_VPN, "bind failed: who: $who, addr: $addrPort, fd: $fid")
    }

    private fun netid(nwHandle: Long): Long {
        // ref: cs.android.com/android/platform/superproject/main/+/main:packages/modules/Connectivity/framework/src/android/net/Network.java;drc=0209c366627e98d6311629a0592c6e22be7d13e0;l=491
        return nwHandle shr (32)
    }

    fun bindToNwForConnectivityChecks(nw: Network, fid: Long): Boolean {
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.adoptFd(fid.toInt())
            return bindToNw(nw, pfd, fid)
        } catch (e: IOException) {
            Logger.w(LOG_TAG_VPN, "err bindToNwForConnectivityChecks, ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_VPN, "err bindToNwForConnectivityChecks, ${e.message}", e)
        } finally {
            pfd?.detachFd()
        }
        return false
    }

    fun protectFdForConnectivityChecks(fd: Long) {
        this.protect(fd.toInt())
        Logger.v(LOG_TAG_CONNECTION, "fd($fd) protected for connectivity checks")
    }

    suspend fun getPlusResolvers(): List<String> {
        return vpnAdapter?.getPlusResolvers() ?: emptyList()
    }

    suspend fun getPlusTransportById(transportId: String): DNSTransport? {
        return vpnAdapter?.getPlusTransportById(transportId)
    }

    private fun bindToNw(net: Network, pfd: ParcelFileDescriptor, fid: Long): Boolean {
        return try {
            net.bindSocket(pfd.fileDescriptor)
            true
        } catch (e: IOException) {
            Logger.e(LOG_TAG_VPN, "err bindToNw(nw: ${net.networkHandle}, netid: ${netid(net.networkHandle)}, fid: $fid, ${e.message}, $e")
            false
        }
    }

    suspend fun probeIpOrUrl(ipOrUrl: String, useAuto: Boolean): ConnectionMonitor.ProbeResult? {
        val nws = constructNetworkProperties(underlyingNetworks)
        if (useAuto) {
            // in auto mode the ipOrUrl contain scheme:protocol
            val split = ipOrUrl.split(":")
            val scheme = split.firstOrNull() ?: ConnectionMonitor.SCHEME_HTTPS
            val protocol = split.getOrNull(1) ?: ConnectionMonitor.PROTOCOL_V4
            val defaultIps = persistentState.pingv4Ips.split(",").map { it.trim() }
            if (nws.isEmpty()) {
                val res = ConnectivityCheckHelper.probeConnectivityInAutoMode(scheme = scheme, protocol = protocol, ipOrUrl = defaultIps, useKotlinChecks = ConnectionMonitor.USE_KOTLIN_REACHABILITY_CHECKS)
                return ConnectionMonitor.ProbeResult("", res, null)
            }
            nws.forEach { nwprop ->
                val res = ConnectivityCheckHelper.probeConnectivityInAutoMode(nwprop.network, scheme,  protocol, defaultIps,  ConnectionMonitor.USE_KOTLIN_REACHABILITY_CHECKS)
                if (res) {
                    return ConnectionMonitor.ProbeResult("", true, nwprop.capabilities)
                }
            }
            return ConnectionMonitor.ProbeResult("", false, null)
        } else {
            val activeCap = cm.getNetworkCapabilities(cm.activeNetwork) // can be null
            val useKotlinConnectivityChecks = ConnectionMonitor.USE_KOTLIN_REACHABILITY_CHECKS
            return ConnectivityCheckHelper.probeIpOrUrl(
                ipOrUrl,
                nws,
                activeCap,
                useKotlinConnectivityChecks
            )
        }
    }

    private fun constructNetworkProperties(nws: ConnectionMonitor.UnderlyingNetworks?): Set<ConnectionMonitor.NetworkProperties> {
        if (nws == null) {
            Logger.w(LOG_TAG_VPN, "constructNetworkProperties: underlying networks is null")
            return emptySet()
        }
        val nwProps: MutableList<ConnectionMonitor.NetworkProperties> = mutableListOf()
        if (nws.ipv4Net.isNotEmpty()) {
            nwProps.addAll(nws.ipv4Net)
        }
        if (nws.ipv6Net.isNotEmpty()) {
            nwProps.addAll(nws.ipv6Net)
        }
        return nwProps.toSet()
    }

    fun protectSocket(socket: Socket) {
        this.protect(socket)
        Logger.v(LOG_TAG_VPN, "socket protected")
    }

    override fun protect(who: String?, fd: Long) = go2kt(protectDispatcher) {
        if (who == null) {
            Logger.w(LOG_TAG_VPN, "protect: who is null, fd: $fd")
            return@go2kt
        }

        val rinr = persistentState.routeRethinkInRethink
        logd("protect: $who, fd: $fd, rinr? $rinr")
        if (rinr) {
            val isRethinkBypassedFromProxy = FirewallManager.getAppInfoByUid(rethinkUid)?.isProxyExcluded ?: false
            if (!isRethinkBypassedFromProxy) {
                // let user set proxy proceed to protect & bind, as rethink is not bypassing proxies
                if (!ProxyManager.isAnyUserSetProxy(who) && who != Backend.Exit) {
                    // do not proceed if rethink is bypassed and proxyId(who) is not user-set proxy
                    // or Exit, this includes Base or any other go related proxies
                    Logger.vv(LOG_TAG_VPN, "protect: rinr, bypassed rethink, who: $who, fd: $fd")
                    return@go2kt
                }
            } else if (who != Backend.Exit) {
                // do not proceed if rethink within rethink is enabled and proxyId(who) is not exit
                Logger.vv(LOG_TAG_VPN, "protect: rinr, within rethink, who: $who, fd: $fd")
                return@go2kt
            }
        }
        this.protect(fd.toInt())
    }

    private suspend fun getUid(
        recdUid: Int,
        protocol: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): Int {
        if (recdUid != INVALID_UID) {
            return recdUid
        }
        return if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            ioAsync("getUidQ") { connTracer.getUidQ(protocol, srcIp, srcPort, dstIp, dstPort) }
                .await()
        } else {
            recdUid // uid must have been retrieved from procfs by the caller
        }
    }

    /** Checks if incoming connection is blocked by any user-set firewall rule */
    private suspend fun firewall(
        connInfo: ConnTrackerMetaData,
        domains: String?,
        anyRealIpBlocked: Boolean = false,
        isSplApp: Boolean,
        rinr: Boolean,
        isAlg: Boolean = false,
        forDnsAnswer: Boolean = false,
    ): FirewallRuleset {
        val connId = connInfo.connId
        try {
            val uid = connInfo.uid
            if (uid == rethinkUid && !rinr) {
                logd("firewall($connId): rethink uid, $rethinkUid, not processing firewall rules")
                return FirewallRuleset.RULE0
            }

            val appStatus = FirewallManager.appStatus(uid)
            val connectionStatus = FirewallManager.connectionStatus(uid)
            val isTempAllowed = FirewallManager.isTempAllowed(uid)

            val verdict =
                connectionFirewall.evaluate(
                    ConnectionRequest(
                        connId = connId,
                        destIP = connInfo.destIP,
                        destPort = connInfo.destPort,
                        protocol = connInfo.protocol,
                        query = connInfo.query,
                        domains = domains,
                        anyRealIpBlocked = anyRealIpBlocked,
                        isSplApp = isSplApp,
                        rinr = rinr,
                        debugConnInfo = connInfo.toString(),
                        app =
                            AppFirewallSnapshot(
                                uid = uid,
                                rethinkUid = rethinkUid,
                                appStatus = appStatus,
                                connectionStatus = connectionStatus,
                                isTempAllowed = isTempAllowed,
                                allowOrbot = false,
                                isMissingOrInvalidUid = isMissingOrInvalidUid(uid),
                                isUidSystemApp = false,
                                newAppStillBlocked = false,
                                backgroundBlocked = false
                            ),
                        flags =
                            FirewallFlags(
                                blockMeteredConnections = persistentState.getBlockMeteredConnections(),
                                universalLockdown = persistentState.getUniversalLockdown(),
                                blockHttpConnections = persistentState.getBlockHttpConnections(),
                                blockWhenDeviceLocked = persistentState.getBlockWhenDeviceLocked(),
                                deviceLocked = false,
                                udpBlocked = persistentState.getUdpBlocked(),
                                blockUnknownConnections = persistentState.getBlockUnknownConnections() && (!forDnsAnswer || persistentState.splitDns),
                                blockNewlyInstalledApp = persistentState.getBlockNewlyInstalledApp(),
                                disallowDnsBypass = persistentState.getDisallowDnsBypass(),
                                preventDnsLeaks = appConfig.preventDnsLeaks(),
                                isDnsFirewallMode = appConfig.getBraveMode().isDnsFirewallMode(),
                                isLockdown = isLockdown(),
                                isAppPaused = isAppPaused()
                            ),
                        network = NetworkSnapshot(),
                        isMetered = { isConnectionMetered(connInfo.destIP.substringBefore(',')) },
                        isAlg = isAlg,
                        forDnsAnswer = forDnsAnswer,
                    )
                )

            if (!verdict.matchedQuery.isNullOrEmpty()) {
                connInfo.query = verdict.matchedQuery
            }
            if (verdict.closeTrackedOnDeviceLock) {
                closeTrackedConnsOnDeviceLock()
            }
            verdict.logs.forEach { logd(it) }
            return verdict.rule
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (iex: Exception) {
            // TODO: show alerts to user on such exceptions, in a separate ui?
            Logger.crash(LOG_TAG_VPN, "unexpected err in firewall()($connId), block anyway", iex)
            return FirewallRuleset.RULE1C
        }
    }

    private fun getDomainRule(domain: String?, uid: Int): Pair<DomainRulesManager.Status, String?> {
        if (domain.isNullOrEmpty()) {
            return Pair(DomainRulesManager.Status.NONE, "")
        }

        val domains = if (isAtleastR()) {
            // on Android R and above, go will give the first domain as accurate domain so
            // no need to check further domains
            val d = domain.lowercase(Locale.getDefault()).split(",").firstOrNull()
            if (d.isNullOrEmpty()) return Pair(DomainRulesManager.Status.NONE, "")
            listOf(d)
        } else {
            domain.lowercase(Locale.getDefault()).split(",")
        }

        if (domains.isEmpty()) {
            return Pair(DomainRulesManager.Status.NONE, "")
        }
        for (d in domains) {
            val status = DomainRulesManager.status(d, uid)
            if (status != DomainRulesManager.Status.NONE) {
                return Pair(status, d)
            }
        }

        return Pair(DomainRulesManager.Status.NONE, domains.firstOrNull())
    }

    private suspend fun allowOrbot(uid: Int): Boolean {
        return settingUpOrbot.get() &&
                OrbotHelper.ORBOT_PACKAGE_NAME == FirewallManager.getPackageNameByUid(uid)
    }

    private suspend fun waitAndCheckIfUidBlocked(uid: Int): Boolean {
        val allowed = testWithBackoff {
            FirewallManager.hasUid(uid) && !FirewallManager.isUidFirewalled(uid)
        }
        return !allowed
    }

    private fun is4in6FilterRequired(): Boolean {
        return persistentState.filterIpv4inIpv6
    }

    private fun ipStatus(uid: Int, destIp: String, destPort: Int): IpRulesManager.IpRuleStatus {
        if (destIp.isEmpty() || isUnspecifiedIp(destIp)) {
            return IpRulesManager.IpRuleStatus.NONE
        }
        // is ip:port or ip:* blocked / trusted?
        val statusIpPort = IpRulesManager.hasRule(uid, destIp, destPort)
        if (statusIpPort != IpRulesManager.IpRuleStatus.NONE) {
            return statusIpPort // trusted or blocked or bypassed-universal
        }
        // is ipv4 addr as ipv6 blocked / trusted?
        if (is4in6FilterRequired()) {
            val addr = try {
                IPAddressString(destIp).address
            } catch (_: inet.ipaddr.AddressStringException) {
                return IpRulesManager.IpRuleStatus.NONE
            } catch (_: inet.ipaddr.IncompatibleAddressException) {
                return IpRulesManager.IpRuleStatus.NONE
            }

            val ip4in6 = IPUtil.ip4in6(addr) ?: return IpRulesManager.IpRuleStatus.NONE
            val ip4str = ip4in6.toNormalizedString()
            val statusIpPort4in6 = IpRulesManager.hasRule(uid, ip4str, destPort)
            if (statusIpPort4in6 != IpRulesManager.IpRuleStatus.NONE) {
                return statusIpPort4in6 // trusted or blocked or bypassed-universal
            }
        }
        return statusIpPort
    }

    private suspend fun testWithBackoff(
        stallSec: Long = 20,
        durationSec: Long = 10,
        test: suspend () -> Boolean
    ): Boolean {
        val minWaitMs = TimeUnit.SECONDS.toMillis(stallSec)
        var remainingWaitMs = TimeUnit.SECONDS.toMillis(durationSec)
        var attempt = 0
        while (remainingWaitMs > 0) {
            if (test()) return true

            remainingWaitMs = exponentialBackoff(remainingWaitMs, attempt)
            attempt += 1
        }

        delay(minWaitMs + remainingWaitMs)

        return false
    }

    private fun isVpnDns(ip: String): Boolean {
        val isCustomLanIp = persistentState.customLanIpMode
        if (isCustomLanIp) {
            val customDnsIp4 = persistentState.customLanDnsIpv4
            val customDnsIp6 = persistentState.customLanDnsIpv6
            val ipv4Parts = customDnsIp4.split("/").firstOrNull() ?: ""
            val ipv6Parts = customDnsIp6.split("/").firstOrNull() ?: ""
            val ipv4 = HostName(ipv4Parts).toString()
            val ipv6 = HostName(ipv6Parts).toString()
            return when (persistentState.internetProtocolType) {
                InternetProtocol.IPv4.id -> {
                    ip == ipv4
                }

                InternetProtocol.IPv6.id -> {
                    false
                }

                InternetProtocol.IPv46.id -> {
                    ip == ipv4 || ip == ipv6
                }

                InternetProtocol.ALWAYSv46.id -> {
                    ip == ipv4 || ip == ipv6
                }

                else -> {
                    ip == ipv4
                }
            }
        } else {
            val (fakeDnsIpv4, fakeDnsIpv6) = vpnTunRoutes.dnsLanIps()
            return when (persistentState.internetProtocolType) {
                InternetProtocol.IPv4.id -> {
                    ip == fakeDnsIpv4
                }

                InternetProtocol.IPv6.id -> {
                    ip == fakeDnsIpv6
                }

                InternetProtocol.IPv46.id -> {
                    ip == fakeDnsIpv4 || ip == fakeDnsIpv6
                }

                InternetProtocol.ALWAYSv46.id -> {
                    ip == fakeDnsIpv4 || ip == fakeDnsIpv6
                }

                else -> {
                    ip == fakeDnsIpv4
                }
            }
        }
    }

    private fun isDns(port: Int): Boolean {
        return KnownPorts.isDns(port)
    }

    private fun isPrivateDns(port: Int): Boolean {
        return KnownPorts.isDoT(port)
    }

    private fun isConnectionMetered(dst: String): Boolean {
        val curnet = underlyingNetworks
        // assume active network until underlying networks are set by ConnectionMonitor
        // do not use persistentState.useMultipleNetworks
        val useActive = curnet == null || curnet.useActive
        val treatMobileAsMetered = persistentState.treatOnlyMobileNetworkAsMetered
        if (!useActive || isLockdown()) {
            return if (treatMobileAsMetered) {
                // TODO: should this check be a combination of cellular & metered?
                isIfaceCellular(dst)
            } else {
                isIfaceMetered(dst)
            }
        }
        return if (treatMobileAsMetered) {
            isActiveIfaceCellular()
        } else {
            isActiveIfaceMetered()
        }
    }

    private fun isIfaceCellular(dst: String): Boolean {
        val dest = IPAddressString(dst)
        if (dest.isEmpty) {
            Logger.e(LOG_TAG_VPN, "invalid destination IP: $dst")
            return isActiveIfaceCellular()
        }

        val curnet = underlyingNetworks
        val cap =
            if (dest.isZero || dest.isIPv6) { // wildcard addrs(::80, ::443, etc.) are bound to ipv6
                // if there are no network to be bound, fallback to active network
                if (curnet?.ipv6Net?.isEmpty() == true) {
                    return isActiveIfaceCellular()
                }
                curnet?.ipv6Net?.firstOrNull()?.capabilities
            } else {
                // if there are no network to be bound, fallback to active network
                if (curnet?.ipv4Net?.isEmpty() == true) {
                    return isActiveIfaceCellular()
                }
                curnet?.ipv4Net?.firstOrNull()?.capabilities
            }
        // if there are no network to be bound given a destination IP, fallback to active network
        if (cap == null) {
            Logger.e(LOG_TAG_VPN, "no network to be bound for $dst, use active network")
            return isActiveIfaceCellular()
        }
        return cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun isIfaceMetered(dst: String): Boolean {
        val dest = IPAddressString(dst)
        if (dest.isEmpty) {
            Logger.e(LOG_TAG_VPN, "invalid destination IP: $dst")
            return isActiveIfaceMetered()
        }

        // TODO: check for all networks instead of just the first one
        val curnet = underlyingNetworks
        val cap =
            if (dest.isZero || dest.isIPv6) { // wildcard addrs(::80, ::443, etc.) are bound to ipv6
                // if there are no network to be bound, fallback to active network
                if (curnet?.ipv6Net?.isEmpty() == true) {
                    return isActiveIfaceMetered()
                }
                curnet?.ipv6Net?.firstOrNull()?.capabilities
            } else {
                // if there are no network to be bound, fallback to active network
                if (curnet?.ipv4Net?.isEmpty() == true) {
                    return isActiveIfaceMetered()
                }
                curnet?.ipv4Net?.firstOrNull()?.capabilities
            }

        // if there are no network to be bound given a destination IP, fallback to active network
        if (cap == null) {
            Logger.e(LOG_TAG_VPN, "no network to be bound for $dst, use active network")
            return isActiveIfaceMetered()
        }
        return !cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun isActiveIfaceMetered(): Boolean {
        val curnet = underlyingNetworks ?: return false // assume unmetered
        val now = elapsedRealtime()
        val ts = curnet.lastUpdated
        if (abs(now - ts) > Constants.ACTIVE_NETWORK_CHECK_THRESHOLD_MS) {
            curnet.lastUpdated = now
            curnet.isActiveNetworkMetered = cm.isActiveNetworkMetered
        }
        return curnet.isActiveNetworkMetered
    }

    private fun isActiveIfaceCellular(): Boolean {
        val curnet = underlyingNetworks ?: return false // assume unmetered
        val now = elapsedRealtime()
        val ts = curnet.lastUpdated
        if (abs(now - ts) > Constants.ACTIVE_NETWORK_CHECK_THRESHOLD_MS) {
            curnet.lastUpdated = now
            val activeNetwork = cm.activeNetwork
            val cap = cm.getNetworkCapabilities(activeNetwork) ?: return false
            val isCellular = cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            curnet.isActiveNetworkCellular = isCellular
        }
        return curnet.isActiveNetworkCellular
    }

    private suspend fun blockBackgroundData(uid: Int): Boolean {
        if (!persistentState.getBlockAppWhenBackground()) return false

        if (!accessibilityServiceFunctional()) {
            Logger.w(LOG_TAG_VPN, "accessibility service not functional, disable bg-block")
            handleAccessibilityFailure()
            return false
        }
        if (keyguardManager == null) {
            keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        }
        // if keyguard is locked, then the app is in background
        val allowed = testWithBackoff { FirewallManager.isAppForeground(uid, keyguardManager) }

        return !allowed
    }

    private fun handleAccessibilityFailure() {
        // Disable app not in use behaviour when the accessibility failure is detected.
        persistentState.setBlockAppWhenBackground(false)
        vpnNotificationManager.showAccessibilityStoppedNotification()
    }

    private fun accessibilityServiceFunctional(): Boolean {
        val now = elapsedRealtime()
        // Added the INIT_TIME_MS check, encountered a bug during phone restart
        // isAccessibilityServiceRunning default value(false) is passed instead of
        // checking it from accessibility service for the first time.
        if (
            accessibilityHearbeatTimestamp == INIT_TIME_MS ||
            abs(now - accessibilityHearbeatTimestamp) >
            Constants.ACCESSIBILITY_SERVICE_HEARTBEAT_THRESHOLD_MS
        ) {
            accessibilityHearbeatTimestamp = now

            isAccessibilityServiceFunctional =
                Utilities.isAccessibilityServiceEnabled(
                    this,
                    BackgroundAccessibilityService::class.java
                ) &&
                        Utilities.isAccessibilityServiceEnabledViaSettingsSecure(
                            this,
                            BackgroundAccessibilityService::class.java
                        )
        }
        return isAccessibilityServiceFunctional
    }

    // ref: https://stackoverflow.com/a/363692
    private val baseWaitMs = TimeUnit.MILLISECONDS.toMillis(50)

    private suspend fun exponentialBackoff(remainingWaitMs: Long, attempt: Int): Long {
        var tempRemainingWaitMs = remainingWaitMs
        val exponent = exp(attempt)
        val randomValue = rand.nextLong(exponent - baseWaitMs + 1) + baseWaitMs
        val waitTimeMs = min(randomValue, remainingWaitMs)

        tempRemainingWaitMs -= waitTimeMs

        delay(waitTimeMs)

        return tempRemainingWaitMs
    }

    private fun exp(pow: Int): Long {
        return if (pow == 0) {
            baseWaitMs
        } else {
            (1 shl pow) * baseWaitMs
        }
    }

    private suspend fun newBuilder(): Builder {
        val underlyingNws = getUnderlays()
        // prefer view of underlying networks over vpn service lockdown state for being consistent
        // with onNetworksChanged()
        val vpnLockdown = if (isAtleastQ()) {
            underlyingNetworks?.vpnLockdown ?: isLockdownEnabled
        } else {
            false
        }
        tunUnderlyingNetworks = underlyingNws?.joinToString()
        logd("builder: set underlying networks: $tunUnderlyingNetworks")
        // re-hydrate exclude-apps incase it has changed in the interim
        excludedApps = FirewallManager.getExcludedApps()
        return vpnTunAppPolicy.newBuilder(underlyingNws, vpnLockdown, isAppPaused(), excludedApps)
    }

    override fun onCreate() {
        overlayNetworkRefresher = OverlayNetworkRefresher(
            CoroutineScope(vpnScope.coroutineContext + Dispatchers.IO),
            reportError = { error -> Logger.w(LOG_TAG_VPN, "Proxy reachability refresh failed", error) },
        ) {
            val networks = vpnAdapter?.getActiveProxiesIpAndMtu()
            if (networks != null) onOverlayNetworkChanged(networks)
        }
        connTracer = ConnectionTracer(this)
        VpnController.onVpnCreated(this)

        // Temp-allow expiry scheduling is only relevant when VPN is active.
        FirewallManager.initTempAllowScheduler(this)

        io("nlt") {
            Log.d(LOG_BATCH_LOGGER, "vpn: restart $vpnScope")
            netLogTracker.restart(vpnScope)
        }

        activityManager = this.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        accessibilityManager = this.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        keyguardManager = this.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        cm =
            this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        // Initialize modular service managers
        initializeModularManagers()

        if (persistentState.getBlockAppWhenBackground()) {
            registerAccessibilityServiceState()
        }
        registerUserPresentReceiver()
        if (isAtleastQ()) {
            bubbleController.handleIfNeeded()
        }
    }

    private fun initializeModularManagers() {
        vpnNotificationManager = VpnNotificationManager(
            this,
            persistentState,
            appConfig,
            isAppPaused = { isAppPaused() },
            isAppLockEnabled = { isAppLockEnabled() },
        )
        bubbleController = BubbleController(this, persistentState, connTrackRepository, vpnScope)
        vpnTunRoutes = VpnTunRoutes(persistentState, appConfig, cm)
        vpnTunAppPolicy = VpnTunAppPolicy(this, persistentState, appConfig)
        vpnDnsTransportController = VpnDnsTransportController(
            this,
            persistentState,
            appConfig,
            cm,
            serializer,
            vpnScope,
            adapter = { vpnAdapter },
            underlyingNetworks = { underlyingNetworks },
            requestRestart = { vpnRestartTrigger.value = it },
            getDnsStatus = { getDnsStatus(it) },
            logd = ::logd,
        )
        vpnPrefObservers = VpnPrefObservers(
            this,
            persistentState,
            appConfig,
            vpnScope,
            adapter = { vpnAdapter },
            excludedApps = { excludedApps },
            requestRestart = { vpnRestartTrigger.value = it },
            dnsTransport = vpnDnsTransportController,
            notifyForeground = { vpnNotificationManager.notifyForeground() },
            registerAccessibility = ::registerAccessibilityServiceState,
            unregisterAccessibility = ::unregisterAccessibilityServiceState,
            notifyConnectionMonitor = { notifyConnectionMonitor() },
            onTunPolicyChanged = { connectionMonitor.onPolicyChanged() },
            currentMtu = ::mtu,
            handleBubbleIfNeeded = {
                if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                    bubbleController.handleIfNeeded()
                }
            },
            newTunnelOptions = {
                appConfig.newTunnelOptions(
                    this,
                    getFakeDns(),
                    appConfig.getProtocolTranslationMode()
                )
            },
            onOrbotStatus = { settingUpOrbot.set(it) },
            logd = ::logd,
            applyPcapPreferences = { pcapSession.refresh() },
        )
        vpnServiceDiagnostics = VpnServiceDiagnostics(this, persistentState, appConfig)
        dnsConfigurationManager = DnsConfigurationManager(persistentState, appConfig, vpnScope)
        underlyingNetworkManager = UnderlyingNetworkManager(this)
        proxyStateManager = ProxyStateManager(appConfig, persistentState)
        appsStatsManager = AppsStatsManager(FirewallManager, vpnScope)
        logsCountManager = LogsCountManager(appConfig, vpnScope)
        firewallStatsManager = FirewallStatsManager(persistentState, IpRulesManager, DomainRulesManager, vpnScope)
        vpnDnsQueryRouter = VpnDnsQueryRouter(
            persistentState,
            appConfig,
            rethinkUid = { rethinkUid },
            isAppPaused = { isAppPaused() },
            isLockdown = { isLockdown() },
            getDomainRule = ::getDomainRule,
            getUnderlyingSsid = { getUnderlyingSsid() },
            isUidPresentInAnyDnsRequest = { isUidPresentInAnyDnsRequest },
            markUidPresentInDnsRequest = { isUidPresentInAnyDnsRequest = true },
            logd = ::logd,
            normalizeUid = { FirewallManager.appId(it, isPrimaryUser()) },
            isCaptivePortal = ::useCaptivePortalDns,
            isActiveCellular = ::isActiveIfaceCellular,
        )

        underlyingNetworkManager.initialize()
        Logger.i(LOG_TAG_VPN, "Modular managers initialized")
    }

    // Public accessors for modular manager states
    fun getProxyStatusFlow() = proxyStateManager.proxyStatus
    fun getAppsStatsFlow() = appsStatsManager.appsStats
    fun getFirewallStatsFlow() = firewallStatsManager.firewallStats
    fun getLogsCountFlow() = logsCountManager.dnsLogsCount
    fun getNetworkLogsCountFlow() = logsCountManager.networkLogsCount
    fun getUnderlyingNetworkFlow() = underlyingNetworkManager.activeNetwork
    fun getDnsStatusFlow() = dnsConfigurationManager.dnsStatus

    suspend fun refreshProxyStatus() {
        proxyStateManager.updateProxyStatus()
    }

    fun refreshAppsStats() {
        vpnScope.launch { appsStatsManager.refreshStats() }
    }


    private fun registerUserPresentReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(userPresentReceiver, filter)
        Logger.i(LOG_TAG_VPN, "user present receiver registered")
    }

    private fun unregisterUserPresentReceiver() {
        try {
            unregisterReceiver(userPresentReceiver)
            Logger.i(LOG_TAG_VPN, "user present receiver unregistered")
        } catch (_: IllegalArgumentException) {
            Logger.w(LOG_TAG_VPN, "user present receiver not registered")
        }
    }

    private fun isAppLockEnabled(): Boolean {
        if (isAppRunningOnTv()) return false

        // TODO: should we check for last unlock time here?
        BioMetricType.fromValue(persistentState.biometricAuthType).let {
            return it.enabled()
        }
    }

    private fun updateNotificationBuilder(): Notification {
        return vpnNotificationManager.buildForegroundNotification()
    }

    private fun isAppRunningOnTv(): Boolean {
        return try {
            val uiModeManager: UiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
            uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        } catch (_: Exception) {
            false
        }
    }

    private fun getRethinkUid(): Int {
        return Utilities.getApplicationInfo(this, this.packageName)?.uid ?: INVALID_UID
    }

    private fun isPrimaryUser(): Boolean {
        return FirewallManager.userId(rethinkUid) == PRIMARY_USER
    }

    private val vpnRestartTrigger: MutableStateFlow<String> = MutableStateFlow("startVpn")
    @OptIn(FlowPreview::class)
    private fun observeVpnRestartRequests() {
        vpnScope.launch {
            Logger.i(LOG_TAG_VPN, "start restart manager flow")
            // if the string is same, it will not restart the vpn, so adding ts whereever same
            // value requires a restart
            var reasonForEvent = ""
            vpnRestartTrigger
                .debounce(3000)
                .collect { reason ->
                    Logger.v(LOG_TAG_VPN, "RESTART; new restart request: $reason")
                    restartVpnWithNewAppConfig(reason)
                    reasonForEvent = reason
                }
            io("eventLogger") {
                eventLogger.logHigh(
                    EventType.VPN_RESTART,
                    "stopped vpn restart manager flow",
                    EventSource.VPN,
                    userAction = false,
                    details = "reason: $reasonForEvent"
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (teardown.isStopping) return START_NOT_STICKY
        rethinkUid = getRethinkUid()
        val pid = Process.myPid()

        Logger.i(
            LOG_TAG_VPN,
            "onStartCommand, us: $rethinkUid / pid: $pid, primary? ${isPrimaryUser()}"
        )

        VpnController.onConnectionStateChanged(State.NEW)

        // startForeground must run synchronously in onStartCommand (not posted via ui {}).
        // https://developer.android.com/guide/components/fg-service-types
        val startedForeground = if (isAtleastU()) {
            var ok = startForegroundService(FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            if (!ok) {
                Logger.w(LOG_TAG_VPN, "start service failed, retrying with connected device")
                ok = startForegroundService(FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            }
            if (!ok) {
                Logger.w(LOG_TAG_VPN, "start service failed, stopping service")
                signalStopService("startFg1", userInitiated = false)
            }
            ok
        } else {
            val ok = startForegroundService()
            if (!ok) {
                Logger.w(LOG_TAG_VPN, "start service failed ( > U ), stopping service")
                signalStopService("startFg2", userInitiated = false)
            }
            ok
        }
        if (!startedForeground) {
            return START_STICKY
        }

        ui {
            // Initialize the value whenever the vpn is started.
            accessibilityHearbeatTimestamp = INIT_TIME_MS

            // this should always be set before ConnectionMonitor is init-d
            // see restartVpn and updateTun which expect this to be the case
            persistentState.setVpnEnabled(true)

            startOrbotAsyncIfNeeded()

            val isNewVpn = connectionMonitor.onVpnStart(this)

            if (isNewVpn) {
                // clear the underlying networks, so that the new vpn can be created with the
                // current active network.
                underlyingNetworks = null
                Logger.i(LOG_TAG_VPN, "new vpn")
            }

            val opts =
                appConfig.newTunnelOptions(
                    this,
                    getFakeDns(),
                    appConfig.getProtocolTranslationMode()
                )

            val isVpnEnabled = persistentState.getVpnEnabled()
            Logger.i(LOG_TAG_VPN, "start-fg with opts $opts (for new-vpn? $isNewVpn), isEnabled? $isVpnEnabled")
            if (!isNewVpn) {
                io("tunUpdate") {
                    // may call signalStopService(userInitiated=false) if go-vpn-adapter is missing
                    // which is the inverse of actually starting the vpn! But that's okay, since
                    // it indicates that something is out of whack (as in, connection monitor
                    // exists, vpn service exists, but the underlying adapter doesn't...
                    updateTun(opts)
                    eventLogger.logLow(EventType.VPN_START, "restart existing vpn from onStartCommand",
                        EventSource.VPN, userAction = false, details = "opts: $opts")
                }
            } else {
                io("startVpn") {
                    // refresh should happen before restartVpn, otherwise the new vpn will not
                    // have app, ip, domain rules. See RefreshDatabase#refresh
                    rdb.refresh(RefreshDatabase.ACTION_REFRESH_AUTO) {
                        restartVpn(this, opts, why = "startVpn")
                        // this should always happen after vpn enabled is set to true, as this will
                        // call restart-vpn and there is a check for vpn enabled state
                        observeVpnRestartRequests()
                        // call this *after* a new vpn is created #512
                        uiCtx("observers") { vpnPrefObservers.register() }
                        eventLogger.logLow(EventType.VPN_START, "start new vpn from onStartCommand",
                            EventSource.VPN, userAction = false, details = "opts: $opts")
                    }
                }
            }
        }
        return START_STICKY
    }

    suspend fun refreshRpnProxies() {
        Logger.v(LOG_TAG_VPN, "refreshRpnProxies: active=${RpnProxyManager.isRpnActive()}")
        val adapter = vpnAdapter
        if (!RpnProxyManager.isRpnActive()) {
            adapter?.unregisterWin()
            RpnProxyUpdateWorker.cancel(applicationContext)
            return
        }
        if (adapter == null) {
            Logger.i(LOG_TAG_VPN, "refreshRpnProxies: adapter unavailable")
            return
        }
        if (!adapter.isWinRegistered() && RpnProxyManager.registerProxy(RpnType.WIN)) {
            RpnProxyUpdateWorker.schedule(applicationContext)
        }
        RpnProxyManager.getEnabledConfigs().forEach { server ->
            val result = adapter.addNewWinServer(server.key)
            RpnProxyManager.notifyServerAddedToTun(server.key)
            if (!result.first) {
                Logger.w(LOG_TAG_VPN, "refreshRpnProxies: failed to add ${server.key}: ${result.second}")
            }
        }
        adapter.setRpnAutoMode()
    }

    @SuppressLint("ForegroundServiceType")
    @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startForegroundService(serviceType: Int): Boolean {
        Logger.vv(LOG_TAG_VPN, "startForegroundService, api: ${VERSION.SDK_INT}")
        try {
            ServiceCompat.startForeground(
                this,
                SERVICE_ID,
                updateNotificationBuilder(),
                serviceType
            )
            return true
        } catch (e: ForegroundServiceStartNotAllowedException) { // API 31 and above
            Logger.e(LOG_TAG_VPN, "startForeground failed, start not allowed exception", e)
        } catch (e: InvalidForegroundServiceTypeException) { // API 34 and above
            Logger.e(LOG_TAG_VPN, "startForeground failed, invalid service type exception", e)
        } catch (e: MissingForegroundServiceTypeException) { // API 34 and above
            Logger.e(LOG_TAG_VPN, "startForeground failed, missing service type exception", e)
        } catch (e: SecurityException) { // API 34 and above
            Logger.e(LOG_TAG_VPN, "startForeground failed, security exception", e)
        } catch (e: IllegalArgumentException) { // API 34 and above
            Logger.e(LOG_TAG_VPN, "startForeground failed, illegal argument", e)
        } catch (e: Exception) {
            Logger.e(LOG_TAG_VPN, "startForeground failed", e)
        }
        return false
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService(): Boolean {
        Logger.vv(LOG_TAG_VPN, "startForegroundService, api: ${VERSION.SDK_INT}")
        if (isAtleastS()) {
            try {
                startForeground(SERVICE_ID, updateNotificationBuilder())
                return true
            } catch (e: ForegroundServiceStartNotAllowedException) { // API 31 and above
                Logger.e(LOG_TAG_VPN, "startForeground failed, start not allowed exception", e)
            } catch (e: Exception) {
                Logger.e(LOG_TAG_VPN, "startForeground failed", e)
            }
        } else {
            try {
                startForeground(SERVICE_ID, updateNotificationBuilder())
                return true
            } catch (e: Exception) { // no exception expected for API < 31
                Logger.e(LOG_TAG_VPN, "startForeground failed", e)
            }
        }
        return false
    }

    private fun mtu(): Int {
        val overlayMtu = overlayNetworks.mtu
        val underlyingMtu = underlyingNetworks?.minMtu ?: VPN_INTERFACE_MTU
        val minMtu = min(overlayMtu, underlyingMtu)
        Logger.i(LOG_TAG_VPN, "mtu; proxy: $overlayMtu, underlying: $underlyingMtu, min: $minMtu")
        // min mtu should be at least MIN_MTU (1280)
        if (minMtu <= MIN_MTU) {
            Logger.w(LOG_TAG_VPN, "mtu less than or equal to $MIN_MTU, using $MIN_MTU")
            return MIN_MTU
        }
        return minMtu
    }

    private fun startOrbotAsyncIfNeeded() {
        if (!appConfig.isOrbotProxyEnabled()) return

        io("startOrbot") { orbotHelper.startOrbot(appConfig.getProxyType()) }
    }

    private fun registerAccessibilityServiceState() {
        val listener =
            AccessibilityManager.AccessibilityStateChangeListener { b ->
                if (!b) {
                    handleAccessibilityFailure()
                }
            }
        accessibilityListener = listener
        accessibilityManager.addAccessibilityStateChangeListener(listener)

        // Reset the heart beat time for the accessibility check.
        // On accessibility failure the value will be stored for next 5 mins.
        // If user, re-enable the settings reset the timestamp so that vpn service
        // will check for the accessibility service availability.
        accessibilityHearbeatTimestamp = INIT_TIME_MS
    }

    private fun unregisterAccessibilityServiceState() {
        accessibilityListener?.let {
            accessibilityManager.removeAccessibilityStateChangeListener(it)
        }
    }

    private suspend fun updateTun(tunnelOptions: AppConfig.TunnelOptions) {
        Logger.i(LOG_TAG_VPN, "update-tun with new pre-set tunnel options")
        if (!persistentState.getVpnEnabled()) {
            // when persistent-state "thinks" vpn is disabled, stop the service, especially when
            // we could be here via onStartCommand -> updateTun -> handleVpnAdapterChange while
            // conn-monitor and go-vpn-adapter exist, but persistent-state tracking vpn goes out
            // of sync
            Logger.e(LOG_TAG_VPN, "stop-vpn(updateTun), tracking vpn is out of sync")
            io("outOfSync") { signalStopService("outOfSync", userInitiated = false) }
            return
        }

        // should not be called in normal circumstances, but just in case...
        val ok = vpnAdapter?.updateTun(tunnelOptions) ?: false
        // TODO: like Intra, call VpnController#stop instead? see
        // VpnController#onStartComplete
        if (!ok) {
            Logger.w(LOG_TAG_VPN, "Cannot handle vpn adapter changes, no tunnel")
            io("noTunnel") { signalStopService("noTunnel", userInitiated = false) }
            return
        }
        notifyConnectionStateChangeIfNeeded()
    }

    fun closeConnectionsIfNeeded(uid: Int, reason: String) { // can be invalid uid, in which case, no-op
        if (uid == INVALID_UID) return

        if (uid == UID_EVERYBODY) {
            // when the uid is everybody, close all the connections
            io("closeConn") { vpnAdapter?.closeConnections(emptyList(), isUid = false, reason) }
            return
        }

        // close conns can now be called with a list of uids / connIds
        val uid0 = listOf(FirewallManager.appId(uid, isPrimaryUser()).toString())
        io("closeConn") { vpnAdapter?.closeConnections(uid0, isUid = true, reason) }
    }

    fun closeConnectionsByUidDomain(uid: Int, ipAddress: String?, reason: String) {
        // can be invalid uid, in which case, no-op
        // no need to close all connections in case of empty domain, as it is not valid
        if (uid == INVALID_UID || ipAddress.isNullOrEmpty()) return

        io("closeUidIp") {
            val to = System.currentTimeMillis() - VpnController.uptimeMs()
            // can be empty when the conns already closed before ui calls this
            val cids = connTrackRepository.getConnIdByUidIpAddress(uid, ipAddress, to)
            if (cids.isEmpty()) {
                Logger.w(LOG_TAG_VPN, "no connections found for uid: $uid, domain: $ipAddress")
                return@io
            }
            vpnAdapter?.closeConnections(cids, isUid = false, reason)
            Logger.i(LOG_TAG_VPN, "close connections by uid: $uid, domain: $ipAddress, cids: $cids, reason: $reason")
        }
    }

    // invoked on pref / probe-ip changes, so that the connection monitor can
    // re-initiate the connectivity checks
    fun notifyConnectionMonitor(enforcePolicyChange: Boolean = false) {
        if (enforcePolicyChange) {
            connectionMonitor.onPolicyChanged()
        } else {
            connectionMonitor.onUserPreferenceChanged()
        }
    }

    fun signalStopService(reason: String, userInitiated: Boolean = true) {
        if (!userInitiated) notifyUserOnVpnFailure()
        teardown.requestStop()
        eventLogger.logHigh(EventType.VPN_STOP, "vpn service destroyed",
                    EventSource.SERVICE, userAction = userInitiated, details = "vpn destroyed")
        Logger.i(LOG_TAG_VPN, "stopping vpn adapter & service: $reason, $userInitiated")
    }

    fun isStopping(): Boolean = teardown.isStopping

    suspend fun awaitStopped() = teardown.awaitCompletion()

    private suspend fun stopVpnAdapter() =
        withContext(CoroutineName("stopVpn") + serializer) {
            if (vpnAdapter == null) {
                Logger.i(LOG_TAG_VPN, "vpn adapter already stopped")
                return@withContext
            }

            vpnAdapter?.closeTun()
            vpnAdapter = null
            Logger.i(LOG_TAG_VPN, "stop vpn adapter")
        }

    private suspend fun restartVpnWithNewAppConfig(reason: String) {
        val ctx = this
        val bridge = this
        withContext(serializer) {
            logd("restart vpn with new app config")
            restartVpn(
                ctx,
                appConfig.newTunnelOptions(
                    bridge,
                    getFakeDns(),
                    appConfig.getProtocolTranslationMode()
                ),
                reason
            )
        }
    }


    private suspend fun restartVpn(
        ctx: Context,
        opts: AppConfig.TunnelOptions,
        why: String
    ) =
        withContext(CoroutineName(why) + serializer) {
            if (teardown.isStopping) return@withContext
            if (!persistentState.getVpnEnabled()) {
                // when persistent-state "thinks" vpn is disabled, stop the service, especially when
                // we could be here via onStartCommand -> isNewVpn -> restartVpn while both,
                // vpn-service & conn-monitor exist & vpn-enabled state goes out of sync
                io("outOfSyncRestart") {
                    logAndToastIfNeeded("$why, stop-vpn(restartVpn), tracking vpn is out of sync", Log.ERROR)
                    signalStopService("outOfSyncRestart", userInitiated = false)
                }
                return@withContext
            }
            try {
                Logger.i(
                    LOG_TAG_VPN,
                    "---------------------------RESTART-INIT----------------------------"
                )
                val nws = Networks(underlyingNetworks, overlayNetworks)
                val mtu = if (persistentState.useMaxMtu && !persistentState.routeRethinkInRethink) {
                    MAX_MTU
                } else {
                    mtu()
                }
                val nwMtu = mtu()
                // Lockdown prevents traffic leaking while the old TUN is detached. Detach before
                // establish replaces the OS interface, for either adapter restart policy.
                if (isLockdown()) vpnAdapter?.unlink()
                // attempt seamless hand-off as described in VpnService.Builder.establish() docs
                val tunFd = establishVpn(nws, mtu)
                if (tunFd == null) {
                    io("noTunRestart1") {
                        Logger.i(LOG_TAG_VPN, "-------------------------RESTART-ERR1----------------------")
                        logAndToastIfNeeded("$why, cannot restart-vpn, no tun-fd", Log.ERROR)
                        signalStopService("noTunRestart1", userInitiated = false)
                    }
                    return@withContext
                }

                testFd.set(tunFd.fd) // save the fd for testing purposes

                val ok =
                    makeOrUpdateVpnAdapter(
                        ctx,
                        tunFd,
                        mtu,
                        nwMtu,
                        opts,
                        builderRoutes
                    ) // builderRoutes set in establishVpn()
                if (!ok) {
                    io("noTunnelRestart2") {
                        Logger.i(LOG_TAG_VPN, "----------------------RESTART-ERR2----------------------")
                        logAndToastIfNeeded("$why, cannot restart-vpn, no vpn-adapter", Log.ERROR)
                        signalStopService("noTunRestart2", userInitiated = false)
                    }
                    return@withContext
                } else {
                    io("restarted") { logAndToastIfNeeded("$why, vpn restarted", Log.INFO) }
                }
                Logger.i(
                    LOG_TAG_VPN,
                    "---------------------------RESTART-OK----------------------------"
                )

                notifyConnectionStateChangeIfNeeded()
                informVpnControllerForProtoChange(builderRoutes)
            } catch (e: Exception) {
                Logger.i(LOG_TAG_VPN, "----------------------RESTART-ERR0----------------------")
                Logger.e(LOG_TAG_VPN, "restart-vpn failed: ${e.message}", e)
                io("restartVpnError") {
                    logAndToastIfNeeded("$why, restart-vpn failed: ${e.message}", Log.ERROR)
                    signalStopService("restartVpnError", userInitiated = false)
                }
            }
        }

    private suspend fun logAndToastIfNeeded(msg: String, logLevel: Int = Log.WARN) {
        when (logLevel) {
            Log.WARN -> Logger.w(LOG_TAG_VPN, msg)
            Log.ERROR -> Logger.e(LOG_TAG_VPN, msg)
            Log.INFO -> Logger.i(LOG_TAG_VPN, msg)
            else -> Logger.d(LOG_TAG_VPN, msg)
        }
        // Avoid transient UI noise for internal restart diagnostics.
        // These messages remain in logs for debugging.
    }

    private fun notifyConnectionStateChangeIfNeeded() {
        // Case: Set state to working in case of Firewall mode
        if (appConfig.getBraveMode().isFirewallMode()) {
            VpnController.onConnectionStateChanged(State.WORKING)
        }
    }

    private fun informVpnControllerForProtoChange(protos: Pair<Boolean, Boolean>) {
        // update the controller, which will update the UI (home screen btm sheet)
        VpnController.updateProtocol(protos)
    }

    fun hasTunnel(): Boolean {
        return vpnAdapter?.hasTunnel() == true
    }

    suspend fun refreshResolvers() {
        vpnDnsTransportController.refreshResolvers()
    }

    private suspend fun makeOrUpdateVpnAdapter(
        ctx: Context,
        tunFd: ParcelFileDescriptor,
        mtu: Int,
        nwMtu: Int,
        opts: AppConfig.TunnelOptions,
        p: Pair<Boolean, Boolean>
    ): Boolean =
        withContext(CoroutineName("makeVpn") + serializer) {
            val restartPolicy = VpnBuilderPolicy.fromOrdinalOrDefault(persistentState.vpnBuilderPolicy).vpnAdapterBehaviour
            val lockdown = underlyingNetworks?.vpnLockdown ?: isLockdown()
            val ok = true
            val noTun = false // should eventually call signalStopService(userInitiated=false)
            val protos = InternetProtocol.byProtos(p.first, p.second).value()
            try {
                val fd = if (FIRESTACK_MUST_DUP_TUNFD) {
                    tunFd.fd.toLong()
                } else {
                    tunFd.detachFd().toLong()
                }

                if (vpnAdapter == null) {
                    // create a new vpn adapter
                    val ifaceAddresses = getAddresses()
                    Logger.i(LOG_TAG_VPN, "vpn-adapter doesn't exists, create one, fd: $fd, lockdown: $lockdown, protos: $protos, ifaddr: $ifaceAddresses, opts: $opts, mtu: $mtu, nwMtu: $nwMtu")
                    (application as? com.bernaferrari.bravedns.RethinkDnsApplication)?.initializeNativeReporting()
                    vpnAdapter = GoVpnAdapter(
                        ctx,
                        vpnScope,
                        fd,
                        ifaceAddresses,
                        mtu,
                        nwMtu,
                        opts,
                        persistentState,
                        appConfig,
                        connTrackRepository,
                        eventLogger,
                    ) // may throw
                    GoVpnAdapter.setLogLevel(
                        persistentState.goLoggerLevel.toInt(),
                        includeFileTrace = persistentState.includeFileTrace
                    )
                    vpnAdapter?.initResolverProxiesPcap(opts)
                    pcapSession.refresh()
                    return@withContext ok
                } else {
                    Logger.i(LOG_TAG_VPN, "vpn-adapter exists, fd: $fd, policy: ${restartPolicy.name}, lockdown: $lockdown, protos: $protos, mtu: $mtu, nwMtu: $nwMtu")
                    when (restartPolicy) {
                        VpnBuilderPolicy.GoVpnAdapterBehaviour.NEVER_RESTART -> {
                            // in case, if vpn-adapter exists, update the existing vpn-adapter
                            if (vpnAdapter?.updateLinkAndRoutes(fd, mtu, nwMtu,protos) == false) {
                                Logger.e(LOG_TAG_VPN, "err update vpn-adapter")
                                return@withContext noTun
                            }
                        }
                        VpnBuilderPolicy.GoVpnAdapterBehaviour.PREFER_RESTART -> {
                            // if vpn-adapter exists, recreate vpn-adapter only on lockdown mode
                            if (lockdown) {
                                if (vpnAdapter?.restartTunnel(fd, mtu, nwMtu, protos) == false) {
                                    Logger.e(LOG_TAG_VPN, "err recreate vpn-adapter")
                                    return@withContext noTun
                                }
                                vpnAdapter?.setLinkMtu(nwMtu)
                            } else {
                                if (vpnAdapter?.updateLinkAndRoutes(fd, mtu, nwMtu,protos) == false) {
                                    Logger.e(LOG_TAG_VPN, "err update vpn-adapter")
                                    return@withContext noTun
                                }
                            }
                        }
                    }

                    return@withContext ok
                }
            } catch (e: Exception) {
                Logger.e(LOG_TAG_VPN, "err new vpn-adapter: ${e.message}", e)
                return@withContext noTun
            } finally {
                try { // close the tunFd as GoVpnAdapter has its own copy
                    if (FIRESTACK_MUST_DUP_TUNFD) tunFd.close()
                } catch (e: IOException) {
                    Logger.e(LOG_TAG_VPN, "err closing tunFd: ${e.message}", e)
                }
            }
        }

    // TODO: #294 - Figure out a way to show users that the device is offline instead of status as
    // failing.
    suspend fun onNetworkDisconnected(networks: ConnectionMonitor.UnderlyingNetworks, forceRestart: Boolean = false) {
        underlyingNetworks = networks
        underlyingNetworks?.vpnLockdown = isLockdown()

        val underlyingNws = getUnderlays()
        setUnderlyingNetworks(underlyingNws)
        tunUnderlyingNetworks = underlyingNws?.joinToString()

        Logger.i(LOG_TAG_VPN, "$TAG; nw disconnect, restart vpn? $forceRestart")

        // if there is no changes, then already a disconnection restart happened, no need to
        // restart again, this will avoid unnecessary restarts
        // some cases, onLost is called multiple times, so avoid restarting
        if (!forceRestart) {
            // if forceRestart is false, then we are not restarting the vpn, so no need to set
            // network and default dns, as it is already set to empty before
            return
        }

        val reason = "nwDisconnect"
        vpnRestartTrigger.value = reason
        eventLogger.logMedium(EventType.SYSTEM_EVENT, "network disconnected, restart vpn",
            EventSource.SERVICE, userAction = false, details = "networks: ${networks.ipv4Net.size + networks.ipv6Net.size}")
        // pause mobile-only wgs on no network
        pauseMobileOnlyWireGuardOnNoNw()
        pauseSsidEnabledWireGuardOnNoNw()
        vpnDnsTransportController.setNetworkAndDefaultDnsIfNeeded(true)
        VpnController.onConnectionStateChanged(null)
    }

    override suspend fun onNetworkChange(networks: ConnectionMonitor.UnderlyingNetworks) {
        withContext(serializer) {
            val prev = underlyingNetworks
            // no need to handle nw change when last updated time is stale
            if ((prev?.lastUpdated ?: 0L) > networks.lastUpdated) {
                Logger.w(LOG_TAG_VPN, "onNetworkChange: ignoring stale network change, prev: ${prev?.lastUpdated}, new: ${networks.lastUpdated}")
                return@withContext
            }

            val prevSize = (prev?.ipv4Net?.size ?: 0) + (prev?.ipv6Net?.size ?: 0)
            val currSize = networks.ipv4Net.size + networks.ipv6Net.size
            // force restart if no networks before or after
            val forceRestart  = (prevSize == 0 && currSize > 0) || (prevSize > 0 && currSize == 0)
            if (currSize > 0) {
                onNetworkConnected(networks, forceRestart)
            } else {
                onNetworkDisconnected(networks, forceRestart)
            }
        }
    }

    override suspend fun onNetworkRegistrationFailed() = withContext(serializer) {
        Logger.i(LOG_TAG_VPN, "recd nw reg failed, stop vpn service with notification")
        signalStopService("nwRegFail", userInitiated = false)
    }

    override suspend fun maybeNetworkStall() {
        // these calls are not fool proof, just a mitigation mechanism
        // see if there is no flow call for 30 seconds and this is called, then restart the vpn
        val elapsed = elapsedRealtime()
        if (elapsed >= lastRxTrafficTime + DATA_STALL_THRESHOLD_MS) {
            Logger.w(LOG_TAG_VPN, "diags; no flow call for 30 seconds, restarting vpn, last: $lastRxTrafficTime")
            val reason = "diags ${elapsed/(10*1000L)}" // restart once in a given 10 sec interval
            vpnRestartTrigger.value = reason
        } else {
            Logger.d(LOG_TAG_VPN, "diags; flow call recd, no restart needed, last: $lastRxTrafficTime")
        }
    }

    private fun getUnderlays(): Array<Network>? {
        return vpnTunRoutes.getUnderlays(underlyingNetworks, isLockdown(), ::netid)
    }

    suspend fun onNetworkConnected(networks: ConnectionMonitor.UnderlyingNetworks, forceRestart: Boolean = false) {
        val curnet = underlyingNetworks
        val out = interestingNetworkChanges(curnet, networks)
        val isRoutesChanged = hasRouteChangedInAutoMode(out)
        val isBoundNetworksChanged = out.netChanged
        val isMtuChanged = out.mtuChanged
        val isSsidChanged = out.ssidChanged
        underlyingNetworks = networks
        underlyingNetworks?.vpnLockdown = isLockdown()

        // always reset the system dns server ip of the active network with the tunnel
        vpnDnsTransportController.setNetworkAndDefaultDnsIfNeeded(isRoutesChanged || isBoundNetworksChanged)

        val underlyingNws = getUnderlays()
        setUnderlyingNetworks(underlyingNws)
        tunUnderlyingNetworks = underlyingNws?.joinToString()
        var ipv4Ssid = ""
        var ipv6Ssid = ""
        networks.ipv4Net.forEach {
            ipv4Ssid = ipv4Ssid + it.network.networkHandle.toString() + "##" + (it.ssid ?: "")
        }
        networks.ipv6Net.forEach {
            ipv6Ssid = ipv6Ssid + it.network.networkHandle.toString() + "##" + (it.ssid ?: "")
        }

        logd("getNetworkSSID - onNetworkConnected: active: ${networks.activeSsid}, v4: $ipv4Ssid, v6: $ipv6Ssid")
        logd(
            "underlays: ${underlyingNws?.joinToString()}, forceRestart? $forceRestart mtu? $isMtuChanged(o:${curnet?.minMtu}, n:${networks.minMtu}), tun: ${tunMtu()}; routes? $isRoutesChanged, bound-nws? $isBoundNetworksChanged, stall? ${persistentState.stallOnNoNetwork}, updatedTs: ${networks.lastUpdated}"
        )

        // restart vpn if the routes or when mtu changes
        if (isMtuChanged || isRoutesChanged || forceRestart) {
            Logger.i(LOG_TAG_VPN, "$TAG; mtu/routes/force-restart,  restart vpn")
            ioCtx("nwConnect") {
                var reason = "mtu: ${curnet?.minMtu}/${networks.minMtu}, "
                reason += "r: $isRoutesChanged, "
                reason += "nws: ${curnet?.ipv4Net?.size}/${curnet?.ipv6Net?.size} > new: ${networks.ipv4Net.size}/${networks.ipv6Net.size} ($isBoundNetworksChanged), "
                reason += "force: $forceRestart, lock: ${curnet?.vpnLockdown}/${networks.vpnLockdown}, "
                reason += "nwConnect, $reason"
                vpnRestartTrigger.value = reason
            }
        } else {
            // update the network mtu even though no restart is needed
            // can set multiple times, no issues
            vpnAdapter?.setLinkMtu(mtu())
        }

        // now the proxy need to be either paused/resumed/refreshed/readded
        // so no need to check for isRoutesChanged, even though the routes are same,
        // the bound networks have changed, so either of the above operations are needed
        // case: wireguard in mobile-only mode & ssid change in wifi for ssidEnabled wgs
        if (isBoundNetworksChanged || isSsidChanged) {
            // Workaround for WireGuard connection issues after network change
            // WireGuard may fail to connect to the server when the network changes.
            Logger.i(LOG_TAG_VPN, "$TAG ssid/bound-nws changed, refresh wg if needed")
            refreshOrPauseOrResumeOrReAddProxies() // takes care of adding the proxies if missing in tun
            eventLogger.logLow(EventType.PROXY_REFRESH, "refresh/pause/resume/readd proxies",
                EventSource.SERVICE, userAction = false, "nwChange, $isBoundNetworksChanged, $isSsidChanged, ssid: ${networks.activeSsid}, refresh/pause/resume/readd proxies")
        }

        underlyingNetworks?.ipv4Net?.forEach {
            it.linkProperties?.linkAddresses?.forEach { ips ->
                Logger.i(
                    LOG_TAG_VPN,
                    "IPv4 link Address: ${ips.address.hostAddress}, prefix: ${ips.prefixLength}, flags: ${ips.flags}, scope: ${ips.scope}, all: $ips"
                )
            }
        }

        underlyingNetworks?.ipv6Net?.forEach {
            it.linkProperties?.linkAddresses?.forEach { ips ->
                Logger.i(
                    LOG_TAG_VPN,
                    "IPv6 link Address: ${ips.address.hostAddress}, prefix: ${ips.prefixLength}, flags: ${ips.flags}, scope: ${ips.scope}, all: $ips"
                )
            }
        }
    }

    fun tunMtu(): Int {
        return vpnAdapter?.tunMtu() ?: 0
    }

    private fun hasRouteChangedInAutoMode(out: NetworkChanges): Boolean {
        // no need to check for routes if the app is not set in auto mode
        if (!appConfig.getInternetProtocol().isIPv46()) {
            return false
        }
        return out.routesChanged
    }

    data class NetworkChanges(
        val routesChanged: Boolean = true,
        val netChanged: Boolean = true,
        val mtuChanged: Boolean = true,
        val ssidChanged: Boolean = true
    )

    // v4/v6 routes of the established tunnel; set by establishVpn via VpnTunRoutes.
    private var builderRoutes: Pair<Boolean, Boolean> = Pair(false, false)

    private suspend fun onOverlayNetworkChanged(nw: OverlayNetworks) =
        withContext(CoroutineName("ovch") + serializer) {
            if (teardown.isStopping || (nw.proxyGeneration >= 0 &&
                vpnAdapter?.isProxyProbeGenerationCurrent(nw.proxyGeneration) != true)) return@withContext
            val interestingNet = interestingNetworkChanges(aux = nw)
            val isRoutesChanged = interestingNet.routesChanged
            val isMtuChanged = interestingNet.mtuChanged
            Logger.i(
                LOG_TAG_VPN,
                "overlay: routes changed? $isRoutesChanged, mtu changed? $isMtuChanged"
            )
            overlayNetworks = nw
            if (isRoutesChanged || isMtuChanged) {
                Logger.i(LOG_TAG_VPN, "overlay changed $overlayNetworks, restart vpn")
                // Both overlay and underlay may share routes; the routeChange here tracks
                // only overlay changes, so the VPN restarts to re-evaluate route decisions.
                val reason = "overlayNwChanged, routes: $isRoutesChanged, mtu: $isMtuChanged, at: ${elapsedRealtime()}"
                vpnRestartTrigger.value = reason
            } else {
                Logger.i(LOG_TAG_VPN, "overlay routes or mtu not changed, no restart needed")
            }
        }

    private fun interestingNetworkChanges(
        old: ConnectionMonitor.UnderlyingNetworks? = underlyingNetworks,
        _new: ConnectionMonitor.UnderlyingNetworks? = null,
        aux: OverlayNetworks = overlayNetworks
    ): NetworkChanges {
        return vpnTunRoutes.interestingNetworkChanges(
            old,
            _new,
            aux,
            overlayNetworks.mtu,
            tunMtu(),
            builderRoutes,
            RECONCILE_WITH_VPN_ROUTES,
            ::logd,
        )
    }

    private fun handleVpnLockdownStateAsync() {
        if (!syncLockdownState()) return

        Logger.i(LOG_TAG_VPN, "vpn lockdown mode change, restarting")
        io("lockdownSync") {
            val reason = "lockdown: ${VpnController.isVpnLockdown()}"
            vpnRestartTrigger.value = reason
            vpnAdapter?.notifyLoopback()
        }
    }

    private fun syncLockdownState(): Boolean {
        if (!isAtleastQ()) return false

        val curr = isLockdownEnabled
        // cannot set the lockdown status while the vpn is being created, it will return false
        // until the vpn is created. so the sync will be done after the vpn is created
        // when the first flow call is made.
        val prev = isLockDownPrevious.get()

        if (curr == prev) {
            underlyingNetworks?.vpnLockdown = prev
            return false
        }

        val set = isLockDownPrevious.compareAndSet(prev, curr)
        if (set) {
            underlyingNetworks?.vpnLockdown = curr
        }
        return set
    }

    private fun notifyUserOnVpnFailure() {
        ui { vpnNotificationManager.showVpnFailureNotification() }
    }

    override fun onDestroy() {
        dnsAnswerReasons.clear()
        overlayNetworkRefresher?.close()
        overlayNetworkRefresher = null
        bubbleController.dismissIfEnabled()

        try {
            unregisterAccessibilityServiceState()
            orbotHelper.unregisterReceiver()
            unregisterUserPresentReceiver()
        } catch (e: IllegalArgumentException) {
            Logger.w(LOG_TAG_VPN, "Unregister receiver error: ${e.message}")
        }
        persistentState.setVpnEnabled(false)
        PauseTimer.stop()
        // reset the underlying networks
        underlyingNetworks = null

        // Cleanup modular managers
        cleanupModularManagers()

        vpnPrefObservers.unregister()

        // onVpnStart is also called from the main thread (ui).
        // Must unregister callbacks before vpnScope.cancel — ConnectionMonitor shares vpnScope.
        connectionMonitor.onVpnStop()
        VpnController.onVpnDestroyed()
        // Callbacks remain available until the native tunnel has disconnected and logs are drained.
        teardown.onDestroyed()

        Logger.w(LOG_TAG_VPN, "Destroying VPN service")

        // stop foreground service will take care of stopping the service for both
        // version >= 24 and < 24
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
    }

    private fun cleanupModularManagers() {
        dnsConfigurationManager.cleanup()
        underlyingNetworkManager.cleanup()
        Logger.i(LOG_TAG_VPN, "Modular managers cleaned up")
    }

    fun increasePauseDuration(durationMs: Long) {
        PauseTimer.addDuration(durationMs)
    }

    fun decreasePauseDuration(durationMs: Long) {
        PauseTimer.subtractDuration(durationMs)
    }

    fun pauseCountDownFlow(): StateFlow<Long> {
        return PauseTimer.pauseCountDownFlow()
    }

    private fun isAppPaused(): Boolean {
        return VpnController.isAppPaused()
    }

    fun pauseApp() {
        PauseTimer.start(PauseTimer.DEFAULT_PAUSE_TIME_MS)
        handleVpnServiceOnAppStateChange()
        Logger.i(LOG_TAG_VPN, "App paused")
    }

    fun resumeApp() {
        PauseTimer.stop()
        handleVpnServiceOnAppStateChange()
        Logger.i(LOG_TAG_VPN, "App resumed")
    }

    private fun handleVpnServiceOnAppStateChange() { // paused or resumed
        val reason = if (isAppPaused()) "pause" else "resume"
        vpnRestartTrigger.value = reason
        ui { vpnNotificationManager.notifyForeground() }
    }

    private fun determineRoutes(n: Networks): Pair<Boolean, Boolean> {
        return vpnTunRoutes.determineRoutes(n)
    }

    private suspend fun establishVpn(networks: Networks, mtu: Int): ParcelFileDescriptor? {
        val pendingIntent =
            Utilities.getActivityPendingIntent(
                this,
                Intent(this, HomeScreenActivity::class.java).setAction("$packageName.vpn.configure"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                mutable = false
            )

        var builder: Builder = newBuilder().setSession(getString(R.string.app_name)).setMtu(mtu)
        builder.setConfigureIntent(pendingIntent)
        val result = vpnTunRoutes.establish(builder, networks, mtu)
        builderRoutes = result.routes
        builderStats = result.stats
        return result.fd
    }

    private fun getFakeDns(): String = vpnTunRoutes.fakeDns()

    private fun getAddresses(): String = vpnTunRoutes.addresses()


    private fun <T> go2kt(co: CoFactory<T>, f: suspend() -> T): T = runBlocking {
        // runBlocking blocks the current thread until all coroutines within it are complete
        // an call a suspending function from a non-suspending context and obtain the result.
        return@runBlocking co.tryDispatch(f)
    }

    private suspend fun ioCtx(s: String, f: suspend () -> Unit) =
        withContext(CoroutineName(s) + Dispatchers.IO) { f() }


    private fun io(s: String, f: suspend () -> Unit) =
        vpnScope.launch(CoroutineName(s) + Dispatchers.IO) { f() }

    private fun ui(f: suspend () -> Unit) = vpnScope.launch(Dispatchers.Main) { f() }

    private suspend fun uiCtx(s: String, f: suspend () -> Unit) =
        withContext(CoroutineName(s) + Dispatchers.Main) { f() }

    private fun <T> ioAsync(s: String, f: suspend () -> T): Deferred<T> {
        return vpnScope.async(CoroutineName(s) + Dispatchers.IO) { f() }
    }

    override fun onPrequery(origin: String?, uid: String?, fqdn: String, qtype: Long): com.celzero.firestack.backend.DomainOpts =
        com.celzero.firestack.backend.DomainOpts()

    override fun onQuery(origin: String?, uidGostr: String?, fqdn: String, qtype: Long): DNSOpts = go2kt(dnsQueryDispatcher) {
        vpnDnsQueryRouter.resolve(uidGostr, fqdn, qtype)
    }


    private fun useCaptivePortalDns(): Boolean {
        if (!persistentState.useSystemDnsOnCaptivePortal || !::cm.isInitialized) return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }

    private fun getUnderlyingSsid(): String? {
        return underlyingNetworks?.activeSsid ?: underlyingNetworks?.ipv4Net?.firstOrNull { it.ssid != null }?.ssid ?: underlyingNetworks?.ipv6Net?.firstOrNull { it.ssid != null }?.ssid
    }

    override fun onResponse(summary: DNSSummary?) {
        if (summary == null) {
            Logger.i(LOG_TAG_VPN, "received null summary for dns")
            return
        }
        logd("onResponse: $summary")
        if (!DEBUG) {
            // not expected to have fixed id in the summary on production builds
            if (summary.id.contains(Backend.Fixed)) {
                return
            }
        }
        dnsAnswerReasons.find(dnsQuestionKey(summary))?.let {
            val marker = "firewall=$it"
            if (!summary.msg.orEmpty().contains(marker)) summary.msg = "${summary.msg.orEmpty()}; $marker"
        }
        netLogTracker.processDnsLog(summary)
        setRegionIfRequired(summary)
    }

    private fun setRegionIfRequired(summary: DNSSummary) {
        if (summary.region == null) {
            return
        }

        val region = summary.region
        if (regionFlow.value != region) {
            regionFlow.value = region
        }
    }

    fun getRegionFlow(): StateFlow<String> {
        return regionFlow
    }

    override fun onProxiesStopped() {
        // clear the proxy handshake times
        logd("onProxiesStopped; clear the handshake times")
    }

    override fun onProxyAdded(id: String?, handle: String): Unit = go2kt(proxyAddedDispatcher) {
        val iid = id
        if (iid == null) {
            Logger.e(LOG_TAG_VPN, "onProxyAdded: received null id")
            return@go2kt
        }

        if (!iid.startsWith(ID_WG_BASE, true) && !iid.startsWith(Backend.RPN, true)) {
            // only wireguard proxies are considered for overlay network
            logd("onProxyAdded: no-op as it is not wireguard proxy, added $iid")
            return@go2kt
        }

        // Cache before exposing fallback candidates to flow; unknown status fails closed.
        WireguardManager.refreshSplitProxyInfo(iid)
        // new proxy added, refresh overlay network pair
        vpnAdapter?.invalidateProxyProbes()
        overlayNetworkRefresher?.request()
        val id = iid.substringAfter(ID_WG_BASE).toIntOrNull() ?: return@go2kt
        val files = WireguardManager.getConfigFilesById(id) ?: return@go2kt

        if (!files.useOnlyOnMetered || files.oneWireGuard) return@go2kt
        withContext(CoroutineName("onProxyAdded") + serializer) {
            val newNet = underlyingNetworks
            val v4first = newNet?.ipv4Net?.firstOrNull()
            val v6first = newNet?.ipv6Net?.firstOrNull()
            val v4Mobile = v4first?.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            val v6Mobile = v6first?.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            val isActiveMobile = v4Mobile || (v4first == null && v6Mobile)
            Logger.i(LOG_TAG_VPN, "onProxyAdded: wg proxy $iid is added, isActiveMobile: $isActiveMobile")
        }
    }

    override fun onProxyRemoved(id: String?, handle: String) {
        val iid = id
        if (iid == null) {
            Logger.e(LOG_TAG_VPN, "onProxyAdded: received null id")
            return
        }

        if (!iid.startsWith(ID_WG_BASE) && !iid.startsWith(Backend.RPN)) {
            // only wireguard proxies are considered for overlay network
            logd("onProxyRemoved: proxy removed $iid, not wireguard")
            return
        }
        // proxy removed, refresh overlay network pair
        vpnAdapter?.invalidateProxyProbes()
        overlayNetworkRefresher?.request()
    }

    override fun onProxyStopped(id: String?, handle: String) {
        // no-op
        Logger.v(LOG_TAG_VPN, "onProxyStopped: $id")
    }

    override fun onProxyUpdated(id: String, handle: String) {
        Logger.v(LOG_TAG_VPN, "onProxyUpdated: $id, handle: $handle")
        onProxyAdded(id, handle)
    }

    override fun onDNSAdded(id: String?) {
        // no-op
        Logger.v(LOG_TAG_VPN, "onDNSAdded: $id")
    }

    override fun onDNSRemoved(id: String?) {
        // no-op
        Logger.v(LOG_TAG_VPN, "onDNSRemoved: $id")
    }

    override fun onDNSStopped() {
        // no-op
        Logger.v(LOG_TAG_VPN, "onDNSStopped")
    }

    override fun onSvcComplete(p0: ServerSummary) {
        // no-op
    }

    override fun onUpstreamAnswer(
        id: String,
        smm: DNSSummary,
        rcvdDnsOpts: DNSOpts,
        ipcsv: String
    ): DNSOpts = go2kt(dnsQueryDispatcher) {
        val rawUid = smm.uid.toIntOrNull() ?: return@go2kt DNSOpts()
        val uid = FirewallManager.appId(rawUid, isPrimaryUser())
        val context = DnsAnswerContext(
            addresses = ipcsv,
            captivePortal = useCaptivePortalDns(),
            dnsOnly = appConfig.getBraveMode().isDnsMode(),
            fixedTransport = rcvdDnsOpts.tidcsv.contains(Backend.Fixed) || rcvdDnsOpts.tidseccsv.contains(Backend.Fixed),
            portSpecificRules = IpRulesManager.hasPortSpecificRules(uid),
            isolated = FirewallManager.appStatus(uid).isolate(),
        )
        val decision = DnsAnswerPolicy.decide(context) { addresses ->
            val snapshot = buildProxyRoutingSnapshot(uid)
            val metadata = createConnTrackerMetaData(
                uid, FirewallManager.userId(uid), "", 0, addresses.joinToString(","), 0, 0,
                "", smm.blocklists.orEmpty(), smm.qName, "",
                if (isConnectionMetered(addresses.first())) ConnectionTracker.ConnType.METERED else ConnectionTracker.ConnType.UNMETERED,
            )
            firewall(metadata, smm.qName, false, isSpecialApp(snapshot), persistentState.routeRethinkInRethink,
                isAlg = true, forDnsAnswer = true)
        }
        when (decision) {
            DnsAnswerPolicy.Decision.Keep -> DNSOpts()
            is DnsAnswerPolicy.Decision.Block -> {
                dnsAnswerReasons.record(dnsQuestionKey(smm), decision.rule.id)
                DNSOpts().apply { tidcsv = Backend.BlockAll; noblock = false }
            }
            is DnsAnswerPolicy.Decision.Bypass -> {
                dnsAnswerReasons.record(dnsQuestionKey(smm), decision.rule.id)
                vpnDnsQueryRouter.bypassAnswer(uid, smm.qName.orEmpty(), rcvdDnsOpts)
            }
        }
    }

    override fun svcRoute(
        sid: String,
        pid: String,
        network: String,
        sipport: String,
        dipport: String
    ): Tab {
        // no-op
        return Tab()
    }

    // should never run in seperate go-routine as msg is a bytearray in go and will be
    // released immediately
    private fun showNwEngineNotification(msg: String) {
        vpnNotificationManager.showNwEngineNotification(msg)
    }

    // no need of go2kt here as it is called from go and just performs db operations
    // requires go2kt if there any calls to go functions
    override fun postflow(s: FlowSummary?) {
        if (s == null) {
            Logger.i(LOG_TAG_VPN, "received null summary for socket")
            return
        }

        if (s.id.isEmpty()) { // this should not happen, but just in case
            // in case of empty connId, insert a new entry
            val cid = "debug-" + Utilities.getRandomString(8)
            val uid = FirewallManager.appId(s.uid.toInt(), isPrimaryUser())
            val cm = createConnTrackerMetaData(
                    uid,
                    uid,
                    s.target,
                    0,
                    s.target,
                    0,
                    0,
                    proxyDetails = s.pid,
                    "",
                    s.target,
                    cid,
                    ConnectionTracker.ConnType.UNMETERED,
                )
            netLogTracker.writeIpLog(cm)
            return
        }

        val durationSec = (s.duration / 1000).toInt()
        val isNotLocalProxy = isNotLocalAndRpnProxy(s.pid)

        val cm = getConnTrackerMetaData(s.id)
        if (cm != null) { // if the connection metadata is already tracked, insert with summary
            var proxyRule = ""
            if (s.pid.isNotEmpty() && isNotLocalProxy) {
                proxyRule = FirewallRuleset.RULE12.id
            }

            val isRethink = cm.uid == rethinkUid
            cm.proxyDetails = s.pid
            cm.rpid = s.rpid
            cm.downloadBytes = s.rx
            cm.uploadBytes = s.tx
            cm.duration = durationSec
            cm.synack = s.rtt
            cm.message = s.msg
            cm.destIP = s.target
            cm.isBlocked = if (proxyRule.isEmpty()) true else cm.isBlocked
            cm.blockedByRule = proxyRule.ifEmpty { FirewallRuleset.RULE18.id }
            logd("onSocketClosed-flow/postflow: $s, pid: ${s.pid.isNullOrEmpty()}, cm: $cm")
            if (isRethink) {
                netLogTracker.writeRethinkLog(cm)
            } else {
                netLogTracker.writeIpLog(cm)
            }
            return // no need to proceed further as no need to update the summary
        }

        // set the flag as null, will calculate the flag based on the target
        val connectionSummary =
            ConnectionSummary(
                s.uid,
                s.pid,
                s.rpid,
                s.id,
                s.rx,
                s.tx,
                durationSec,
                s.rtt, // updated in synack var
                s.msg,
                s.target,
                null
            )
        logd("onSocketClosed: $s")

        if (s.uid.isNullOrEmpty()) {
            Logger.e(LOG_TAG_VPN, "onSocketClosed: missing uid, summary: $s")
            return
        }

        // note the last time when there is a connection update with download traffic
        // useful to detect data stalls
        if (s.rx  > 0) {
            lastRxTrafficTime = elapsedRealtime()
        }

        try {
            val key: CidKey
            if (s.uid == rethinkUid.toString()) {
                // update rethink summary
                key = CidKey(connectionSummary.connId, rethinkUid)
                netLogTracker.updateRethinkSummary(connectionSummary)
            } else {
                // other apps summary
                // convert the uid to app id
                val uid = FirewallManager.appId(s.uid.toInt(), isPrimaryUser())
                key = CidKey(connectionSummary.connId, uid)
                netLogTracker.updateIpSummary(connectionSummary)
            }
            synchronized(activeCids) {
                activeCids.remove(key)
            }
            synchronized(activeClosableCids) {
                activeClosableCids.remove(connectionSummary.connId)
            }
            io("dlIpInfo") {
                IpInfoDownloader.fetchIpInfoIfRequired(s.target)
            }
        } catch (e: NumberFormatException) {
            Logger.e(LOG_TAG_VPN, "onSocketClosed: ${e.message}", e)
        }
    }

    private fun getConnTrackerMetaData(cid: String): ConnTrackerMetaData? {
        val cm = trackedConnMetaData.getIfPresent(cid)
        trackedConnMetaData.invalidate(cid)
        return cm
    }

    fun parseIpAndPort(endpoint: String?): Pair<String, Int> {
        if (endpoint.isNullOrBlank()) return "" to 0

        val trimmed = endpoint.trim()

        // handle bracketed IPv6: [2001:db8::1]:443
        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            val endBracket = trimmed.indexOf(']')
            val ipPart = trimmed.substring(1, endBracket)
            val portPart = trimmed.substring(endBracket + 1).removePrefix(":")
            val port = portPart.toIntOrNull() ?: 0
            return ipPart to port
        }

        // handle unbracketed IPv6 with port, e.g. 2001:db8::1:443
        // we assume the last colon separates port, but IPv6 can contain many colons.
        // so, only treat the last part as port if it's numeric.
        val lastColonIndex = trimmed.lastIndexOf(':')
        if (lastColonIndex > 0) {
            val potentialPort = trimmed.substring(lastColonIndex + 1)
            if (potentialPort.toIntOrNull() != null) {
                val ipPart = trimmed.substring(0, lastColonIndex)
                // avoid cutting an IPv6 without port (like 2001:db8::1)
                val colonCount = ipPart.count { it == ':' }
                val ip = if (colonCount >= 2) ipPart else trimmed
                val port = if (colonCount >= 2) potentialPort.toIntOrNull() ?: 0 else 0
                if (colonCount >= 2) return ip to port
            }
        }

        // handle IPv4 or hostname with port, e.g. 10.0.0.1:53
        val parts = trimmed.split(":")
        if (parts.size == 2 && parts[1].toIntOrNull() != null) {
            return parts[0] to (parts[1].toIntOrNull() ?: 0)
        }

        // use hostName parser for complex cases
        return try {
            val host = HostName(trimmed)
            val ip = host.asAddress()?.toString() ?: trimmed
            val port = host.port ?: 0
            ip to port
        } catch (_: inet.ipaddr.HostNameException) {
            trimmed to 0
        } catch (_: inet.ipaddr.AddressStringException) {
            trimmed to 0
        }
    }


    override fun preflow(
        protocol: Int,
        uid: Int,
        src: String?,
        dst: String?
    ): PreMark = go2kt(preflowDispatcher) {
        val srcIpPort = parseIpAndPort(src)
        val dstIpPort = parseIpAndPort(dst)
        Logger.d(LOG_TAG_VPN, "preflow - init: $uid, rcvd: $src & $dst, parsed: $srcIpPort & $dstIpPort")
        val newUid = if (uid == INVALID_UID) { // fetch uid only if it is invalid
            getUid(
                uid,
                protocol,
                srcIpPort.first,
                srcIpPort.second,
                dstIpPort.first,
                dstIpPort.second
            )
        } else {
            uid
        }
        Logger.d(LOG_TAG_VPN, "preflow: $newUid, $srcIpPort, $dstIpPort")

        val p = PreMark()
        p.uid = FirewallManager.appId(newUid, isPrimaryUser()).toString()
        p.isUidSelf = newUid == rethinkUid
        Logger.i(LOG_TAG_VPN, "preflow: returning ${p.uid} for src: $srcIpPort, dst: $dstIpPort, isRethink? ${p.isUidSelf}")
        return@go2kt p
    }

    override fun flow(
        protocol: Int,
        _uid: Int,
        src: String?,
        dst: String?,
        realIps: String?,
        d: String?,
        possibleDomains: String?,
        blocklists: String?,
        isAlg: Boolean,
    ): Mark = go2kt(flowDispatcher) {
        logd("flow: $_uid, $src, $dst, $realIps, $d, $blocklists")
        handleVpnLockdownStateAsync()

        // in case of double loopback, all traffic will be part of rinr instead of just rethink's
        // own traffic. flip the doubleLoopback flag to true if we need that behavior
        val doubleLoopback = false

        val srcIpPort = parseIpAndPort(src)
        val dstIpPort = parseIpAndPort(dst)
        val srcIp = srcIpPort.first
        val srcPort = srcIpPort.second
        val dstIp = dstIpPort.first
        val dstPort = dstIpPort.second

        val rinr = persistentState.routeRethinkInRethink

        val ips = realIps?.split(",") ?: emptyList()
        // take the first non-unspecified ip as the real destination ip
        val fip = ips.firstOrNull { !isUnspecifiedIp(it.trim()) }?.trim()
        // use realIps; as of now, netstack uses the first ip
        // TODO: apply firewall rules on all real ips
        val realDestIp =
            if (fip.isNullOrEmpty()) {
                dstIp
            } else {
                fip
            }
        var uid = getUid(
            _uid,
            protocol,
            srcIp,
            srcPort,
            dstIp,
            dstPort
        )
        uid = FirewallManager.appId(uid, isPrimaryUser())
        val userId = FirewallManager.userId(uid)

        // generates a random 8-byte value, converts it to hexadecimal, and then
        // provides the hexadecimal value as a string for connId
        val connId = Utilities.getRandomString(8)

        // TODO: handle multiple domains, for now, use the first domain
        val domains = d?.split(",") ?: emptyList()

        // if `d` is blocked, then at least one of the real ips is unspecified
        val anyRealIpBlocked = !ips.none { isUnspecifiedIp(it.trim()) }
        val connType =
            if (isConnectionMetered(realDestIp)) {
                ConnectionTracker.ConnType.METERED
            } else {
                ConnectionTracker.ConnType.UNMETERED
            }

        val cm =
            createConnTrackerMetaData(
                uid,
                userId,
                srcIp,
                srcPort,
                if (isAlg && ips.isNotEmpty()) realIps.orEmpty() else realDestIp,
                dstPort,
                protocol,
                proxyDetails = "", // set later
                blocklists.orEmpty(),
                domains.firstOrNull(),
                connId,
                connType
            )

        val trapVpnDns = isDns(dstPort) && isVpnDns(dstIp)
        val trapVpnPrivateDns = isVpnDns(dstIp) && isPrivateDns(dstPort)

        val isRethink = uid == rethinkUid

        // always block, since the vpn tunnel doesn't serve dns-over-tls
        if (trapVpnPrivateDns) {
            logd("flow: dns-over-tls, returning Ipn.Block, $uid, $domains")
            cm.isBlocked = true
            cm.blockedByRule = FirewallRuleset.RULE14.id
            return@go2kt persistAndConstructFlowResponse(cm, Backend.Block, connId, uid)
        }

        val proxyRoutingSnapshot = buildProxyRoutingSnapshot(uid)
        // app is considered as spl when it is selected to forward dns proxy, socks5 or http proxy
        val isSplApp = isSpecialApp(proxyRoutingSnapshot)

        if (isRethink && !rinr) {
            // case when uid is rethink, return Ipn.Base
            logd(
                "flow: Ipn.Exit for rethink, $uid, $packageName, $srcIp, $srcPort, $realDestIp, $dstPort, $domains, $possibleDomains"
            )
            if (cm.query.isNullOrEmpty()) {
                // possible domains only used for logging purposes, it may be available if
                // the domains are empty. So, use the possibleDomains only if domains is empty
                // no need to show the possible domains other than rethink
                cm.query = possibleDomains?.split(",")?.firstOrNull() ?: ""
            }

            // TODO: should handle the LanIp.GATEWAY, LanIp.ROUTER addresses as well
            // now only handling the LanIp.DNS address, handle it once go implementation is ready

            // if trapVpnDns is true, then Ipn.Exit won't be able to route the request via the
            // underlying network as the IP only exists within the VPN tunnel. So, use Ipn.Base
            // and expect Android's netd via the network engine to re-route as appropriate.
            val proxy =
                if (trapVpnDns) {
                    // on Android Q and below, the uid for local dns(to VPN's dns servers)
                    // is always DNS.uid (AID_DNS), ie., the true src for the dns request
                    // is not known. override uid with INVALID_UID to force a preflow() call
                    // on Android P and above, the uid for private dns is also DNS.uid (AID_DNS)
                    // which we should not override and let it out as it is
                    if (uid == AndroidUidConfig.DNS.uid) {
                        uid = INVALID_UID
                    }
                    Backend.Base
                    // do not add the trackedCids for dns entries as there will not be any
                    // onSocketClosed event for dns entries
                } else {
                    // add to trackedCids, so that the connection can be removed from the list when the
                    // connection is closed (onSocketClosed), use: ui to show the active connections
                    val key = CidKey(cm.connId, uid)
                    synchronized(activeCids) {
                        activeCids.add(key)
                    }
                    if (persistentState.autoProxyEnabled) {
                        Backend.Auto
                    } else {
                        Backend.Exit
                    }
                }

            return@go2kt persistAndConstructFlowResponse(cm, proxy, connId, uid)
        }

        if (trapVpnDns) {
            // see the comment above for the reasoning #trapVpnDns
            if (uid == AndroidUidConfig.DNS.uid) {
                uid = INVALID_UID
            }
            // android R+, uid will be there for dns request as well
            logd("flow: dns-request, returning ${Backend.Base}, $uid, $connId, $domains")
            return@go2kt persistAndConstructFlowResponse(null, Backend.Base, connId, uid)
        }
        processFirewallRequest(cm, d, anyRealIpBlocked, blocklists.orEmpty(), isSplApp, rinr, isAlg)
        cm.destIP = realDestIp

        if (cm.isBlocked) {
            // return Ipn.Block, no need to check for other rules
            logd("flow: received rule: block, returning Ipn.Block, $connId, $uid, $domains")
            return@go2kt persistAndConstructFlowResponse(cm, Backend.Block, connId, uid)
        }

        // add to trackedCids, so that the connection can be removed from the list when the
        // connection is closed (onSocketClosed), use: ui to show the active connections
        val key = CidKey(cm.connId, uid)
        synchronized(activeCids) {
            activeCids.add(key)
        }

        return@go2kt determineProxyDetails(cm, doubleLoopback, rinr, proxyRoutingSnapshot)
    }

    override fun inflow(protocol: Int, recvdUid: Int, src: String?, dst: String?): Mark =
        go2kt(inflowDispatcher) {
            val srcIpPort = parseIpAndPort(src)
            val dstIpPort = parseIpAndPort(dst)
            val srcIp = srcIpPort.first
            val srcPort = srcIpPort.second
            val dstIp = dstIpPort.first
            val dstPort = dstIpPort.second

            val rinr = persistentState.routeRethinkInRethink

            var uid = getUid(
                recvdUid,
                protocol,
                srcIp,
                srcPort,
                dstIp,
                dstPort
            )
            uid = FirewallManager.appId(uid, isPrimaryUser())
            val userId = FirewallManager.userId(uid)

            logd("inflow: $uid($recvdUid), $srcIp, $srcPort, $dstIp, $dstPort")

            val connId = Utilities.getRandomString(8)

            val connType =
                if (isConnectionMetered(dstIp)) {
                    ConnectionTracker.ConnType.METERED
                } else {
                    ConnectionTracker.ConnType.UNMETERED
                }

            val cm =
                createConnTrackerMetaData(
                    uid,
                    userId,
                    srcIp,
                    srcPort,
                    dstIp,
                    dstPort,
                    protocol,
                    proxyDetails = "",
                    "",
                    "",
                    connId,
                    connType
                )

            processFirewallRequest(cm, "",false, "", rinr = rinr)

            if (cm.isBlocked) {
                // return Ipn.Block, no need to check for other rules
                logd("inflow: received rule: block, returning Ipn.Block, $connId, $uid")
                return@go2kt persistAndConstructFlowResponse(cm, Backend.Block, connId, uid)
            }

            // add to trackedCids, so that the connection can be removed from the list when the
            // connection is closed (onSocketClosed), use: ui to show the active connections
            val key = CidKey(cm.connId, uid)
            synchronized(activeCids) {
                activeCids.add(key)
            }

            logd("inflow: determine proxy and other dtls for $connId, $uid")

            // the proxy id (other than block) will be ignored by the go code, so use
            // Backend.Ingress as a placeholder
            return@go2kt persistAndConstructFlowResponse(cm, Backend.Ingress, connId, uid)
        }

    // no need of go2kt here as it is called from go and performs db operations with no return value
    // requires go2kt if there any calls to go functions
    override fun flowing(m: Mark?) {
        val mark = m
        if (mark == null) {
            Logger.e(LOG_TAG_VPN, "postFlow: received null mark")
            return
        }
        val cm = getConnTrackerMetaData(m.cid)
        if (cm == null) {
            Logger.w(LOG_TAG_VPN, "postFlow: no connection metadata found for mark: $mark")
            return
        }

        val isRethink = cm.uid == rethinkUid
        cm.proxyDetails = mark.pidcsv
        cm.destIP = mark.ip
        val isNotLocalProxy = isNotLocalAndRpnProxy(mark.pidcsv)
        if (mark.pidcsv.isNotEmpty() && isNotLocalProxy) {
            cm.blockedByRule = FirewallRuleset.RULE12.id
        }

        if (isRethink) {
            netLogTracker.writeRethinkLog(cm)
        } else {
            netLogTracker.writeIpLog(cm)
        }

        logd("flow/postFlow, write conn in db: $mark")
    }

    fun handleExpiredConnMetaData(cm: ConnTrackerMetaData, cause: ExpiringCache.RemovalCause) {
        if (cause != ExpiringCache.RemovalCause.EXPIRED) return

        val isRethink = cm.uid == rethinkUid
        cm.proxyDetails = ""
        cm.rpid = ""
        cm.downloadBytes = 0L
        cm.uploadBytes = 0L
        cm.duration = 0
        cm.synack = 0L
        cm.message = "no metadata"
        if (isRethink) {
            netLogTracker.writeRethinkLog(cm)
        } else {
            netLogTracker.writeIpLog(cm)
        }
        Logger.d(LOG_TAG_VPN, "expired connMetaData, close conns: $cm")
    }

    private data class ProxyRoutingSnapshot(
        val uid: Int,
        val packageName: String?,
        val appExcludedFromProxy: Boolean,
        val isDnsFirewallMode: Boolean,
        val isProxyEnabled: Boolean,
        val isOrbotProxyEnabled: Boolean,
        val isCustomSocks5Enabled: Boolean,
        val isCustomHttpProxyEnabled: Boolean,
        val isDnsProxyActive: Boolean,
        val orbotProxyAppName: String?,
        val socks5ProxyAppName: String?,
        val httpProxyAppName: String?,
        val dnsProxyAppName: String?,
        val orbotProxyAssignedToApp: Boolean
    )

    private suspend fun buildProxyRoutingSnapshot(uid: Int): ProxyRoutingSnapshot {
        val packageName = FirewallManager.getPackageNameByUid(uid)
        val isOrbotProxyEnabled = appConfig.isOrbotProxyEnabled()
        val isCustomSocks5Enabled = appConfig.isCustomSocks5Enabled()
        val isCustomHttpProxyEnabled = appConfig.isCustomHttpProxyEnabled()
        val isDnsProxyActive = appConfig.isDnsProxyActive()

        val orbotProxyAppName =
            if (isOrbotProxyEnabled) {
                appConfig.getConnectedOrbotProxy()?.proxyAppName
            } else {
                null
            }
        val socks5ProxyAppName =
            if (isCustomSocks5Enabled) {
                appConfig.getSocks5ProxyDetails()?.proxyAppName
            } else {
                null
            }
        val httpProxyAppName =
            if (isCustomHttpProxyEnabled) {
                appConfig.getHttpProxyDetails()?.proxyAppName
            } else {
                null
            }
        val dnsProxyAppName =
            if (isDnsProxyActive) {
                appConfig.getSelectedDnsProxyDetails()?.proxyAppName
            } else {
                null
            }

        if (isCustomSocks5Enabled && socks5ProxyAppName == null) {
            Logger.e(LOG_TAG_VPN, "flow: socks5 proxy enabled but endpoint is null")
        }
        if (isCustomHttpProxyEnabled && httpProxyAppName == null) {
            Logger.e(LOG_TAG_VPN, "flow: http proxy enabled but endpoint is null")
        }
        if (isCustomSocks5Enabled) {
            logd("flow/inflow: socks5 proxy is enabled, $packageName, $socks5ProxyAppName")
        }

        return ProxyRoutingSnapshot(
            uid = uid,
            packageName = packageName,
            appExcludedFromProxy = FirewallManager.isAppExcludedFromProxy(uid),
            isDnsFirewallMode = appConfig.getBraveMode().isDnsFirewallMode(),
            isProxyEnabled = appConfig.isProxyEnabled(),
            isOrbotProxyEnabled = isOrbotProxyEnabled,
            isCustomSocks5Enabled = isCustomSocks5Enabled,
            isCustomHttpProxyEnabled = isCustomHttpProxyEnabled,
            isDnsProxyActive = isDnsProxyActive,
            orbotProxyAppName = orbotProxyAppName,
            socks5ProxyAppName = socks5ProxyAppName,
            httpProxyAppName = httpProxyAppName,
            dnsProxyAppName = dnsProxyAppName,
            orbotProxyAssignedToApp =
                if (isOrbotProxyEnabled) {
                    ProxyManager.getProxyIdForApp(uid).contains(ProxyManager.ID_ORBOT_BASE)
                } else {
                    false
                }
        )
    }

    private fun isSpecialApp(snapshot: ProxyRoutingSnapshot): Boolean {

        val request =
            ProxyRoutingEngine.SpecialAppRequest(
                isDnsFirewallMode = snapshot.isDnsFirewallMode,
                isOrbotProxyEnabled = snapshot.isOrbotProxyEnabled,
                isCustomSocks5Enabled = snapshot.isCustomSocks5Enabled,
                isCustomHttpProxyEnabled = snapshot.isCustomHttpProxyEnabled,
                isDnsProxyActive = snapshot.isDnsProxyActive,
                packageName = snapshot.packageName,
                orbotProxyAppName = snapshot.orbotProxyAppName,
                socks5ProxyAppName = snapshot.socks5ProxyAppName,
                httpProxyAppName = snapshot.httpProxyAppName,
                dnsProxyAppName = snapshot.dnsProxyAppName
            )

        val isSpecialApp = ProxyRoutingEngine.isSpecialApp(request)
        if (isSpecialApp) {
            logd("flow/inflow: special app matched for proxy routing, ${snapshot.uid}, ${snapshot.packageName}")
        }
        return isSpecialApp
    }

    private suspend fun determineProxyDetails(
        connTracker: ConnTrackerMetaData,
        doubleLoopback: Boolean,
        rinr: Boolean,
        proxySnapshot: ProxyRoutingSnapshot
    ): Mark {
        val connId = connTracker.connId
        val uid = connTracker.uid
        val baseOrExit =
            ProxyRoutingEngine.resolveBaseOrExitProxyId(
                doubleLoopback = doubleLoopback,
                blockedByRule = connTracker.blockedByRule,
                rinr = rinr,
                uid = uid,
                rethinkUid = rethinkUid,
                autoProxyEnabled = persistentState.autoProxyEnabled
            )

        // Add baseOrExit in the end of the list if needed (not true for lockdown).
        val ssid = getUnderlyingSsid().orEmpty()
        val wireguardProxyIds =
            WireguardManager.getAllPossibleConfigIdsForApp(
                uid,
                connTracker.destIP,
                connTracker.destPort,
                connTracker.query ?: "",
                isIfaceCellular(connTracker.destIP),
                ssid,
                baseOrExit
            )

        val decision =
            ProxyRoutingEngine.determineRoute(
                ProxyRoutingEngine.RoutingRequest(
                    uid = uid,
                    rethinkUid = rethinkUid,
                    rinr = rinr,
                    autoProxyEnabled = persistentState.autoProxyEnabled,
                    blockedByRule = connTracker.blockedByRule,
                    appExcludedFromProxy = proxySnapshot.appExcludedFromProxy,
                    baseOrExitProxyId = baseOrExit,
                    wireguardProxyIds = wireguardProxyIds,
                    isProxyEnabled = proxySnapshot.isProxyEnabled,
                    isDnsProxyActive = proxySnapshot.isDnsProxyActive,
                    isOrbotProxyEnabled = proxySnapshot.isOrbotProxyEnabled,
                    isCustomSocks5Enabled = proxySnapshot.isCustomSocks5Enabled,
                    isCustomHttpProxyEnabled = proxySnapshot.isCustomHttpProxyEnabled,
                    packageName = proxySnapshot.packageName,
                    orbotProxyAppName = proxySnapshot.orbotProxyAppName,
                    orbotProxyAssignedToApp = proxySnapshot.orbotProxyAssignedToApp,
                    socks5ProxyAppName = proxySnapshot.socks5ProxyAppName,
                    httpProxyAppName = proxySnapshot.httpProxyAppName,
                    dnsProxyAppName = proxySnapshot.dnsProxyAppName
                )
            )

        if (decision.markBlocked) {
            connTracker.isBlocked = true
        }
        decision.blockedByRuleOverride?.let {
            connTracker.blockedByRule = it
        }
        if (decision.orbotProxyEnabledButAppNotIncluded) {
            Logger.e(LOG_TAG_VPN, "flow/inflow: orbot proxy is enabled but app is not included")
        }

        when (decision.reason) {
            ProxyRoutingEngine.Reason.RETHINK_DIRECT ->
                logd("flow/inflow: ${decision.proxyIds} for rethink, $uid, $connId")
            ProxyRoutingEngine.Reason.EXCLUDED_APP ->
                logd("flow/inflow: app is excluded from proxy, returning ${decision.proxyIds}, $connId, $uid")
            ProxyRoutingEngine.Reason.WIREGUARD ->
                logd("flow/inflow: wg route selected, ${decision.proxyIds}, $connId, $uid")
            ProxyRoutingEngine.Reason.NO_PROXY_ACTIVE ->
                logd("flow/inflow: no proxy/dnsproxy enabled, returning ${decision.proxyIds}, $connId, $uid")
            ProxyRoutingEngine.Reason.ORBOT_DIRECT_APP ->
                logd("flow/inflow: orbot ${decision.proxyIds} for ${proxySnapshot.packageName}, $connId, $uid")
            ProxyRoutingEngine.Reason.ORBOT_PROXY ->
                logd("flow/inflow: orbot proxy for $uid, $connId")
            ProxyRoutingEngine.Reason.SOCKS5_DIRECT_APP ->
                logd("flow/inflow: socks5 ${decision.proxyIds} for ${proxySnapshot.packageName}, $connId, $uid")
            ProxyRoutingEngine.Reason.SOCKS5_PROXY ->
                logd("flow/inflow: socks5 proxy for $connId, $uid")
            ProxyRoutingEngine.Reason.HTTP_DIRECT_APP ->
                logd("flow/inflow: http ${decision.proxyIds} for ${proxySnapshot.packageName}, $connId, $uid")
            ProxyRoutingEngine.Reason.HTTP_PROXY ->
                logd("flow/inflow: http proxy for $connId, $uid")
            ProxyRoutingEngine.Reason.DNS_PROXY_DIRECT_APP ->
                logd("flow/inflow: dns proxy enabled for ${proxySnapshot.packageName}, return ${decision.proxyIds}, $connId, $uid")
            ProxyRoutingEngine.Reason.FALLBACK_BASE_OR_EXIT ->
                logd("flow/inflow: no proxies, ${decision.proxyIds}, $connId, $uid")
        }
        return persistAndConstructFlowResponse(connTracker, decision.proxyIds, connId, uid)
    }

    fun hasCid(connId: String, uid: Int): Boolean {
        // get app id from uid
        val uid0 = FirewallManager.appId(uid, isPrimaryUser())
        val key = CidKey(connId, uid0)
        synchronized(activeCids) {
            return activeCids.contains(key)
        }
    }

    suspend fun removeWireGuardProxy(id: Int) {
        logd("remove wg from tunnel: $id")
        vpnAdapter?.removeWgProxy(id)
    }

    suspend fun addWireGuardProxy(id: String, force: Boolean = false) {
        vpnAdapter?.invalidateProxyProbes()
        logd("add wg from tunnel: $id")
        vpnAdapter?.addWgProxy(id, force)
    }

    suspend fun pauseMobileOnlyWireGuardOnNoNw() {
        val activeWgs = WireguardManager.getActiveConfigs()
        activeWgs.forEach { config ->
            val map = WireguardManager.getConfigFilesById(config.getId())
            if (map == null || !map.useOnlyOnMetered) {
                // if the config is not using only on metered, then skip it
                logd("pause wg from tunnel: ${config.getId()} is not using only on metered")
                return@forEach
            }
            val id = ID_WG_BASE + config.getId()
            logd("pause wg from tunnel (mobile): $id")
            // pause the wireguard proxy, so that it won't be used for new connections
            vpnAdapter?.pauseWireguard(id)
        }
    }

    suspend fun pauseSsidEnabledWireGuardOnNoNw() {
        val activeWgs = WireguardManager.getActiveConfigs()
        activeWgs.forEach { config ->
            val map = WireguardManager.getConfigFilesById(config.getId())
            if (map == null || !map.ssidEnabled) {
                // if the config is not using ssid restriction, then skip it
                logd("pause wg from tunnel: ${config.getId()} is not using ssid restriction")
                return@forEach
            }
            val id = ID_WG_BASE + config.getId()
            logd("pause wg from tunnel (ssid): $id")
            // pause the wireguard proxy, so that it won't be used for new connections
            vpnAdapter?.pauseWireguard(id)
        }
    }

    suspend fun refreshOrPauseOrResumeOrReAddProxies() {
        withContext(CoroutineName("ref-pro") + serializer) {
            logd("refresh wg config")
            // perform the active network mobile check
            val newNet = underlyingNetworks
            val v4first = newNet?.ipv4Net?.firstOrNull()
            val v6first = newNet?.ipv6Net?.firstOrNull()
            val v4Mobile = v4first?.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            val v6Mobile = v6first?.capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
            val isActiveMobile = v4Mobile || (v4first == null && v6Mobile)
            val activeSsid = getUnderlyingSsid() ?: ""
            Logger.v(LOG_TAG_VPN, "refreshOrPauseOrResumeOrReAddProxies: canResumeMobileOnlyWg? $isActiveMobile, curr-ssid: $activeSsid")
            io("refreshWg") { vpnAdapter?.refreshOrPauseOrResumeOrReAddProxies(isActiveMobile, activeSsid) }
        }
    }

    suspend fun getDnsStatus(id: String): Int? {
        return vpnAdapter?.getDnsStatus(id)
    }

    suspend fun getRDNS(type: RethinkBlocklistManager.RethinkBlocklistType): RDNS? {
        return vpnAdapter?.getRDNS(type)
    }

    private fun persistAndConstructFlowResponse(
        cm: ConnTrackerMetaData?,
        proxyIds: String,
        connId: String,
        uid: Int
    ): Mark {
        // override exit in case of rethink plus subscription
        // case: do not override the proxyId in case of rethink as rethink's traffic should
        // always use Exit proxy not the rpn proxy


        if (cm != null) {
            // in case of multiple proxies we do not need to write the log as we are not sure
            // which proxy is used for the connection, so wait for the postflow/onSocketClosed
            // to write the log, until that maintain the connTrackerMetaData in a set
            val containsMultipleProxy = proxyIds
                .split(",")
                .map { it.trim() }.count { it.isNotEmpty() } > 1
            if (containsMultipleProxy && !cm.isBlocked) {
                trackedConnMetaData.put(cm.connId, cm)
                if (DEBUG) logd("flow/inflow/postflow: multiple proxies for connId: $connId, proxies: $proxyIds, uid: $uid, cache-size: ${trackedConnMetaData.size()}, cm: $cm")
            } else {
                cm.proxyDetails = proxyIds // contains only one proxy id

                // set proxied rule if the proxy is ipn
                if (proxyIds.isNotEmpty() && isNotLocalAndRpnProxy(proxyIds)) {
                    cm.blockedByRule = FirewallRuleset.RULE12.id
                }

                if (uid == rethinkUid) {
                    netLogTracker.writeRethinkLog(cm)
                } else {
                    netLogTracker.writeIpLog(cm)
                }
            }
            logd("flow/inflow: connTracker: $cm, isRethink? ${cm.uid == rethinkUid}")
        }

        val mark = Mark()

        mark.pidcsv = proxyIds
        mark.cid = connId
        // no need to handle rethink
        mark.uid = uid.toString()
        if (cm == null) {
            Logger.i(
                LOG_TAG_VPN,
                "flow/inflow: returning mark: $mark for connId: $connId, uid: $uid, cm: null"
            )
        } else {
            Logger.i(
                LOG_TAG_VPN,
                "flow/inflow: returning mark: $mark for src(${cm.sourceIP}: ${cm.sourcePort}), dest(${cm.destIP}:${cm.destPort}, ${cm.query})"
            )
        }
        return mark
    }

    private suspend fun processFirewallRequest(
        metadata: ConnTrackerMetaData,
        domains: String?,
        anyRealIpBlocked: Boolean = false,
        blocklists: String = "",
        isSplApp: Boolean = false,
        rinr: Boolean,
        isAlg: Boolean = false,
    ) {
        val rule = firewall(metadata, domains, anyRealIpBlocked, isSplApp, rinr, isAlg)

        metadata.blockedByRule = rule.id
        metadata.blocklists = blocklists

        val blocked = FirewallRuleset.ground(rule)
        metadata.isBlocked = blocked

        addCidToTrackedCidsToCloseIfNeeded(metadata.connId, rule)

        logd("firewall-rule $rule on conn: ${metadata.connId}; $metadata")
        return
    }

    private fun addCidToTrackedCidsToCloseIfNeeded(cid: String, rule: FirewallRuleset) {
        // no need to track the blocked connections, as they will be closed
        if (FirewallRuleset.ground(rule)) {
            return
        }
        // skip the connections if the rules is part of any bypass rules
        // like, app bypass, dns bypass, domain trust, ip trust
        if (FirewallRuleset.isBypassRule(rule)) {
            return
        }

        Logger.v(LOG_TAG_VPN, "firewall-rule $rule, adding to trackedCids to close, $cid")
        synchronized(activeClosableCids) {
            activeClosableCids.add(cid)
        }
    }

    // this method is called when the device is locked, so no need to check for device lock here
    private fun closeTrackedConnsOnDeviceLock() {
        io("devLockCloseConns") {
            val cidsToClose: List<String> = synchronized(activeClosableCids) {
                if (activeClosableCids.isEmpty()) emptyList<String>()

                val snapshot = activeClosableCids.toList()
                activeClosableCids.clear()
                snapshot
            }
            if (cidsToClose.isNotEmpty()) {
                vpnAdapter?.closeConnections(cidsToClose, isUid = false, "dev-lock-close-conns")
            }
        }
    }

    private fun isLockdown(): Boolean {
        return isLockDownPrevious.get()
    }

    private fun createConnTrackerMetaData(
        uid: Int,
        usrId: Int,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int,
        protocol: Int,
        proxyDetails: String = "",
        blocklists: String = "",
        query: String? = "",
        connId: String,
        connType: ConnectionTracker.ConnType
    ): ConnTrackerMetaData {

        // Ref: ipaddress doc:
        // https://seancfoley.github.io/IPAddress/ipaddress.html#host-name-or-address-with-port-or-service-name
        logd(
            "createConnInfoObj: uid: $uid, srcIp: $srcIp, srcPort: $srcPort, dstIp: $dstIp, dstPort: $dstPort, protocol: $protocol, query: $query, connId: $connId"
        )

        return ConnTrackerMetaData(
            uid,
            usrId,
            srcIp,
            srcPort,
            dstIp,
            dstPort,
            System.currentTimeMillis(),
            false, /*blocked?*/
            "", /*rule*/
            proxyDetails,
            blocklists,
            protocol,
            query,
            connId,
            connType.value
        )
    }

    suspend fun getProxyStatusById(id: String): Pair<Int?, String> {
        return vpnAdapter?.getProxyStatusById(id) ?: Pair(null, "adapter is null")
    }

    suspend fun getProxyStats(id: String): RouterStats? {
        return vpnAdapter?.getProxyStats(id)
    }

    suspend fun getWireGuardStats(id: String): WireguardManager.WgStats? {
        return vpnAdapter?.getWireGuardStats(id)
    }

    suspend fun getSupportedIpVersion(id: String): Pair<Boolean, Boolean>? {
        return vpnAdapter?.getSupportedIpVersion(id) ?: return Pair(false, false)
    }

    suspend fun isSplitTunnelProxy(id: String, pair: Pair<Boolean, Boolean>): Boolean {
        return vpnAdapter?.isSplitTunnelProxy(id, pair) ?: false
    }

    suspend fun p50(id: String): Long {
        return vpnAdapter?.p50(id) ?: -1L
    }

    override fun onTrimMemory(level: Int) {
        // override onLowMemory is deprecated, so use onTrimMemory
        // ref: developer.android.com/reference/android/net/VpnService
        super.onTrimMemory(level)
        Logger.i(LOG_TAG_VPN, "onTrimMemory: $level")
        if (level >= TRIM_MEMORY_BACKGROUND) {
            // TODO: call go to clear the cache
            // show notification to user, that the app is consuming more memory
            vpnNotificationManager.showMemoryNotification()
        }
        io("onLowMem") { vpnAdapter?.onLowMemory() }
    }

    override fun onRevoke() {
        Logger.i(LOG_TAG_VPN, "onRevoke, stop vpn adapter")
        signalStopService("revoked", false)
    }

    suspend fun getSystemDns(): String {
        return vpnAdapter?.getSystemDns() ?: ""
    }

    suspend fun getNetStat(): NetStat? {
        return vpnAdapter?.getNetStat()
    }

    fun writeConsoleLog(log: ConsoleLog) {
        netLogTracker.writeConsoleLog(log)
    }

    suspend fun isProxyReachable(_proxyId: String, csv: String): Boolean { // can be ippcsv or hostpcsv
        return vpnAdapter?.isRpnReachable(csv) == true
    }

    suspend fun isRpnReachable(csv: String): Boolean {
        return vpnAdapter?.isRpnReachable(csv) == true
    }

    suspend fun testRpnProxy(): Boolean {
        return vpnAdapter?.testRpnProxy() == true
    }

    suspend fun testHop(src: String, hop: String): Pair<Boolean, String?> {
        return vpnAdapter?.testHop(src, hop) ?: Pair(false, "vpn not active")
    }

    suspend fun hopStatus(src: String, via: String): Pair<Int?, String> {
        return vpnAdapter?.hopStatus(src, via) ?: Pair(null, "vpn not active")
    }

    suspend fun removeHop(src: String): Pair<Boolean, String> {
        return vpnAdapter?.removeHop(src) ?: Pair(false, "vpn not active")
    }

    suspend fun getRpnProps(type: RpnType): Pair<RpnProxyManager.RpnProps?, String?> {
        return vpnAdapter?.getRpnProps(type) ?: Pair(null, null)
    }

    suspend fun getRpnLocations(type: RpnType): Pair<RpnServers?, String?> =
        vpnAdapter?.getRpnLocations(type) ?: Pair(null, null)

    suspend fun registerAndFetchWinIfNeeded(prevBytes: ByteArray?, deviceId: String): ByteArray? =
        registerAndFetchWinIfNeeded(prevBytes, null, deviceId)

    suspend fun registerAndFetchWinIfNeeded(entitlement: ByteArray?, state: ByteArray?, deviceId: String): ByteArray? {
        return vpnAdapter?.registerAndFetchWinIfNeeded(entitlement, state, deviceId)
    }

    suspend fun getEntitlementDetails(prevBytes: ByteArray?, deviceId: String): RpnEntitlement? =
        vpnAdapter?.getEntitlementDetails(prevBytes, deviceId)

    suspend fun isWinRegistered(): Boolean = vpnAdapter?.isWinRegistered() == true

    suspend fun startPcapCapture(mode: Int, path: String): Boolean = pcapSession.start(mode, path)

    suspend fun stopPcapCapture(): Boolean = pcapSession.stop()

    suspend fun getActiveEntitlement(): com.celzero.firestack.backend.RpnEntitlement? = vpnAdapter?.getActiveEntitlement()

    suspend fun unregisterWin(): Boolean = vpnAdapter?.unregisterWin() == true

    suspend fun getWinExpiryTs(): Long? = vpnAdapter?.getWinExpiryTs()

    suspend fun updateWin(): ByteArray? {
        return vpnAdapter?.updateWin()
    }

    suspend fun getRpnStats(id: String): RpnProxyManager.RpnStats? = vpnAdapter?.getRpnStats(id)

    suspend fun getWinByKey(key: String): Proxy? = vpnAdapter?.getWinByKey(key)

    suspend fun addNewWinServer(key: String): Pair<Boolean, String> =
        vpnAdapter?.addNewWinServer(key) ?: Pair(false, "adapter is null")

    suspend fun handleRpnHop(key: String, configChanged: Boolean): Pair<Boolean, String> =
        vpnAdapter?.handleRpnHop(key, configChanged) ?: Pair(false, "adapter is null")

    suspend fun removeWinServer(key: String): Pair<Boolean, String> =
        vpnAdapter?.removeWinServer(key) ?: Pair(false, "adapter is null")

    suspend fun initiateWgPing(proxyId: String) {
        vpnAdapter?.initiateWgPing(proxyId)
    }

    suspend fun initiateRpnPing(proxyId: String) {
        vpnAdapter?.initiateRpnPing(proxyId)
    }

    fun screenLock() {
        closeTrackedConnsOnDeviceLock()
    }

    suspend fun createWgHop(origin: String, hop: String): Pair<Boolean, String> {
        return (vpnAdapter?.createHop(origin, hop)) ?: Pair(false, "adapter is null")
    }
    suspend fun vpnStats(): String {
        return vpnServiceDiagnostics.vpnStats(builderStats())
    }

    fun performConnectivityCheck(controller: Controller, id: String, addrPort: String): Boolean {
        return vpnAdapter?.performConnectivityCheck(controller, id, addrPort) ?: false
    }

    fun performAutoConnectivityCheck(controller: Controller, id: String, mode: String): Boolean {
        return vpnAdapter?.performAutoConnectivityCheck(controller, id, mode) ?: false
    }

    fun screenUnlock() {
        io("screenUnlock") {
            // initiate wireguard ping for one wg, catch-all, hop proxies
            val proxies = WireguardManager.getActiveConfigs()
            Logger.i(LOG_TAG_VPN, "unlock: initiate ping for one-wg/catchall/hop/rpn proxies")
            proxies.forEach { c ->
                val isOneWg = WireguardManager.getOneWireGuardProxyId() == c.getId()
                val isCatchAll = WireguardManager.getActiveCatchAllConfig().any { it.id == c.getId()}
                val isPartOfHop = WgHopManager.isWgEitherHopOrSrc(c.getId())
                if (isOneWg || isCatchAll || isPartOfHop) {
                    val id = ID_WG_BASE + c.getId()
                    vpnAdapter?.initiateWgPing(id)
                }
            }
        }
    }

    private fun builderStats(): String {
        val n = Networks(underlyingNetworks, overlayNetworks)
        val (route4, route6) = determineRoutes(n)
        return vpnServiceDiagnostics.builderStats(
            n = n,
            builderRoutes = builderRoutes,
            builderStatsText = builderStats,
            fd = testFd.get(),
            systemDns = vpnDnsTransportController.systemDnsSnapshot(),
            tunUnderlyingNetworks = tunUnderlyingNetworks,
            lockdown = isLockdown(),
            vpnLockdown = underlyingNetworks?.vpnLockdown,
            ssid = getUnderlyingSsid() ?: "N/A",
            route4 = route4,
            route6 = route6,
            netid = ::netid,
            isLockdownEnabled = if (isAtleastQ()) isLockdownEnabled else false,
        )
    }

    fun isUnderlyingVpnNetworkEmpty(): Boolean {
        val tunUnderlyingNetworks = tunUnderlyingNetworks ?: return false
        // return the current underlying networks in the vpn adapter
        return tunUnderlyingNetworks.isEmpty()
    }

}

