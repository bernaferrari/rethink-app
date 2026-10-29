package com.bernaferrari.bravedns.ui

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33])
class HomeAppLockStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestDialogActivity>()

    @Test
    fun returningAfterTimeoutRelocksSession() {
        val owner = Owner()
        var expired = false
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val unlocked = rememberAppUnlockState { expired }
                Text(if (unlocked.value) "unlocked" else "locked")
                Button(onClick = { unlocked.value = true }) { Text("authenticate") }
            }
        }
        composeRule.onNodeWithText("authenticate").performClick()
        composeRule.runOnUiThread {
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.onNodeWithText("unlocked").assertIsDisplayed()
        composeRule.runOnUiThread {
            owner.registry.currentState = Lifecycle.State.CREATED
            expired = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.onNodeWithText("locked").assertIsDisplayed()
    }

    @Test
    fun restoredUiStateDoesNotRestoreAuthorization() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            val unlocked = rememberAppUnlockState { true }
            Text(if (unlocked.value) "unlocked" else "locked")
            Button(onClick = { unlocked.value = true }) { Text("authenticate") }
        }
        composeRule.onNodeWithText("authenticate").performClick()
        composeRule.onNodeWithText("unlocked").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("locked").assertIsDisplayed()
    }

    @Test
    fun returningFromCredentialPromptDoesNotImmediatelyRelock() {
        val owner = Owner()
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val unlocked = rememberAppUnlockState { true }
                Text(if (unlocked.value) "unlocked" else "locked")
                Button(onClick = { unlocked.value = true }) { Text("authenticate") }
            }
        }
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.onNodeWithText("authenticate").performClick()
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.onNodeWithText("unlocked").assertIsDisplayed()
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
