package com.bernaferrari.bravedns.ui.compose.firewall

import org.junit.Assert.assertEquals
import org.junit.Test

class RethinkFirewallScrollerTest {

    @Test
    fun marks_pointAtLetterHeaders_notDoubledAppNames() {
        val apps = listOf(
            app("Gboard"),
            app("Gmail"),
            app("Maps"),
            app("Messages"),
            app("Netflix"),
        )
        val marks = rethinkFirewallScrollerMarks(apps)
        assertEquals(listOf("G", "M", "N"), marks.map { it.label })
        // Lazy column is header, app, app, header, app, app, header, app.
        assertEquals(0, marks[0].listIndex)
        assertEquals(3, marks[1].listIndex)
        assertEquals(6, marks[2].listIndex)
    }

    private fun app(name: String) = RethinkFirewallApp(
        id = name,
        uid = name.hashCode(),
        appName = name,
        packageName = "com.example.${name.lowercase()}",
        status = RethinkFirewallAppStatus.Allowed,
        wifiBlocked = false,
        mobileBlocked = false,
    )
}
