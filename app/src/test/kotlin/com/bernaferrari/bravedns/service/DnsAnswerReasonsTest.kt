package com.bernaferrari.bravedns.service

import org.junit.Assert.*
import org.junit.Test

class DnsAnswerReasonsTest {
    @Test fun `indistinguishable questions with conflicting verdicts omit the reason`() {
        val reasons = DnsAnswerReasons()
        val key = DnsQuestionKey("socket", "10001", "same.example.", 1, 1234)
        reasons.record(key, "blocked")
        reasons.record(key, "trusted")
        assertNull(reasons.find(key))
        reasons.record(key, "blocked")
        assertNull(reasons.find(key))
    }
    @Test fun `concurrent questions on one socket retain reasons through out-of-order callbacks`() {
        val reasons = DnsAnswerReasons()
        val first = DnsQuestionKey("socket1", "10001", "first.example.", 1, 1234)
        val second = first.copy(name = "second.example.")
        val ipv6 = first.copy(type = 28)
        reasons.record(first, "blocked")
        reasons.record(second, "trusted")
        reasons.record(ipv6, "isolated")
        assertEquals("trusted", reasons.find(second))
        assertEquals("isolated", reasons.find(ipv6))
        assertEquals("blocked", reasons.find(first))
        assertEquals("blocked", reasons.find(first)) // duplicate summary for one question
        assertNull(reasons.find(first.copy(startedAt = 1235)))
        assertNull(reasons.find(first.copy(uid = "10002")))
        reasons.clear()
        assertNull(reasons.find(first))
    }
}
