package com.bernaferrari.bravedns.ui.compose.apps

import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.viewmodel.AppConnectionStatus
import com.bernaferrari.bravedns.viewmodel.AppFirewallRuleMutator
import com.bernaferrari.bravedns.viewmodel.AppFirewallStatus

/** Android adapter from portable app rules to the live VPN firewall. */
object AndroidAppFirewallRuleMutator : AppFirewallRuleMutator {
    override suspend fun connectionStatus(uid: Int): AppConnectionStatus =
        FirewallManager.connectionStatus(uid).toShared()

    override suspend fun updateFirewallStatus(
        uid: Int,
        firewallStatus: AppFirewallStatus,
        connectionStatus: AppConnectionStatus,
    ) {
        FirewallManager.updateFirewallStatus(uid, firewallStatus.toAndroid(), connectionStatus.toAndroid())
    }
}

private fun FirewallManager.FirewallStatus.toShared(): AppFirewallStatus = when (this) {
    FirewallManager.FirewallStatus.BYPASS_UNIVERSAL -> AppFirewallStatus.BypassUniversal
    FirewallManager.FirewallStatus.EXCLUDE -> AppFirewallStatus.Exclude
    FirewallManager.FirewallStatus.ISOLATE -> AppFirewallStatus.Isolate
    FirewallManager.FirewallStatus.NONE -> AppFirewallStatus.None
    FirewallManager.FirewallStatus.UNTRACKED -> AppFirewallStatus.Untracked
    FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL -> AppFirewallStatus.BypassDnsFirewall
}

private fun AppFirewallStatus.toAndroid(): FirewallManager.FirewallStatus = when (this) {
    AppFirewallStatus.BypassUniversal -> FirewallManager.FirewallStatus.BYPASS_UNIVERSAL
    AppFirewallStatus.Exclude -> FirewallManager.FirewallStatus.EXCLUDE
    AppFirewallStatus.Isolate -> FirewallManager.FirewallStatus.ISOLATE
    AppFirewallStatus.None -> FirewallManager.FirewallStatus.NONE
    AppFirewallStatus.Untracked -> FirewallManager.FirewallStatus.UNTRACKED
    AppFirewallStatus.BypassDnsFirewall -> FirewallManager.FirewallStatus.BYPASS_DNS_FIREWALL
}

private fun FirewallManager.ConnectionStatus.toShared(): AppConnectionStatus = when (this) {
    FirewallManager.ConnectionStatus.BOTH -> AppConnectionStatus.Both
    FirewallManager.ConnectionStatus.UNMETERED -> AppConnectionStatus.Unmetered
    FirewallManager.ConnectionStatus.METERED -> AppConnectionStatus.Metered
    FirewallManager.ConnectionStatus.ALLOW -> AppConnectionStatus.Allow
}

private fun AppConnectionStatus.toAndroid(): FirewallManager.ConnectionStatus = when (this) {
    AppConnectionStatus.Both -> FirewallManager.ConnectionStatus.BOTH
    AppConnectionStatus.Unmetered -> FirewallManager.ConnectionStatus.UNMETERED
    AppConnectionStatus.Metered -> FirewallManager.ConnectionStatus.METERED
    AppConnectionStatus.Allow -> FirewallManager.ConnectionStatus.ALLOW
}
