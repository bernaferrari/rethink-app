/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

/**
 * The three answers to "who answers your lookups", plus Smart DNS, which still works but lives in
 * DNS › Advanced ("Automatic server selection"). Derived from the stored DNS type; never written.
 */
enum class RethinkDnsChoice { Rethink, Provider, Network, Automatic }

fun RethinkDnsSettingsState.dnsChoice(): RethinkDnsChoice = when {
    isSmartDnsEnabled -> RethinkDnsChoice.Automatic
    isSystemDnsEnabled -> RethinkDnsChoice.Network
    isRethinkDnsConnected -> RethinkDnsChoice.Rethink
    else -> RethinkDnsChoice.Provider
}

/**
 * Display names for the stock Rethink DNS endpoints. The stored names ("RDNS Adult", "RDNS Plus")
 * and their URLs never change; only the label shown to people does.
 */
enum class RethinkDnsPreset(val storedName: String) {
    Default("RDNS Default"),
    Family("RDNS Adult"),
    Piracy("RDNS Piracy"),
    SocialMedia("RDNS Social Media"),
    Security("RDNS Security"),
    Privacy("RDNS Privacy"),
    Custom("RDNS Plus");

    companion object {
        /** The preset for a stored endpoint name, or null for app-specific or user endpoints. */
        fun fromStoredName(name: String): RethinkDnsPreset? {
            val trimmed = name.trim()
            return entries.firstOrNull { it.storedName.equals(trimmed, ignoreCase = true) }
        }
    }
}

/** The endpoint name part of `PersistentState.connectedDnsName` ("RDNS Default, https://…"). */
fun connectedDnsDisplayName(connectedDnsName: String): String =
    connectedDnsName.substringBefore(',').trim()

/** The endpoint part of `PersistentState.connectedDnsName`, or blank when there is none. */
fun connectedDnsEndpoint(connectedDnsName: String): String =
    connectedDnsName.substringAfter(',', "").trim()

/**
 * Settings-search focus keys that live on DNS › Advanced rather than on the main DNS screen, so a
 * search result can open the screen that actually shows the row.
 */
fun isDnsAdvancedFocusKey(key: String?): Boolean {
    if (key.isNullOrBlank()) return false
    if (key in DNS_MAIN_FOCUS_KEYS) return false
    return key.startsWith("dns_")
}

private val DNS_MAIN_FOCUS_KEYS = setOf(
    "dns_mode", "dns_mode_system", "dns_mode_custom", "dns_mode_rethink",
    "dns_blocklist", "dns_block_local",
)

/** The provider a server belongs to: its name's first word, so "Cloudflare Family" joins "Cloudflare". */
fun dnsProviderKey(serverName: String): String =
    serverName.trim().split(' ', '-', '_').firstOrNull { it.isNotBlank() }
        ?.filter(Char::isLetterOrDigit)?.lowercase().orEmpty()

/** How a provider is written, whatever case the server names use ("Adguard" → "AdGuard"). */
fun dnsProviderDisplayName(serverName: String): String {
    val key = dnsProviderKey(serverName)
    KNOWN_PROVIDERS[key]?.let { return it }
    val first = serverName.trim().split(' ').firstOrNull { it.isNotBlank() }.orEmpty()
    return first.replaceFirstChar { it.uppercaseChar() }
}

private val KNOWN_PROVIDERS = mapOf(
    "adguard" to "AdGuard",
    "cleanbrowsing" to "CleanBrowsing",
    "cloudflare" to "Cloudflare",
    "google" to "Google",
    "mullvad" to "Mullvad",
    "nextdns" to "NextDNS",
    "quad9" to "Quad9",
    "opendns" to "OpenDNS",
    "controld" to "Control D",
)

/**
 * Keeps the order a list had when the screen opened: keys already shown stay where they are, new
 * keys go at the end, removed ones drop out. Picking an item never moves it under the finger; the
 * in-use item sorts first only the next time the list opens.
 */
fun <K> stableOrder(previous: List<K>, current: List<K>): List<K> {
    if (previous.isEmpty()) return current
    val present = current.toHashSet()
    val kept = previous.filter { it in present }
    val keptSet = kept.toHashSet()
    return kept + current.filter { it !in keptSet }
}
