package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.rules.IpRuleInput
import inet.ipaddr.IPAddress

/** The direct-add callback validates before scheduling a write and preserves its optional port. */
internal fun ipRuleAddResult(
    value: String,
    invalidMessage: String,
    enqueue: (IPAddress, Int?) -> Unit,
): RethinkRulesAddResult {
    val parsed = IpRuleInput.parse(value) ?: return RethinkRulesAddResult(false, invalidMessage)
    enqueue(parsed.address, parsed.port)
    return RethinkRulesAddResult.Success
}
