package com.bernaferrari.bravedns.ui.compose.apps

/**
 * The small, portable slice of installed-app metadata shared UI needs.
 *
 * Package discovery and icons remain host responsibilities. Android resolves the real catalog
 * from the firewall's tracked apps; the browser demo uses the stable entries below.
 */
data class AppCatalogEntry(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val category: String = "",
    val isSystemApp: Boolean = false,
)

interface AppCatalog {
    suspend fun appsForUid(uid: Int): List<AppCatalogEntry>
}

class InMemoryAppCatalog(entries: Collection<AppCatalogEntry>) : AppCatalog {
    private val entriesByUid = entries.groupBy { it.uid }

    override suspend fun appsForUid(uid: Int): List<AppCatalogEntry> = entriesByUid[uid].orEmpty()
}

private val webDemoEntries = listOf(
    AppCatalogEntry(1001, "com.demo.browser", "Browser", "Communication"),
    AppCatalogEntry(1002, "com.demo.calendar", "Calendar", "Productivity"),
    AppCatalogEntry(1003, "com.demo.github", "GitHub", "Productivity"),
    AppCatalogEntry(1004, "com.demo.messenger", "Messenger", "Social"),
    AppCatalogEntry(1005, "com.demo.reader", "Reader", "Productivity"),
    AppCatalogEntry(1006, "android.systemui", "System UI", "System", isSystemApp = true),
    AppCatalogEntry(1007, "com.demo.weather", "Weather", "Communication"),
    AppCatalogEntry(1008, "com.google.android.gm", "Gmail", "Communication"),
    AppCatalogEntry(1009, "com.spotify.music", "Spotify", "Media"),
    AppCatalogEntry(1010, "com.instagram.android", "Instagram", "Social"),
    AppCatalogEntry(1011, "com.google.android.youtube", "YouTube", "Media"),
    AppCatalogEntry(1012, "com.twitter.android", "X", "Social"),
    AppCatalogEntry(1013, "com.facebook.katana", "Facebook", "Social"),
    AppCatalogEntry(1014, "com.google.android.apps.maps", "Maps", "Travel"),
    AppCatalogEntry(1015, "com.amazon.mShop.android.shopping", "Amazon Shopping", "Shopping"),
    AppCatalogEntry(1016, "com.netflix.mediaclient", "Netflix", "Media"),
    AppCatalogEntry(1017, "com.discord", "Discord", "Social"),
    AppCatalogEntry(1018, "com.android.vending", "Play Store", "System"),
    AppCatalogEntry(1019, "com.google.android.gms", "Google Play services", "System", isSystemApp = true),
    AppCatalogEntry(1020, "com.reddit.frontpage", "Reddit", "Social"),
    AppCatalogEntry(1021, "org.telegram.messenger", "Telegram", "Communication"),
    AppCatalogEntry(1022, "com.zhiliaoapp.musically", "TikTok", "Social"),
    AppCatalogEntry(1023, "com.Slack", "Slack", "Productivity"),
    AppCatalogEntry(1024, "com.linkedin.android", "LinkedIn", "Social"),
    AppCatalogEntry(1025, "org.thoughtcrime.securesms", "Signal", "Communication"),
    AppCatalogEntry(1026, "com.google.android.apps.photos", "Photos", "Productivity"),
    AppCatalogEntry(1027, "com.google.android.apps.docs", "Google Drive", "Productivity"),
    AppCatalogEntry(1028, "com.ubercab", "Uber", "Travel"),
    AppCatalogEntry(1029, "com.rethinkdns.app", "RethinkDNS", "System", isSystemApp = true),
)

/** Stable catalog used by the web demo and common UI previews. */
object RethinkWebDemoAppCatalog : AppCatalog by InMemoryAppCatalog(webDemoEntries) {
    val entries = webDemoEntries
}
