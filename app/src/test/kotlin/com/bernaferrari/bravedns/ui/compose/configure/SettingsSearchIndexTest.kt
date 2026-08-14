package com.bernaferrari.bravedns.ui.compose.configure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App-side behavior of [SettingsSearchDestination]; the search filter/highlight logic
 * itself is tested in :shared commonTest (RethinkConfigureSearchTest).
 */
class SettingsSearchIndexTest {

    @Test
    fun appearanceFocus_staysOnConfigure() {
        assertTrue(SettingsSearchDestination.General("general_appearance").isAppearanceFocus())
        assertTrue(SettingsSearchDestination.General("general_theme_mode").isAppearanceFocus())
        assertTrue(SettingsSearchDestination.General("general_theme_color").isAppearanceFocus())
        assertFalse(SettingsSearchDestination.General("general_backup").isAppearanceFocus())
        assertFalse(SettingsSearchDestination.General().isAppearanceFocus())
    }
}
