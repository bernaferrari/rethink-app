package com.bernaferrari.bravedns.ui.compose.settings

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.diagnostics.PcapCaptureManager
import com.bernaferrari.bravedns.scheduler.WorkScheduler
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.TestDialogActivity
import com.bernaferrari.bravedns.ui.compose.app.AppNotesEditor
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class ProductEditorsTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()
    @After fun cleanup() { stopKoin(); unmockkAll() }

    @Test fun retentionChoicesKeepActionsVisibleWithLargeText() {
        val preferences = mockk<PersistentState>(relaxed = true)
        val scheduler = mockk<WorkScheduler>(relaxed = true)
        every { preferences.logRetentionHours } returns 168
        startKoin { modules(module { single { scheduler } }) }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme { LogRetentionSetting(preferences) }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.port_log_retention)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.port_save)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.resources.getQuantityString(R.plurals.port_hours, 1, 1)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.port_save)).performClick()
        verify { preferences.logRetentionHours = 1; scheduler.schedulePurgeConnectionsLog() }
    }

    @Test fun failedNoteSaveKeepsDraftAcrossRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { AppNotesEditor(AppInfo(packageName = "example", uid = 10001), saveNotes = { false }) } }
        compose.onNodeWithText(compose.activity.getString(R.string.port_notes)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Remember this decision")
        compose.onNodeWithText(compose.activity.getString(R.string.port_save)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.port_notes_error)).fetchSemanticsNodes().isNotEmpty()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains("Remember this decision")
        compose.onNodeWithText(compose.activity.getString(R.string.lbl_cancel)).assertIsDisplayed()
    }

    @Test fun busyImportKeepsCancelVisibleAndDoesNotAllowDuplicateSubmission() {
        var canceled = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme {
                    com.bernaferrari.bravedns.ui.compose.firewall.RulesImportPreviewDialog(
                        preview = com.bernaferrari.bravedns.rules.RuleImportPreview(
                            (1..5).map { "rule$it.example.com" }, 3, 2, false),
                        busy = true, onImport = {}, onDismiss = { canceled = true },
                    )
                }
            }
        }
        compose.onAllNodesWithText(compose.activity.getString(R.string.port_import_rules))
            .filterToOne(hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.lbl_cancel)).assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(canceled) }
    }

    @Test fun captureDialogFitsCompactScreenAndReflectsAutomaticStop() {
        val manager = mockk<PcapCaptureManager>(relaxed = true)
        val active = MutableStateFlow(true)
        every { manager.activeFlow } returns active
        every { manager.active } returns true
        every { manager.files() } returns emptyList()
        startKoin { modules(module { single { manager } }) }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MaterialTheme { PcapCaptureSetting() }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.port_capture_title)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.lbl_done)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.port_capture_stop)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { active.value = false }
        compose.onNodeWithText(compose.activity.getString(R.string.port_capture_start)).performScrollTo().assertIsDisplayed()
    }
}
