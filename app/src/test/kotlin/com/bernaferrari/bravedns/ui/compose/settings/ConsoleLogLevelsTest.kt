package com.bernaferrari.bravedns.ui.compose.settings

import Logger
import org.junit.Assert.*
import org.junit.Test

class ConsoleLogLevelsTest {
    @Test fun noneSelectionSkipsTheUserEventWireLevel() {
        assertEquals(Logger.LoggerLevel.NONE.id, consoleLogSelectedLevel(7))
        assertNotEquals(Logger.LoggerLevel.USR.id, consoleLogSelectedLevel(7))
        assertEquals(7, consoleLogSelectionIndex(Logger.LoggerLevel.NONE.id))
    }

    @Test fun everyDisplayedSeverityKeepsItsWireIdAndSelection() {
        for (id in 0..6) {
            assertEquals(id.toLong(), consoleLogSelectedLevel(id))
            assertEquals(id, consoleLogSelectionIndex(id.toLong()))
        }
    }
}
