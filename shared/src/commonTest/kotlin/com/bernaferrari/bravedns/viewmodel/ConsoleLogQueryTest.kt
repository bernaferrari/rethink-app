package com.bernaferrari.bravedns.viewmodel

import com.bernaferrari.bravedns.database.ConsoleLog
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsoleLogQueryTest {
    @Test fun severityAndLiteralCaseInsensitiveSearchCompose() {
        val query = ConsoleLogQuery("100%_\\", 5)
        assertTrue(query.matches(ConsoleLog(message = "ERROR 100%_\\ marker", level = 5, timestamp = 1)))
        assertFalse(query.matches(ConsoleLog(message = "ERROR 100xxx marker", level = 6, timestamp = 2)))
        assertFalse(query.matches(ConsoleLog(message = "100%_\\ marker", level = 4, timestamp = 3)))
        assertTrue(ConsoleLogQuery("error", 0).matches(ConsoleLog(message = "ERROR", level = 1, timestamp = 4)))
    }
}
