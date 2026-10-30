package com.bernaferrari.bravedns.ui.compose.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTheme
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvNavigationTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun remoteCanSelectRailDestinationAndReachItsControls() {
        var selectedPage = "home"
        var actionCount = 0
        compose.setContent {
            var selected by remember { mutableStateOf("home") }
            val input = LocalInputModeManager.current
            LaunchedEffect(Unit) { input.requestInputMode(InputMode.Keyboard) }
            RethinkTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 540.dp))) {
                    RethinkNavShell(
                        destinations = listOf(
                            RethinkNavigationItem("home", "Home", MaterialSymbols.Filled.Home),
                            RethinkNavigationItem("apps", "Apps", MaterialSymbols.Filled.Apps),
                            RethinkNavigationItem("settings", "Settings", MaterialSymbols.Filled.Settings),
                        ),
                        selectedId = selected,
                        onDestinationSelected = { selected = it; selectedPage = it },
                    ) { Button(onClick = { actionCount++ }) { Text("Page action") } }
                }
            }
        }
        compose.onNodeWithText("Home").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("apps", selectedPage) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, actionCount) }
    }
}
