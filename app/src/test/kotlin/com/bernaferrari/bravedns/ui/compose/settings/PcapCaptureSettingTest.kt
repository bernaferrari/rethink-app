package com.bernaferrari.bravedns.ui.compose.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.diagnostics.PcapCaptureManager
import com.bernaferrari.bravedns.ui.TestDialogActivity
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class PcapCaptureSettingTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()
    @After fun cleanup() { stopKoin(); unmockkAll() }

    @Test fun reopeningDuringNativeStartKeepsControlsDisabled() = verifyPendingOperation(initiallyActive = false)
    @Test fun reopeningDuringNativeStopKeepsControlsDisabled() = verifyPendingOperation(initiallyActive = true)

    private fun verifyPendingOperation(initiallyActive: Boolean) {
        val manager = mockk<PcapCaptureManager>()
        val active = MutableStateFlow(initiallyActive)
        val started = CompletableDeferred<Unit>()
        val completion = CompletableDeferred<Boolean>()
        every { manager.activeFlow } returns active
        every { manager.active } answers { active.value }
        every { manager.files() } returns emptyList()
        suspend fun nativeOperation(): Boolean {
            started.complete(Unit)
            return completion.await().also { if (it) active.value = !initiallyActive }
        }
        coEvery { manager.start(any()) } coAnswers { nativeOperation() }
        coEvery { manager.stop(null) } coAnswers { nativeOperation() }
        startKoin { modules(module { single { manager } }) }
        val dispatcher = UnconfinedTestDispatcher()
        compose.setContent { MaterialTheme { PcapCaptureSetting(operationDispatcher = dispatcher) } }
        val title = compose.activity.getString(R.string.port_capture_title)
        val done = compose.activity.getString(R.string.lbl_done)
        val action = compose.activity.getString(if (initiallyActive) R.string.port_capture_stop else R.string.port_capture_start)
        try {
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText(action).performScrollTo().assertIsEnabled().performClick()
            compose.runOnIdle { Assert.assertTrue(started.isCompleted) }
            compose.onNodeWithText(done).performClick()
            compose.onNodeWithText(title).performClick()
            // Reopening can refresh the dialog, but must never release the pending native operation's lock.
            compose.onNodeWithText(action).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText(action).performTouchInput { click() }
            if (!initiallyActive) {
                compose.onNodeWithText(compose.activity.getString(R.string.port_capture_logcat))
                    .performScrollTo().assertIsNotEnabled()
            }
            compose.runOnIdle { completion.complete(true) }
            val nextAction = compose.activity.getString(if (initiallyActive) R.string.port_capture_start else R.string.port_capture_stop)
            compose.onNodeWithText(nextAction).performScrollTo().assertIsEnabled()
            coVerify(exactly = if (initiallyActive) 0 else 1) { manager.start(any()) }
            coVerify(exactly = if (initiallyActive) 1 else 0) { manager.stop(null) }
            compose.onAllNodesWithText(compose.activity.getString(R.string.port_capture_error)).assertCountEquals(0)
        } finally {
            completion.complete(true)
        }
    }
}
