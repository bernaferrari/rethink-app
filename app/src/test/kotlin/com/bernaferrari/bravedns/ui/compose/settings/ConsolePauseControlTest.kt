package com.bernaferrari.bravedns.ui.compose.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.TestDialogActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class ConsolePauseControlTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()

    @Test fun accessiblePauseAndResumeAreIndependentOfLoggingLevelAndDisabledDuringCapture() {
        val paused = mutableStateOf(false)
        val busy = mutableStateOf(false)
        var toggles = 0
        var levelChanges = 0
        compose.setContent {
            MaterialTheme {
                RethinkConsoleLogsScreen(
                    strings = strings(), initialLogLevel = 0,
                    onFilterChange = {}, onLogLevelSelected = { levelChanges++ },
                    onShareClick = {}, onClearClick = {}, paused = paused.value, pauseBusy = busy.value,
                    onPauseToggle = { toggles++; paused.value = !paused.value },
                ) {}
            }
        }
        compose.onNodeWithContentDescription("Pause live updates").performClick()
        compose.onNodeWithContentDescription("Resume live updates").assertIsOn()
        compose.onNodeWithText("Paused snapshot").assertExists()
        compose.runOnIdle { assertEquals(0, levelChanges); busy.value = true }
        compose.onNodeWithContentDescription("Resume live updates").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, toggles); busy.value = false }
        compose.onNodeWithContentDescription("Resume live updates").performClick()
        compose.onAllNodesWithText("Paused snapshot").assertCountEquals(0)
        compose.runOnIdle { assertEquals(2, toggles); assertEquals(0, levelChanges) }
    }

    @Test fun aPendingClearCannotBeConfirmedTwiceAndShowsProgress() {
        compose.setContent {
            MaterialTheme {
                RethinkConsoleLogsScreen(strings = strings(), initialLogLevel = 0,
                    onFilterChange = {}, onLogLevelSelected = {}, onShareClick = {},
                    onClearClick = { throw AssertionError("A pending deletion must not dispatch again") },
                    clearBusy = true,
                ) {}
            }
        }
        compose.onNodeWithText("Deleting console logs…").assertExists()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete logs").assertIsNotEnabled()
    }

    private fun strings() = RethinkConsoleLogStrings(
        title = "Console", searchHint = "Search", filterDescription = "Severity", clearDescription = "Delete logs",
        clearSearchDescription = "Clear search", deleteDialogTitle = "Delete logs?", deleteDialogMessage = "Delete saved logs",
        deleteLabel = "Delete", reportLabel = "Share", cancelLabel = "Cancel", filterOptions = listOf("All", "Errors"),
        pausedDescription = "Paused snapshot",
    )
}
