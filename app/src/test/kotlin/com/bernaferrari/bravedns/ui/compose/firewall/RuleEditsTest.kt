package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.database.CustomDomain
import com.bernaferrari.bravedns.database.CustomIp
import com.bernaferrari.bravedns.service.DomainRulesManager
import com.bernaferrari.bravedns.service.IpRulesManager.IpRuleStatus
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFirewallRuleStatus
import com.bernaferrari.bravedns.util.Constants.Companion.UID_EVERYBODY
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEditsTest {
    /** Behaves like the managers: they mutate the row they're given and stamp it with "now". */
    private class FakeStore : RuleEditStore {
        var now = 1_000L
        val ips = mutableMapOf<Triple<Int, String, Int>, CustomIp>()
        val domains = mutableMapOf<Pair<Int, String>, CustomDomain>()
        val ipWrites = mutableListOf<IpRuleStatus>()

        override suspend fun ip(uid: Int, address: String, port: Int) = ips[Triple(uid, address, port)]?.deepCopy()
        override suspend fun setIpStatus(row: CustomIp, status: IpRuleStatus) {
            row.status = status.id
            row.modifiedDateTime = ++now
            ips[Triple(row.uid, row.ipAddress, row.port)] = row.deepCopy()
            ipWrites += status
        }
        override suspend fun removeIp(row: CustomIp) { ips.remove(Triple(row.uid, row.ipAddress, row.port)) }
        override suspend fun addIp(row: CustomIp): String {
            row.modifiedDateTime = ++now
            ips[Triple(row.uid, row.ipAddress, row.port)] = row.deepCopy()
            return row.ipAddress
        }
        override suspend fun setIpModified(uid: Int, address: String, port: Int, modified: Long) {
            ips[Triple(uid, address, port)]?.modifiedDateTime = modified
        }
        override suspend fun domain(uid: Int, domain: String) = domains[uid to domain]?.copyOf()
        override suspend fun setDomainStatus(row: CustomDomain, status: DomainRulesManager.Status) {
            row.status = status.id
            row.modifiedTs = ++now
            domains[row.uid to row.domain] = row.copyOf()
        }
        override suspend fun deleteDomain(row: CustomDomain) { domains.remove(row.uid to row.domain) }
        override suspend fun setDomainModified(uid: Int, domain: String, modified: Long) {
            domains[uid to domain]?.modifiedTs = modified
        }

        fun putIp(uid: Int, address: String, port: Int, status: IpRuleStatus, proxyId: String = "") = CustomIp().also {
            it.uid = uid; it.ipAddress = address; it.port = port; it.status = status.id
            it.proxyId = proxyId; it.proxyCC = "US"; it.modifiedDateTime = 42L
            ips[Triple(uid, address, port)] = it
        }

        fun putDomain(uid: Int, domain: String, status: DomainRulesManager.Status) =
            CustomDomain(domain, uid, "1.2.3.4", DomainRulesManager.DomainType.WILDCARD.id, status.id, "wg1", "DE", 77L, 0L, 1L)
                .also { domains[uid to domain] = it }
    }

    private fun ipRule(uid: Int, address: String, port: Int?, status: RethinkFirewallRuleStatus) =
        RethinkFirewallRule("ip", uid, address, status, RethinkRulesTab.IP, port)

    private fun domainRule(uid: Int, domain: String, status: RethinkFirewallRuleStatus) =
        RethinkFirewallRule("d", uid, domain, status, RethinkRulesTab.DOMAIN)

    @Test fun allAppsTrustIpWritesBypassAndUndoRestoresTheBlock() = runBlocking<Unit> {
        val store = FakeStore()
        store.putIp(UID_EVERYBODY, "1.2.3.4", 0, IpRuleStatus.BLOCK, proxyId = "wg0")
        val undo = applyRuleEdit(store, ipRule(UID_EVERYBODY, "1.2.3.4", null, RethinkFirewallRuleStatus.Block),
            RethinkRuleEdit.SetAction(RethinkRuleAction.Trust))
        val changed = store.ips.getValue(Triple(UID_EVERYBODY, "1.2.3.4", 0))
        assertEquals(IpRuleStatus.BYPASS_UNIVERSAL.id, changed.status)
        // In place: the row keeps its spot in the modified-time ordering.
        assertEquals(42L, changed.modifiedDateTime)

        assertNotNull(undo); undo!!()
        val restored = store.ips.getValue(Triple(UID_EVERYBODY, "1.2.3.4", 0))
        assertEquals(IpRuleStatus.BLOCK.id, restored.status)
        assertEquals("wg0", restored.proxyId)
        assertEquals(42L, restored.modifiedDateTime)
    }

    @Test fun perAppTrustIpWritesTrust() = runBlocking<Unit> {
        val store = FakeStore()
        store.putIp(10123, "10.0.0.1", 443, IpRuleStatus.BLOCK)
        applyRuleEdit(store, ipRule(10123, "10.0.0.1", 443, RethinkFirewallRuleStatus.Block), RethinkRuleEdit.SetAction(RethinkRuleAction.Trust))
        assertEquals(listOf(IpRuleStatus.TRUST), store.ipWrites)
    }

    @Test fun choosingTheCurrentActionWritesNothingAndOffersNoUndo() = runBlocking<Unit> {
        val store = FakeStore()
        store.putIp(UID_EVERYBODY, "1.2.3.4", 0, IpRuleStatus.BYPASS_UNIVERSAL)
        val undo = applyRuleEdit(store, ipRule(UID_EVERYBODY, "1.2.3.4", null, RethinkFirewallRuleStatus.Bypass),
            RethinkRuleEdit.SetAction(RethinkRuleAction.Trust))
        assertNull(undo)
        assertEquals(emptyList<IpRuleStatus>(), store.ipWrites)
    }

    @Test fun aRuleThatIsGoneIsNotRecreated() = runBlocking<Unit> {
        val store = FakeStore()
        assertNull(applyRuleEdit(store, domainRule(UID_EVERYBODY, "gone.com", RethinkFirewallRuleStatus.Block), RethinkRuleEdit.Delete))
        assertEquals(0, store.domains.size)
    }

    @Test fun deletedIpComesBackWithStatusProxyAndPlace() = runBlocking<Unit> {
        val store = FakeStore()
        store.putIp(10123, "10.0.0.1", 443, IpRuleStatus.TRUST, proxyId = "wg0")
        val undo = applyRuleEdit(store, ipRule(10123, "10.0.0.1", 443, RethinkFirewallRuleStatus.Trust), RethinkRuleEdit.Delete)
        assertNull(store.ips[Triple(10123, "10.0.0.1", 443)])
        undo!!()
        val restored = store.ips.getValue(Triple(10123, "10.0.0.1", 443))
        assertEquals(IpRuleStatus.TRUST.id, restored.status)
        assertEquals("wg0", restored.proxyId)
        assertEquals("US", restored.proxyCC)
        assertEquals(42L, restored.modifiedDateTime)
    }

    @Test fun domainChangeAndUndoKeepEveryField() = runBlocking<Unit> {
        val store = FakeStore()
        store.putDomain(10123, "*.ads.com", DomainRulesManager.Status.TRUST)
        val undo = applyRuleEdit(store, domainRule(10123, "*.ads.com", RethinkFirewallRuleStatus.Trust), RethinkRuleEdit.SetAction(RethinkRuleAction.Block))
        val changed = store.domains.getValue(10123 to "*.ads.com")
        assertEquals(DomainRulesManager.Status.BLOCK.id, changed.status)
        assertEquals(77L, changed.modifiedTs)
        undo!!()
        store.domains.getValue(10123 to "*.ads.com").let {
            assertEquals(DomainRulesManager.Status.TRUST.id, it.status)
            assertEquals("wg1", it.proxyId)
            assertEquals("DE", it.proxyCC)
            assertEquals("1.2.3.4", it.ips)
            assertEquals(DomainRulesManager.DomainType.WILDCARD.id, it.type)
            assertEquals(77L, it.modifiedTs)
        }
    }

    @Test fun deletedDomainComesBackOnUndo() = runBlocking<Unit> {
        val store = FakeStore()
        store.putDomain(UID_EVERYBODY, "tracker.com", DomainRulesManager.Status.BLOCK)
        val undo = applyRuleEdit(store, domainRule(UID_EVERYBODY, "tracker.com", RethinkFirewallRuleStatus.Block), RethinkRuleEdit.Delete)
        assertNull(store.domains[UID_EVERYBODY to "tracker.com"])
        undo!!()
        val restored = store.domains.getValue(UID_EVERYBODY to "tracker.com")
        assertEquals(DomainRulesManager.Status.BLOCK.id, restored.status)
        assertEquals("wg1", restored.proxyId)
        assertEquals(77L, restored.modifiedTs)
    }
}
