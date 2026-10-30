package com.bernaferrari.bravedns.rules

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class RuleImportReaderTest {
    @Test fun overlongLinesAreDiscardedWithoutLosingFollowingRules() {
        val preview = RuleImportReader.preview(("x".repeat(100_000) + "\nexample.com\n").byteInputStream()) { it }
        assertEquals(listOf("example.com"), preview.entries)
        assertEquals(1, preview.invalidCount)
    }
    @Test(expected = IOException::class) fun oversizedDocumentFailsBeforePreviewCanBeConfirmed() {
        RuleImportReader.preview(ByteArrayInputStream(ByteArray(RuleImportParser.MAX_FILE_BYTES + 1) { 'x'.code.toByte() })) { it }
    }
}
