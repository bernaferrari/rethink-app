/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.database.CustomDomain
import com.bernaferrari.bravedns.database.CustomDomainRepository
import com.bernaferrari.bravedns.database.CustomIp
import com.bernaferrari.bravedns.database.CustomIpRepository
import com.bernaferrari.bravedns.database.getCustomIpAddress
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager.IpRuleStatus
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.bernaferrari.bravedns.util.Constants.Companion.UNSPECIFIED_PORT
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Where a rule edit reads and writes. Status changes, deletes and re-adds go through the firewall
 * managers (DB row and trie together); [setIpModified] / [setDomainModified] only put a row back in
 * its place in the list's modified-time ordering, which the managers bump on every write.
 */
internal interface RuleEditStore {
    suspend fun ip(uid: Int, address: String, port: Int): CustomIp?
    suspend fun setIpStatus(row: CustomIp, status: IpRuleStatus)
    suspend fun removeIp(row: CustomIp)

    /** Re-adds a removed row with its status and proxy; returns the stored address, or null if unparseable. */
    suspend fun addIp(row: CustomIp): String?
    suspend fun setIpModified(uid: Int, address: String, port: Int, modified: Long)

    suspend fun domain(uid: Int, domain: String): CustomDomain?

    /** Writes [row] (insert or replace, so it also re-adds a deleted rule) with [status]. */
    suspend fun setDomainStatus(row: CustomDomain, status: DomainRulesManager.Status)
    suspend fun deleteDomain(row: CustomDomain)
    suspend fun setDomainModified(uid: Int, domain: String, modified: Long)
}

/** The IP status a Block or Trust choice writes: an all-apps Trust IP is the bypass status. */
internal fun ipStatusFor(action: RethinkRuleAction, uid: Int): IpRuleStatus = when (action) {
    RethinkRuleAction.Block -> IpRuleStatus.BLOCK
    RethinkRuleAction.Trust, RethinkRuleAction.Bypass -> if (uid == UID_EVERYBODY) IpRuleStatus.BYPASS_UNIVERSAL else IpRuleStatus.TRUST
    RethinkRuleAction.None -> IpRuleStatus.NONE
}

internal fun domainStatusFor(action: RethinkRuleAction): DomainRulesManager.Status = when (action) {
    RethinkRuleAction.Block -> DomainRulesManager.Status.BLOCK
    RethinkRuleAction.Trust -> DomainRulesManager.Status.TRUST
    RethinkRuleAction.None, RethinkRuleAction.Bypass -> DomainRulesManager.Status.NONE
}

/**
 * Applies [edit] to the stored rule behind [rule] and returns how to undo it, or null when nothing
 * changed (the rule is gone, or already has that status). The rule keeps its place in the list,
 * and undo restores the exact previous row: status, proxy settings and modified time.
 */
internal suspend fun applyRuleEdit(store: RuleEditStore, rule: RethinkFirewallRule, edit: RethinkRuleEdit): (suspend () -> Unit)? =
    when (rule.tab) {
        RethinkRulesTab.IP -> applyIpEdit(store, rule.uid, rule.value, rule.port ?: UNSPECIFIED_PORT, edit)
        RethinkRulesTab.DOMAIN -> applyDomainEdit(store, rule.uid, rule.value, edit)
    }

private suspend fun applyIpEdit(store: RuleEditStore, uid: Int, address: String, port: Int, edit: RethinkRuleEdit): (suspend () -> Unit)? {
    val row = store.ip(uid, address, port) ?: return null
    // The managers mutate the row they're given; keep the previous one apart.
    val previous = row.deepCopy()
    when (edit) {
        is RethinkRuleEdit.SetAction -> {
            val status = ipStatusFor(edit.action, uid)
            if (row.status == status.id) return null
            store.setIpStatus(row, status)
            store.setIpModified(uid, address, port, previous.modifiedDateTime)
        }
        RethinkRuleEdit.Delete -> store.removeIp(row)
    }
    return { restoreIp(store, previous) }
}

private suspend fun restoreIp(store: RuleEditStore, previous: CustomIp) {
    val current = store.ip(previous.uid, previous.ipAddress, previous.port)
    val address = if (current != null) {
        if (current.status != previous.status) store.setIpStatus(current, IpRuleStatus.getStatus(previous.status))
        previous.ipAddress
    } else {
        store.addIp(previous.deepCopy()) ?: return
    }
    store.setIpModified(previous.uid, address, previous.port, previous.modifiedDateTime)
}

private suspend fun applyDomainEdit(store: RuleEditStore, uid: Int, domain: String, edit: RethinkRuleEdit): (suspend () -> Unit)? {
    val row = store.domain(uid, domain) ?: return null
    val previous = row.copyOf()
    when (edit) {
        is RethinkRuleEdit.SetAction -> {
            val status = domainStatusFor(edit.action)
            if (row.status == status.id) return null
            store.setDomainStatus(row, status)
            store.setDomainModified(uid, domain, previous.modifiedTs)
        }
        RethinkRuleEdit.Delete -> store.deleteDomain(row)
    }
    return {
        // Insert-or-replace of the previous row: it comes back whether changed or deleted since.
        store.setDomainStatus(previous.copyOf(), DomainRulesManager.Status.getStatus(previous.status))
        store.setDomainModified(previous.uid, previous.domain, previous.modifiedTs)
    }
}

internal fun CustomDomain.copyOf(): CustomDomain =
    CustomDomain(domain, uid, ips, type, status, proxyId, proxyCC, modifiedTs, deletedTs, version)

/** [RuleEditStore] on the firewall managers. */
internal object ManagerRuleEditStore : RuleEditStore, KoinComponent {
    private val ipDb by inject<CustomIpRepository>()
    private val domainDb by inject<CustomDomainRepository>()

    override suspend fun ip(uid: Int, address: String, port: Int): CustomIp? = IpRulesManager.getObj(uid, address, port)

    override suspend fun setIpStatus(row: CustomIp, status: IpRuleStatus) {
        when (status) {
            IpRuleStatus.BLOCK -> IpRulesManager.updateBlock(row)
            IpRuleStatus.TRUST -> IpRulesManager.updateTrust(row)
            IpRuleStatus.BYPASS_UNIVERSAL -> IpRulesManager.updateBypass(row)
            IpRuleStatus.NONE -> IpRulesManager.updateNoRule(row)
        }
    }

    override suspend fun removeIp(row: CustomIp) = IpRulesManager.removeIpRule(row.uid, row.ipAddress, row.port)

    override suspend fun addIp(row: CustomIp): String? {
        val address = row.getCustomIpAddress()?.first ?: return null
        val port = row.port.takeUnless { it == UNSPECIFIED_PORT }
        return IpRulesManager.addIpRule(row.uid, address, port, IpRuleStatus.getStatus(row.status), row.proxyId, row.proxyCC).ipAddress
    }

    override suspend fun setIpModified(uid: Int, address: String, port: Int, modified: Long) =
        ipDb.setModifiedTime(uid, address, port, modified)

    override suspend fun domain(uid: Int, domain: String): CustomDomain? = DomainRulesManager.getObj(uid, domain)

    override suspend fun setDomainStatus(row: CustomDomain, status: DomainRulesManager.Status) {
        when (status) {
            DomainRulesManager.Status.BLOCK -> DomainRulesManager.block(row)
            DomainRulesManager.Status.TRUST -> DomainRulesManager.trust(row)
            DomainRulesManager.Status.NONE -> DomainRulesManager.noRule(row)
        }
    }

    override suspend fun deleteDomain(row: CustomDomain) = DomainRulesManager.deleteDomain(row)

    override suspend fun setDomainModified(uid: Int, domain: String, modified: Long) =
        domainDb.setModifiedTime(uid, domain, modified)
}
