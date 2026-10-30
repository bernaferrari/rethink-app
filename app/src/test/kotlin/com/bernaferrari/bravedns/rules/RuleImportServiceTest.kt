package com.bernaferrari.bravedns.rules

import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRulesTab
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.Locale

class RuleImportServiceTest {
    @Test fun previewUsesRealDomainValidationAndCanonicalization() {
        val prior = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val preview = RuleImportParser.preview(sequenceOf(
                "not a domain", "https://WWW.EXAMPLE.com/path", "example.com", "*.EU", "bad..example", "-bad.example", "https://*.example.com")) {
                RuleImportService.normalize(RethinkRulesTab.DOMAIN, it)
            }
            assertEquals(listOf("example.com", "*.eu"), preview.entries)
            assertEquals(1, preview.duplicateCount)
            assertEquals(4, preview.invalidCount)
        } finally { Locale.setDefault(prior) }
    }
    @Test fun ipPreviewRejectsMalformedPortsAndRangesOutsideTheNativeTrieContract() {
        assertEquals("[192.0.2.1]:443", RuleImportService.normalize(RethinkRulesTab.IP, "192.0.2.1:443"))
        assertEquals("[2001:db8::1]:53", RuleImportService.normalize(RethinkRulesTab.IP, "[2001:db8::1]:53"))
        assertNull(RuleImportService.normalize(RethinkRulesTab.IP, "192.0.2.1:not-a-port"))
        assertNull(RuleImportService.normalize(RethinkRulesTab.IP, "192.0.2.1:65536"))
        assertNull(RuleImportService.normalize(RethinkRulesTab.IP, "[192.0.2.1]extra"))
        assertNull(RuleImportService.normalize(RethinkRulesTab.IP, "192.0.2.1-7"))
    }
    @Test fun confirmedImportPreservesExistingRulesAndRetrySkipsPartialSuccess() = runTest {
        val rules = mutableMapOf("trusted.example" to "trust")
        var failSecond = true
        val importer = ConfirmedRuleImporter(exists = { it in rules }, insertBlock = {
            if (it == "second.example" && failSecond) throw IOException("disk unavailable")
            rules[it] = "block"
        })
        val entries = listOf("trusted.example", "first.example", "second.example")
        try { importer.import(entries); fail("Expected disk failure") } catch (_: IOException) { }
        assertEquals("trust", rules["trusted.example"])
        assertEquals("block", rules["first.example"])
        failSecond = false
        assertEquals(1 to 2, importer.import(entries))
        assertEquals(0 to 3, importer.import(entries))
        assertEquals("trust", rules["trusted.example"])
    }
}
