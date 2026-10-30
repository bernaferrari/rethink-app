package com.bernaferrari.bravedns.ui.compose.firewall

import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirewallAppGroupingTest {
    @Test
    fun alphabeticalSectionsHaveIndependentHeadersAndConnectedCorners() {
        val initials = listOf("A", "A", "B", "C", "C")
        val rows = initials.indices.map { firewallAppGrouping(initials, it, true) }
        assertEquals(listOf(0, 2, 3), rows.indices.filter { rows[it].startsSection })
        assertEquals(listOf(CardPosition.First, CardPosition.Last, CardPosition.Single,
            CardPosition.First, CardPosition.Last), rows.map { it.position })
    }

    @Test
    fun technicalSortFormsOneGroupWithoutLetterHeaders() {
        val initials = listOf("", "", "")
        val rows = initials.indices.map { firewallAppGrouping(initials, it, false) }
        assertTrue(rows.none { it.startsSection })
        assertEquals(listOf(CardPosition.First, CardPosition.Middle, CardPosition.Last), rows.map { it.position })
    }

    @Test
    fun singleTechnicalRowKeepsAllOuterCorners() {
        val row = firewallAppGrouping(listOf(""), 0, false)
        assertFalse(row.startsSection)
        assertEquals(CardPosition.Single, row.position)
    }

    @Test
    fun repeatedInitialAfterFilteringStillBeginsAnotherSection() {
        val row = firewallAppGrouping(listOf("A", "B", "A"), 2, true)
        assertTrue(row.startsSection)
        assertEquals(CardPosition.Single, row.position)
    }
}
