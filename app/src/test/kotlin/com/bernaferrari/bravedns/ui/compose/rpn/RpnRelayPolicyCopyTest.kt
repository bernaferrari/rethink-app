package com.bernaferrari.bravedns.ui.compose.rpn

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.ui.TestDialogActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33])
class RpnRelayPolicyCopyTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()

    @Test fun explainsInheritedPolicyOnlyWhileRelayIsEnabled() {
        var relay by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                val strings = RethinkRpnWinProxyDetailsStrings("Server", "Unknown", "Name", "Apps", "Domains", "IPs",
                    "Provider", "Error", "Latency", "Last connected", "Status", "Server options", "Relay", "Catch all",
                    "Lockdown", "Mobile only", "Wi-Fi only", "Edit Wi-Fi", "Choose apps", stringResource(R.string.rpn_relay_network_policy))
                RethinkRpnWinProxyDetailsScreen(
                    state = RethinkRpnWinProxyDetailsState("US", "0", "0", "0", proxyLatency = "0", proxyLastConnected = "Now",
                        proxyStatus = "Active", isProxyActive = true, options = RethinkRpnWinServerOptions(relay, false, false, false, false)),
                    strings = strings, onBackClick = {}, onHopChanged = { relay = it }, onCatchAllChanged = {},
                    onLockdownChanged = {}, onMobileOnlyChanged = {}, onSsidChanged = {}, onEditSsids = {}, onSelectApps = {},
                )
            }
        }
        val explanation = ApplicationProvider.getApplicationContext<Application>().getString(R.string.rpn_relay_network_policy)
        compose.onNodeWithText(explanation).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { relay = false }
        compose.onNodeWithText(explanation).assertDoesNotExist()
    }
}
