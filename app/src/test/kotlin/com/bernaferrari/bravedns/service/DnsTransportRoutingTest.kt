package com.bernaferrari.bravedns.service

import com.celzero.firestack.backend.Backend
import org.junit.Assert.*
import org.junit.Test

class DnsTransportRoutingTest {
    @Test fun `each transport carries its ordered proxy candidates`() {
        assertEquals("Preferred:wg1:Base,System:wg1:Base", DnsTransportRouting.encode("Preferred,System", "wg1,Base"))
        assertEquals("", DnsTransportRouting.encode("", "wg1,Base"))
        assertEquals("Preferred", DnsTransportRouting.encode("Preferred", ""))
    }
    @Test fun `proxy DNS cannot recursively route through a user proxy`() {
        assertEquals("wg1:Base", DnsTransportRouting.encode("wg1", "wg2,Base"))
        assertEquals(Backend.CT + "wg1:Base", DnsTransportRouting.encode(Backend.CT + "wg1", "wg2,Base"))
    }
    @Test fun `bypass policy parses original transports rather than encoded proxies`() {
        assertEquals("Preferred,System", DnsTransportRouting.transportIds("Preferred:wg1:Base,System:Exit"))
    }
}
