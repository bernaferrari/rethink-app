package com.celzero.bravedns.viewmodel

/** Shared firewall rule IDs; their values match the persisted AppInfo schema. */
enum class AppFirewallStatus(val id: Int) {
    BypassUniversal(2),
    Exclude(3),
    Isolate(4),
    None(5),
    Untracked(6),
    BypassDnsFirewall(7),
}

enum class AppConnectionStatus(val id: Int) {
    Both(0),
    Unmetered(1),
    Metered(2),
    Allow(3),
}

/** Host boundary for changing live firewall state. */
interface AppFirewallRuleMutator {
    suspend fun connectionStatus(uid: Int): AppConnectionStatus

    suspend fun updateFirewallStatus(
        uid: Int,
        firewallStatus: AppFirewallStatus,
        connectionStatus: AppConnectionStatus,
    )
}
