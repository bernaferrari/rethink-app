package com.bernaferrari.bravedns.rules

import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRulesTab
import inet.ipaddr.IPAddressString

object RuleImportService {
    fun normalize(tab: RethinkRulesTab, line: String): String? = when (tab) {
        RethinkRulesTab.DOMAIN -> DomainRuleInput.normalize(line)
        RethinkRulesTab.IP -> IpRuleInput.parse(line)?.importKey
    }

    suspend fun import(uid: Int, tab: RethinkRulesTab, entries: List<String>): Pair<Int, Int> {
        val importer = ConfirmedRuleImporter(
            exists = { entry -> when (tab) {
                RethinkRulesTab.DOMAIN -> DomainRulesManager.getObj(uid, entry) != null
                RethinkRulesTab.IP -> IpRulesManager.getObj(uid, entry.substringAfter('[').substringBefore(']'), entry.substringAfterLast(':').toInt()) != null
            } },
            insertBlock = { entry ->
                when (tab) {
                    RethinkRulesTab.DOMAIN -> DomainRulesManager.addDomainRule(entry, DomainRulesManager.Status.BLOCK,
                        if (entry.startsWith("*.")) DomainRulesManager.DomainType.WILDCARD else DomainRulesManager.DomainType.DOMAIN, uid)
                    RethinkRulesTab.IP -> IpRulesManager.addIpRule(uid,
                        requireNotNull(IPAddressString(entry.substringAfter('[').substringBefore(']')).address),
                        entry.substringAfterLast(':').toInt(), IpRulesManager.IpRuleStatus.BLOCK, "", "")
                }
            },
        )
        return importer.import(entries)
    }
}
