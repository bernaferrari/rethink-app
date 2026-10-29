package com.bernaferrari.bravedns.ui.compose.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.AppConfig
import com.bernaferrari.bravedns.ui.TestDialogActivity
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class ProxyDialogCancelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestDialogActivity>()

    @Test
    fun cancelSocksEditorKeepsConfiguredProxy() {
        val config = mockk<AppConfig>(relaxed = true)
        composeRule.setContent {
            MaterialTheme {
                val visible = remember { mutableStateOf(true) }
                if (visible.value) {
                    ProxySocks5DialogHost(
                        state = Socks5DialogState("127.0.0.1", "9050", "", "", "", emptyList(), false, true, false),
                        onStateChange = { if (it == null) visible.value = false },
                        appConfig = config,
                        persistentState = mockk(),
                        httpProxyHostEmptyError = "host required",
                        httpProxyInvalidPortError = "invalid port",
                        httpProxyRangePortError = "invalid range",
                        onLogEvent = {},
                        onReloadUi = {},
                    )
                } else Text("dismissed")
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.lbl_cancel))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("dismissed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("dismissed").assertIsDisplayed()
        verify(exactly = 0) { config.removeProxy(any(), any()) }
    }

    @Test
    fun cancelHttpEditorKeepsConfiguredProxy() {
        val config = mockk<AppConfig>(relaxed = true)
        composeRule.setContent {
            MaterialTheme {
                val visible = remember { mutableStateOf(true) }
                if (visible.value) {
                    ProxyHttpDialogHost(
                        state = HttpDialogState("http://127.0.0.1:8118", "", emptyList(), true, false),
                        onStateChange = { if (it == null) visible.value = false },
                        appConfig = config,
                        persistentState = mockk(),
                        httpProxyHostEmptyError = "host required",
                        httpProxyToastSuccess = "saved",
                        onLogEvent = {},
                        onReloadUi = {},
                    )
                } else Text("dismissed")
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.lbl_cancel))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("dismissed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("dismissed").assertIsDisplayed()
        verify(exactly = 0) { config.removeProxy(any(), any()) }
    }
}
