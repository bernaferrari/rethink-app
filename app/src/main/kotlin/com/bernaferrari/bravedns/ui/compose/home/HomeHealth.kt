/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.net.doh.Transaction

/**
 * What Home must say beside "Protected" when protection isn't doing what the hero claims. One at a
 * time, in this order: a conflict that stops Keepout from starting, then one that silently
 * overrides its DNS, then a resolver that isn't answering.
 */
enum class HomeHealthIssue { OtherAlwaysOnVpn, PrivateDnsOverride, DnsFailing }

/** Device facts Home can't observe from a flow; the Activity reads them on start and on resume. */
data class HomeEnvironment(
    /** Another app holds Android's always-on VPN, so Keepout can't start. */
    val otherAlwaysOnVpn: Boolean = false,
    /** Android's Private DNS answers lookups instead of Keepout's DNS. */
    val privateDnsActive: Boolean = false,
    /** Android 13+, notifications not granted, and the user hasn't said no. */
    val canAskForNotifications: Boolean = false,
)

/** The first-run and follow-up cards under the hero; at most one shows. */
enum class HomeCard { Protected, Notifications }

internal fun homeHealthIssue(
    environment: HomeEnvironment,
    active: Boolean,
    paused: Boolean,
    dnsFailing: Boolean,
): HomeHealthIssue? = when {
    environment.otherAlwaysOnVpn -> HomeHealthIssue.OtherAlwaysOnVpn
    // Off, Private DNS is simply Android's DNS; it only matters once Keepout should be answering.
    active && environment.privateDnsActive -> HomeHealthIssue.PrivateDnsOverride
    active && !paused && dnsFailing -> HomeHealthIssue.DnsFailing
    else -> null
}

/**
 * The success card waits for protection to be on after Welcome's "Turn on protection"; the
 * notification ask comes only after a successful start, never in the same moment as VPN consent.
 */
internal fun homeCard(
    environment: HomeEnvironment,
    protectedOn: Boolean,
    protectedCardArmed: Boolean,
): HomeCard? = when {
    !protectedOn -> null
    protectedCardArmed -> HomeCard.Protected
    environment.canAskForNotifications -> HomeCard.Notifications
    else -> null
}

/**
 * Whether the preferred resolver is down, so lookups fall back. Null is how the tunnel says the
 * preferred transport is missing ("using fallback DNS"). A send failure means no internet, which
 * the hero already reports; a bad query is one lookup's fault, not the server's.
 */
internal fun isDnsFailing(status: Int?): Boolean {
    if (status == null) return true
    return when (Transaction.Status.fromId(status)) {
        Transaction.Status.START,
        Transaction.Status.COMPLETE,
        Transaction.Status.SEND_FAIL,
        Transaction.Status.BAD_QUERY -> false
        Transaction.Status.TRANSPORT_ERROR,
        Transaction.Status.NO_RESPONSE,
        Transaction.Status.BAD_RESPONSE,
        Transaction.Status.CLIENT_ERROR,
        Transaction.Status.INTERNAL_ERROR -> true
    }
}

private const val RETHINK_PRESET_PREFIX = "RDNS "

/**
 * The hero's DNS line: "Keepout · Default" for Keepout DNS (the stored "RDNS <preset>" names, with
 * [presetNames] renaming presets by their lowercase stored name), the provider's own name
 * otherwise. Stored names are untouched.
 */
internal fun homeDnsLabel(
    connectedDnsName: String,
    keepoutDns: Boolean,
    keepoutName: String,
    presetNames: Map<String, String> = emptyMap(),
): String {
    val name = connectedDnsName.trim()
    if (!keepoutDns) return name
    val preset = name.removePrefix(RETHINK_PRESET_PREFIX).trim()
    if (preset.isEmpty() || preset.equals("rdns", ignoreCase = true)) return keepoutName
    return "$keepoutName · ${presetNames[preset.lowercase()] ?: preset}"
}
