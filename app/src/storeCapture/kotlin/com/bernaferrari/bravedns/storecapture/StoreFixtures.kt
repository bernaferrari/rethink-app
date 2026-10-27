package com.bernaferrari.bravedns.storecapture

import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallManager.FirewallStatus

/**
 * Fictional apps and traffic for store screenshots. The apps themselves are code-free APKs that
 * store-listing/fixture-apps.json builds and the capture script installs, so names and icons reach
 * the app through the real PackageManager. Names, packages, domains and networks are invented;
 * addresses come from the documentation ranges (192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24).
 *
 * The story: messaging, maps and banking connect freely; video and music wait for Wi-Fi; a
 * flashlight, a keyboard and a puzzle game that only phone home are cut off; and the DNS
 * blocklists quietly drop the ad and tracking domains everyone else tries to reach.
 */
internal data class FixtureApp(
    val packageName: String,
    val firewall: FirewallStatus = FirewallStatus.NONE,
    val connection: ConnectionStatus = ConnectionStatus.ALLOW,
    /** Relative share of the week's traffic. */
    val weight: Int,
    val domains: List<FixtureDomain>,
)

/** A host an app talks to; [tracker] hosts are blocked by the DNS blocklists. */
internal data class FixtureDomain(
    val name: String,
    val ip: String,
    val country: String,
    val network: String,
    val tracker: Boolean = false,
    val port: Int = 443,
    val udp: Boolean = false,
)

private fun host(name: String, ip: String, country: String, network: String, port: Int = 443, udp: Boolean = false) =
    FixtureDomain(name, ip, country, network, port = port, udp = udp)

private fun tracker(name: String, ip: String, country: String, network: String) =
    FixtureDomain(name, ip, country, network, tracker = true)

// Shared ad and analytics networks, so blocked domains repeat across apps the way they do in life.
private val brightpixelAds = tracker("ads.brightpixel.net", "203.0.113.24", "US", "Brightpixel Media")
private val playmetric = tracker("events.playmetric.io", "203.0.113.71", "US", "Playmetric Analytics")
private val adorbit = tracker("sdk.adorbit.com", "203.0.113.55", "SG", "Adorbit Exchange")
private val datafunnel = tracker("collector.datafunnel.io", "203.0.113.77", "IE", "Datafunnel")
private val crashbeacon = tracker("beacon.crashloom.dev", "203.0.113.96", "DE", "Crashloom")

internal val fixtureApps: List<FixtureApp> =
    listOf(
        FixtureApp(
            "chat.harbor", weight = 18,
            domains = listOf(
                host("push.harbor.chat", "192.0.2.18", "NL", "Harbor Cloud"),
                host("media.harbor.chat", "192.0.2.19", "NL", "Harbor Cloud"),
                host("voice.harbor.chat", "192.0.2.20", "DE", "Harbor Cloud", port = 3478, udp = true),
            ),
        ),
        FixtureApp(
            "app.fernway.maps", weight = 12,
            domains = listOf(
                host("tiles.fernway.app", "198.51.100.8", "US", "Fernway"),
                host("route.fernway.app", "198.51.100.9", "US", "Fernway"),
                playmetric,
            ),
        ),
        FixtureApp(
            "weather.clearsky", weight = 9,
            domains = listOf(
                host("api.clearsky.app", "198.51.100.30", "CA", "Maple Edge"),
                brightpixelAds,
                adorbit,
                datafunnel,
            ),
        ),
        FixtureApp(
            "news.driftwood", weight = 10,
            domains = listOf(
                host("feed.driftwood.news", "198.51.100.140", "GB", "Thameside Hosting"),
                host("img.driftwood.news", "198.51.100.141", "GB", "Thameside Hosting"),
                brightpixelAds,
                tracker("pixel.readerstat.com", "203.0.113.120", "US", "Readerstat"),
                adorbit,
            ),
        ),
        FixtureApp(
            "tv.reelhouse", connection = ConnectionStatus.METERED, weight = 14,
            domains = listOf(
                host("cdn.reelhouse.tv", "198.51.100.12", "US", "Reelhouse Media"),
                host("api.reelhouse.tv", "198.51.100.13", "US", "Reelhouse Media"),
                tracker("ads.reelhouse.tv", "203.0.113.31", "US", "Reelhouse Media"),
            ),
        ),
        FixtureApp(
            "fm.cadence", connection = ConnectionStatus.METERED, weight = 11,
            domains = listOf(
                host("stream.cadence.fm", "198.51.100.64", "SE", "Nordlys Net"),
                host("api.cadence.fm", "198.51.100.65", "SE", "Nordlys Net"),
                datafunnel,
            ),
        ),
        FixtureApp(
            "bank.marlow", firewall = FirewallStatus.BYPASS_UNIVERSAL, weight = 4,
            domains = listOf(
                host("api.marlowbank.com", "192.0.2.45", "GB", "Marlow Bank"),
                host("auth.marlowbank.com", "192.0.2.46", "GB", "Marlow Bank"),
            ),
        ),
        FixtureApp(
            "mail.kestrel", weight = 7,
            domains = listOf(
                host("imap.kestrelmail.com", "192.0.2.61", "CH", "Alpenhost", port = 993),
                host("api.kestrelmail.com", "192.0.2.62", "CH", "Alpenhost"),
            ),
        ),
        FixtureApp(
            "shop.basketful", weight = 8,
            domains = listOf(
                host("api.basketful.shop", "192.0.2.99", "US", "Northwind Cloud"),
                host("img.basketful.shop", "192.0.2.100", "US", "Northwind Cloud"),
                brightpixelAds,
                playmetric,
            ),
        ),
        FixtureApp(
            "fit.stride", weight = 5,
            domains = listOf(
                host("sync.stride.fit", "192.0.2.201", "AU", "Southern Cross Data"),
                crashbeacon,
            ),
        ),
        FixtureApp(
            "photo.shutterbox", connection = ConnectionStatus.METERED, weight = 6,
            domains = listOf(
                host("upload.shutterbox.app", "192.0.2.150", "JP", "Sakura Edge"),
                host("api.shutterbox.app", "192.0.2.151", "JP", "Sakura Edge"),
            ),
        ),
        FixtureApp(
            "app.inkwell.notes", weight = 3,
            domains = listOf(host("sync.inkwell.app", "198.51.100.90", "DE", "Rheinwerk Hosting")),
        ),
        FixtureApp(
            "travel.wanderly", weight = 4,
            domains = listOf(
                host("api.wanderly.travel", "198.51.100.110", "FR", "Seine Cloud"),
                playmetric,
                adorbit,
            ),
        ),
        FixtureApp(
            "food.kettle", weight = 3,
            domains = listOf(
                host("recipes.kettle.kitchen", "198.51.100.120", "IT", "Lago Net"),
                brightpixelAds,
            ),
        ),
        FixtureApp(
            "tools.torchlight", connection = ConnectionStatus.BOTH, weight = 6,
            domains = listOf(
                tracker("ads.torchlight.tools", "203.0.113.40", "PA", "Lantern Ads"),
                adorbit,
                datafunnel,
                brightpixelAds,
            ),
        ),
        FixtureApp(
            "io.glyphkeys", connection = ConnectionStatus.BOTH, weight = 5,
            domains = listOf(
                tracker("telemetry.glyphkeys.io", "203.0.113.90", "EE", "Glyph Labs"),
                tracker("sync.glyphkeys.io", "203.0.113.91", "EE", "Glyph Labs"),
            ),
        ),
        FixtureApp(
            "games.orbittiles", connection = ConnectionStatus.BOTH, weight = 9,
            domains = listOf(
                tracker("track.orbittiles.games", "203.0.113.140", "CY", "Orbit Play"),
                playmetric,
                adorbit,
                crashbeacon,
            ),
        ),
    )
