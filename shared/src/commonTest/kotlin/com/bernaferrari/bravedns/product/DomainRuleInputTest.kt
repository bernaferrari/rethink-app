package com.bernaferrari.bravedns.product

import com.bernaferrari.bravedns.rules.DomainRuleInput
import kotlin.test.*

class DomainRuleInputTest {
    @Test fun domainRulesAcceptHostsWildcardsAndHttpUrlsButRejectArbitraryText() {
        assertEquals("example.com", DomainRuleInput.normalize("https://www.EXAMPLE.com:443/path?query=yes"))
        assertEquals("www.example.com", DomainRuleInput.normalize("www.example.com"))
        assertEquals("*.eu", DomainRuleInput.normalize("*.EU."))
        listOf("not a domain", "bad..example", "bad.example/path", "*.bad..example", "-bad.example", "https://*.example.com").forEach {
            assertNull(DomainRuleInput.normalize(it), it)
        }
    }
}
