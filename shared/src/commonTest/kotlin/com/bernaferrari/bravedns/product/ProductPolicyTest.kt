package com.bernaferrari.bravedns.product

import com.bernaferrari.bravedns.rules.RuleImportParser
import com.bernaferrari.bravedns.ui.compose.firewall.*
import com.bernaferrari.bravedns.util.LogRetention
import kotlin.test.*

class ProductPolicyTest {
    @Test fun importPreviewRejectsInvalidAndDuplicateEntriesAndBoundsInput() {
        val preview = RuleImportParser.preview(sequenceOf("# comment", "example.com", "EXAMPLE.COM", "bad input", "x".repeat(4097))) {
            it.lowercase().takeIf { value -> '.' in value && ' ' !in value }
        }
        assertEquals(listOf("example.com"), preview.entries)
        assertEquals(1, preview.duplicateCount)
        assertEquals(2, preview.invalidCount)
        val bounded = RuleImportParser.preview((1..10_001).asSequence().map { "$it.example" }) { it }
        assertEquals(10_000, bounded.entries.size)
        assertTrue(bounded.limitReached)
    }
    @Test fun retentionHasExactCutoffsAndWorkManagerSafeCadence() {
        assertEquals(3_600_000L, LogRetention.OneHour.durationMillis)
        assertEquals(15L, LogRetention.OneHour.purgeIntervalMinutes)
        assertEquals(240L, LogRetention.SevenDays.purgeIntervalMinutes)
        assertEquals(LogRetention.SevenDays, LogRetention.fromHours(-1))
    }
    @Test fun uidSortIsNumericAndPackageAndNameSortHaveStableTies() {
        val apps = listOf(app("a",100,"Zebra","a.pkg"), app("b",20,"Alpha","z.pkg"), app("c",20,"Alpha","b.pkg"))
        assertEquals(listOf("c","b","a"), orderFirewallApps(apps,RethinkAppSort.Uid).map { it.id })
        assertEquals(listOf("a","c","b"), orderFirewallApps(apps,RethinkAppSort.Package).map { it.id })
        assertEquals(listOf("c","b","a"), orderFirewallApps(apps,RethinkAppSort.Name).map { it.id })
    }
    private fun app(id:String,uid:Int,name:String,pkg:String) = RethinkFirewallApp(id,uid,name,pkg,RethinkFirewallAppStatus.Allowed,false,false)
}
