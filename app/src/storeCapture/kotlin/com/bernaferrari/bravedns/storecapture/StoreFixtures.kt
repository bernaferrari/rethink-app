package com.bernaferrari.bravedns.storecapture

import com.bernaferrari.bravedns.service.FirewallManager.ConnectionStatus
import com.bernaferrari.bravedns.service.FirewallManager.FirewallStatus

/**
 * Store-screenshot traffic for the real Google apps that ship on the capture emulator's Play image
 * (no APK is installed for the screenshots). Hosts are the domains these apps really use; the
 * addresses and per-host networks are plausible stand-ins. Apps that are not installed are skipped.
 *
 * The story: Chrome, Gmail and Messages connect freely; YouTube, YouTube Music and Photos wait for
 * Wi-Fi; Maps works on Wi-Fi only; the Clock, Calculator and Camera, which have no reason to go
 * online, are cut off; and the DNS blocklists quietly drop the ad and analytics hosts that
 * everything else tries to reach.
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

private const val GOOGLE = "Google LLC"

private fun host(name: String, ip: String, port: Int = 443, udp: Boolean = false, country: String = "US", network: String = GOOGLE) =
    FixtureDomain(name, ip, country, network, port = port, udp = udp)

private fun tracker(name: String, ip: String) = FixtureDomain(name, ip, "US", GOOGLE, tracker = true)

// Ad and analytics hosts, shared across apps the way they are in life.
private val appMeasurement = tracker("app-measurement.com", "142.250.80.46")
private val firebaseLogging = tracker("firebaselogging-pa.googleapis.com", "142.250.65.106")
private val crashlytics = tracker("crashlyticsreports-pa.googleapis.com", "142.250.64.74")
private val doubleclick = tracker("googleads.g.doubleclick.net", "142.250.72.98")
private val syndication = tracker("pagead2.googlesyndication.com", "142.250.81.226")
private val analytics = tracker("www.google-analytics.com", "142.250.65.78")

internal val fixtureApps: List<FixtureApp> =
    listOf(
        FixtureApp(
            "com.android.chrome", weight = 18,
            domains = listOf(
                host("www.google.com", "142.250.80.100"),
                host("fonts.gstatic.com", "142.250.80.67"),
                host("www.gstatic.com", "142.250.80.35", udp = true),
                host("en.wikipedia.org", "208.80.154.224", country = "US", network = "Wikimedia Foundation"),
                doubleclick,
                syndication,
                analytics,
            ),
        ),
        FixtureApp(
            "com.google.android.gm", weight = 12,
            domains = listOf(
                host("mail.google.com", "142.250.80.37"),
                host("gmail.googleapis.com", "142.250.64.106"),
                host("lh3.googleusercontent.com", "142.250.65.97"),
                firebaseLogging,
            ),
        ),
        FixtureApp(
            "com.google.android.apps.messaging", weight = 9,
            domains = listOf(
                host("instantmessaging-pa.googleapis.com", "142.250.64.138"),
                host("messages.google.com", "142.250.80.110"),
                firebaseLogging,
            ),
        ),
        FixtureApp(
            "com.google.android.youtube", connection = ConnectionStatus.METERED, weight = 16,
            domains = listOf(
                host("youtubei.googleapis.com", "142.250.72.106"),
                host("i.ytimg.com", "142.250.81.246"),
                host("rr3---sn-q4fl6nsr.googlevideo.com", "173.194.160.104", udp = true),
                doubleclick,
                tracker("s.youtube.com", "142.250.65.110"),
            ),
        ),
        FixtureApp(
            "com.google.android.apps.youtube.music", connection = ConnectionStatus.METERED, weight = 10,
            domains = listOf(
                host("music.youtube.com", "142.250.80.14"),
                host("rr5---sn-q4flrnek.googlevideo.com", "173.194.161.42", udp = true),
                appMeasurement,
            ),
        ),
        FixtureApp(
            "com.google.android.apps.photos", connection = ConnectionStatus.METERED, weight = 8,
            domains = listOf(
                host("photos.googleapis.com", "142.250.65.74"),
                host("lh3.googleusercontent.com", "142.250.65.97"),
                crashlytics,
            ),
        ),
        FixtureApp(
            "com.google.android.apps.maps", connection = ConnectionStatus.METERED, weight = 9,
            domains = listOf(
                host("maps.googleapis.com", "142.250.72.138"),
                host("khms0.googleapis.com", "142.250.64.170"),
                host("mobilemaps-pa-gz.googleapis.com", "142.250.80.42"),
                appMeasurement,
            ),
        ),
        FixtureApp(
            "com.google.android.apps.docs", weight = 5,
            domains = listOf(
                host("drive.googleapis.com", "142.250.65.42"),
                host("docs.google.com", "142.250.80.78"),
                crashlytics,
            ),
        ),
        FixtureApp(
            "com.google.android.calendar", weight = 4,
            domains = listOf(host("calendarsync-pa.googleapis.com", "142.250.64.202"), firebaseLogging),
        ),
        FixtureApp(
            "com.android.vending", weight = 7,
            domains = listOf(
                host("play.googleapis.com", "142.250.80.74"),
                host("play-lh.googleusercontent.com", "142.250.65.65"),
                host("android.clients.google.com", "142.250.64.78"),
            ),
        ),
        FixtureApp(
            "com.google.android.googlequicksearchbox", weight = 8,
            domains = listOf(
                host("www.google.com", "142.250.80.100"),
                host("clients4.google.com", "142.250.65.110"),
                appMeasurement,
                firebaseLogging,
            ),
        ),
        FixtureApp(
            "com.google.android.dialer", firewall = FirewallStatus.BYPASS_UNIVERSAL, weight = 2,
            domains = listOf(host("dialercallerid-pa.googleapis.com", "142.250.64.234")),
        ),
        FixtureApp(
            "com.google.android.deskclock", connection = ConnectionStatus.BOTH, weight = 4,
            domains = listOf(firebaseLogging, appMeasurement, crashlytics, host("clients4.google.com", "142.250.65.110")),
        ),
        FixtureApp(
            "com.google.android.calculator", connection = ConnectionStatus.BOTH, weight = 2,
            domains = listOf(firebaseLogging, appMeasurement),
        ),
        FixtureApp(
            "com.android.camera2", connection = ConnectionStatus.BOTH, weight = 3,
            domains = listOf(firebaseLogging, crashlytics),
        ),
        FixtureApp(
            "com.google.android.GoogleCamera", connection = ConnectionStatus.BOTH, weight = 3,
            domains = listOf(firebaseLogging, crashlytics),
        ),
    )
