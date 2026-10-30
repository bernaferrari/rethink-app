package com.bernaferrari.bravedns.rules

import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.IpRuleNormalizer
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRulesTab
import inet.ipaddr.IPAddressString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object RuleImportService {
    fun normalize(tab: RethinkRulesTab, line: String): String? = when (tab) {
        RethinkRulesTab.DOMAIN -> DomainRuleInput.normalize(line)
        RethinkRulesTab.IP -> normalizeIp(line)
    }

    private fun normalizeIp(line: String): String? {
        // Keep port parsing separate from address parsing; never silently turn malformed ports into zero.
        val bracketed = line.startsWith('[')
        val explicitPort = bracketed && line.contains("]:") || line.count { it == ':' } == 1
        val addressText = when {
            bracketed -> line.substringAfter('[').substringBefore(']')
            explicitPort -> line.substringBeforeLast(':')
            else -> line
        }
        if (bracketed) {
            if (!line.contains(']')) return null
            val suffix = line.substringAfter(']')
            if (suffix.isNotEmpty() && !suffix.startsWith(':')) return null
        }
        val port = if (explicitPort) line.substringAfterLast(':').toIntOrNull()?.takeIf { it in 0..65535 } ?: return null else 0
        val normalized = IpRuleNormalizer.trieKey(addressText) ?: return null
        return "[$normalized]:$port"
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
