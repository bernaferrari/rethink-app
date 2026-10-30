package com.bernaferrari.bravedns.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExceptionParserTest {
    @Test fun `java crash points at captured caller rather than reporter`() {
        val parsed = ExceptionParser.parse("java.lang.IllegalStateException: failed\n\tat example.Work.run(Work.kt:42)")
        assertEquals("example.Work", parsed.stackTrace.single().className)
        assertEquals(42, parsed.stackTrace.single().lineNumber)
    }

    @Test fun `Go dump includes only first goroutine and not background workers`() {
        val parsed = ExceptionParser.parse("""
            panic: failed
            goroutine 7 [running]:
            example.Run(0x123)
                /app/run.go:21 +0x22
            goroutine 8 [sleep]:
            example.Background()
                /app/other.go:88 +0x12
        """.trimIndent())
        assertEquals("example.Run", parsed.stackTrace.single().methodName)
        assertEquals(21, parsed.stackTrace.single().lineNumber)
    }

    @Test fun `unrecognized dump never invents report-site frames`() {
        assertEquals(0, ExceptionParser.parse("unrecognized dump").stackTrace.size)
    }
}
