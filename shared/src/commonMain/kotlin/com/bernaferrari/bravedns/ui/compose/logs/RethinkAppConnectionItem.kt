package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus

/** Target-neutral presentation state for one app-wise connection entry. */
data class RethinkAppConnectionItem(
    val flag: String,
    val title: String,
    val supporting: String? = null,
    val count: String,
    val ruleState: RethinkFirewallRuleStatus = RethinkFirewallRuleStatus.None,
    /** Null omits the lightweight activity indicator. */
    val activity: Float? = null,
)

