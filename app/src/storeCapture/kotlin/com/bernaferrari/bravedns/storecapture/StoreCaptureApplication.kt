package com.bernaferrari.bravedns.storecapture

import android.content.pm.PackageManager
import android.util.Log
import androidx.work.WorkManager
import com.bernaferrari.bravedns.RethinkDnsApplication
import com.bernaferrari.bravedns.database.ConnectionTracker
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.database.IpInfo
import com.bernaferrari.bravedns.database.LogDatabase
import com.bernaferrari.bravedns.database.RefreshDatabase
import com.bernaferrari.bravedns.database.materializeStatsInserted
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.BraveVPNService
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.FirewallRuleset
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.service.VpnState
import com.bernaferrari.bravedns.ui.compose.theme.RethinkColorPreset
import com.bernaferrari.bravedns.util.Utilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import java.io.File
import kotlin.random.Random

/**
 * Store-capture host: the real app UI over fictional apps and traffic. The tunnel never starts
 * (the VPN service and INTERNET permission are removed from this build); [VpnController] is
 * pinned to a protected state so Home reads the way it does on a working device.
 */
class StoreCaptureApplication : RethinkDnsApplication() {

    override fun onCreate() {
        super.onCreate()
        // Background jobs would refresh blocklists, check updates or restart the (removed) VPN.
        WorkManager.getInstance(this).cancelAllWork()

        val state = get<PersistentState>()
        state.firstTimeLaunch = false
        state.checkForAppUpdate = false
        state.logsEnabled = true
        // The app's own coral seed rather than colours from the emulator's wallpaper.
        state.themeColorPreset = RethinkColorPreset.CORAL.id
        state.setVpnEnabled(true)
        VpnController.pinnedState =
            VpnState(requested = true, on = true, BraveVPNService.State.WORKING, state.connectedDnsName)

        val marker = File(filesDir, "storecapture-seeded")
        if (marker.exists()) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Let the app list pick up the fixture APKs first, then give them their rules and traffic.
            get<RefreshDatabase>().refresh(RefreshDatabase.ACTION_REFRESH_FORCE) {
                runCatching { seed() }
                    .onSuccess { marker.writeText("ok") }
                    .onFailure { Log.e(TAG, "seeding failed", it) }
            }
        }
    }

    private suspend fun seed() {
        val installed = fixtureApps.mapNotNull { app ->
            try {
                val info = packageManager.getApplicationInfo(app.packageName, 0)
                Installed(app, info.uid, packageManager.getApplicationLabel(info).toString())
            } catch (_: PackageManager.NameNotFoundException) {
                Log.w(TAG, "fixture app missing: ${app.packageName}; run store-listing/capture-emulator.py")
                null
            }
        }
        installed.forEach {
            FirewallManager.updateFirewallStatus(it.uid, it.app.firewall, it.app.connection)
        }

        val db = get<LogDatabase>()
        fixtureApps.flatMap { it.domains }.distinctBy { it.ip }.forEach { d ->
            db.ipInfoDao().insertReplace(
                IpInfo().apply {
                    ip = d.ip
                    asName = d.network
                    asDomain = d.name.substringAfter('.')
                    countryCode = d.country
                    createdTs = now
                },
            )
        }

        val (connections, queries) = traffic(installed)
        connections.chunked(500).forEach {
            db.connectionTrackerDAO().insertBatch(it)
            materializeStatsInserted(it)
        }
        queries.chunked(500).forEach {
            db.dnsLogDAO().insertBatch(it)
            materializeStatsInserted(it)
        }
        Log.i(TAG, "seeded ${installed.size} apps, ${connections.size} connections, ${queries.size} queries")
    }

    private class Installed(val app: FixtureApp, val uid: Int, val name: String)

    /**
     * A week of traffic ending now (the capture script sets the clock to 9:41): the last few
     * minutes follow [latest], so the first screen of each log tells the story; before that every
     * app's hosts are visited in proportion to its weight, densest in the last hour. Seeded, so
     * every capture shows the same numbers. The device is on Wi-Fi, so mobile-data blocks stay idle.
     */
    private fun traffic(apps: List<Installed>): Pair<List<ConnectionTracker>, List<DnsLog>> {
        val random = Random(941)
        val connections = mutableListOf<ConnectionTracker>()
        val queries = mutableListOf<DnsLog>()
        val byPackage = apps.associateBy { it.app.packageName }

        fun visit(owner: Installed, domain: FixtureDomain, minutesAgo: Double) {
            val time = now - (minutesAgo * 60_000).toLong()
            val appBlocked = owner.app.connection == FirewallManager.ConnectionStatus.BOTH
            queries += dnsLog(owner, domain, time, random)
            when {
                appBlocked -> connections += connection(owner, domain, time + 40, blocked = true, random)
                !domain.tracker -> connections += connection(owner, domain, time + 40, blocked = false, random)
                // A hard-coded address skips DNS; the firewall still drops it.
                random.nextInt(3) == 0 || minutesAgo < LATEST_MINUTES ->
                    connections += connection(owner, domain, time + 40, blocked = true, random, FirewallRuleset.RULE2G)
            }
        }

        latest.forEachIndexed { i, (pkg, host) ->
            val owner = byPackage[pkg] ?: return@forEachIndexed
            visit(owner, owner.app.domains.first { it.name == host }, 0.15 + i * 0.37)
        }
        val totalWeight = apps.sumOf { it.app.weight }
        repeat(2500) { i ->
            val minutesAgo = LATEST_MINUTES + when {
                i < 140 -> i * 0.38 + random.nextDouble(0.0, 0.35)
                i < 700 -> 55 + (i - 140) * 2.4 + random.nextDouble(0.0, 2.0)
                else -> 1400 + (i - 700) * 4.8 + random.nextDouble(0.0, 4.0)
            }
            var pick = random.nextInt(totalWeight)
            val owner = apps.first { pick -= it.app.weight; pick < 0 }
            visit(owner, owner.app.domains[random.nextInt(owner.app.domains.size)], minutesAgo)
        }
        // Oldest first, as a live device writes them (lists order by row id).
        return connections.sortedBy { it.timeStamp } to queries.sortedBy { it.time }
    }

    private fun connection(
        owner: Installed,
        domain: FixtureDomain,
        time: Long,
        blocked: Boolean,
        random: Random,
        rule: FirewallRuleset? = null,
    ) = ConnectionTracker().apply {
        appName = owner.name
        uid = owner.uid
        packageName = owner.app.packageName
        ipAddress = domain.ip
        port = domain.port
        protocol = if (domain.udp) 17 else 6
        isBlocked = blocked
        blockedByRule =
            (rule ?: when {
                !blocked -> FirewallRuleset.RULE0
                owner.app.connection == FirewallManager.ConnectionStatus.METERED -> FirewallRuleset.RULE1E
                else -> FirewallRuleset.RULE1
            }).id
        flag = Utilities.getFlag(domain.country)
        dnsQuery = domain.name
        timeStamp = time
        connId = "sc${time.toString(36)}${random.nextInt(1 shl 20).toString(36)}"
        connType = if (owner.app.connection == FirewallManager.ConnectionStatus.METERED && blocked) {
            ConnectionTracker.ConnType.METERED.value
        } else {
            ConnectionTracker.ConnType.UNMETERED.value
        }
        if (!blocked) {
            downloadBytes = random.nextLong(2_000, 900_000) * if (owner.app.weight > 10) 6 else 1
            uploadBytes = random.nextLong(600, 60_000)
            duration = random.nextInt(1, 240)
            synack = random.nextLong(18, 140)
        }
    }

    private fun dnsLog(owner: Installed, domain: FixtureDomain, time: Long, random: Random) = DnsLog().apply {
        uid = owner.uid
        appName = owner.name
        packageName = owner.app.packageName
        queryStr = domain.name
        this.time = time
        typeName = "A"
        status = Transaction.Status.COMPLETE.name
        dnsType = Transaction.TransportType.DOH.ordinal
        resolver = get<PersistentState>().connectedDnsName
        serverIP = resolver
        latency = random.nextLong(9, 48)
        responseTime = latency
        ttl = 300
        isCached = random.nextInt(3) == 0
        if (domain.tracker) {
            isBlocked = true
            response = "0.0.0.0"
            responseIps = "0.0.0.0"
            blockLists = "Ads & trackers"
        } else {
            flag = Utilities.getFlag(domain.country)
            response = Utilities.makeAddressPair(domain.country, domain.ip)
            responseIps = response
        }
    }

    private companion object {
        const val TAG = "StoreCapture"
        const val LATEST_MINUTES = 7.0

        /** The newest visits, newest first: what the top of the logs shows. */
        val latest = listOf(
            "tools.torchlight" to "ads.torchlight.tools",
            "chat.harbor" to "push.harbor.chat",
            "weather.clearsky" to "ads.brightpixel.net",
            "weather.clearsky" to "api.clearsky.app",
            "games.orbittiles" to "track.orbittiles.games",
            "app.fernway.maps" to "tiles.fernway.app",
            "io.glyphkeys" to "telemetry.glyphkeys.io",
            "tv.reelhouse" to "cdn.reelhouse.tv",
            "news.driftwood" to "pixel.readerstat.com",
            "mail.kestrel" to "imap.kestrelmail.com",
            "news.driftwood" to "feed.driftwood.news",
            "chat.harbor" to "voice.harbor.chat",
            "games.orbittiles" to "events.playmetric.io",
            "bank.marlow" to "api.marlowbank.com",
            "fm.cadence" to "stream.cadence.fm",
            "fit.stride" to "beacon.crashloom.dev",
            "shop.basketful" to "api.basketful.shop",
        )
        val now: Long get() = System.currentTimeMillis()
    }
}
