package com.bernaferrari.bravedns.ui.compose.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.TestDialogActivity
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33])
class NavigationKeyboardFocusTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()

    @Test fun keyboardFocusWaitsForTheBoundedNavigationSurfaceToExist() {
        var visible by mutableStateOf(false)
        compose.setContent {
            val input = LocalInputModeManager.current
            LaunchedEffect(Unit) { input.requestInputMode(InputMode.Keyboard) }
            MaterialTheme {
                Box(if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                    RethinkNavShell(
                        destinations = listOf(RethinkNavigationItem("home", "Home", MaterialSymbols.Filled.Home)),
                        selectedId = "home", onDestinationSelected = {},
                    ) { }
                }
            }
        }
        // The WASM host and initial TV layout can temporarily have no navigation item to focus.
        compose.waitForIdle()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("Home").assertIsFocused()
    }
}
