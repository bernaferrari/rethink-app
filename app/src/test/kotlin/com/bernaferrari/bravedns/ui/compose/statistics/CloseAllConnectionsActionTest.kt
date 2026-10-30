package com.bernaferrari.bravedns.ui.compose.statistics

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.TestDialogActivity
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class CloseAllConnectionsActionTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()

    @Test fun stoppedMenuIsDisabledAndShowingConfirmationDoesNotDispatch() {
        var calls = 0
        val request = CloseConnectionsRequest({ false }) { calls++; true }
        compose.setContent { MaterialTheme { CloseAllConnectionsMenu(false, request) } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_more)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.stats_close_connections)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun confirmationCannotCloseAfterProtectionStops() {
        var calls = 0
        val running = mutableStateOf(true)
        val request = CloseConnectionsRequest({ running.value }) { calls++; true }
        compose.setContent { MaterialTheme { CloseAllConnectionsMenu(running.value, request) } }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.cd_more)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.stats_close_connections)).performClick()
        compose.runOnIdle { assertEquals(0, calls); running.value = false }
        compose.onNodeWithText(compose.activity.getString(R.string.stats_close_connections_stopped)).assertExists()
        compose.onNode(hasText(compose.activity.getString(R.string.stats_close_connections)) and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }
}
