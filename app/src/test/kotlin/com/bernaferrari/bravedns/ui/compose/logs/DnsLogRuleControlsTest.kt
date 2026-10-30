package com.bernaferrari.bravedns.ui.compose.logs

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.DomainRulesManager.Status
import com.bernaferrari.bravedns.ui.TestDialogActivity
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class DnsLogRuleControlsTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()

    @Test fun appAndGlobalActionsDisableTogetherWhileSavingAndReflectOnlyCommittedScope() {
        val release = CompletableDeferred<Unit>()
        val rows = mutableMapOf(42 to Status.NONE, -1000 to Status.NONE)
        val editor = DnsQueryRuleEditor("example.com", rows.keys,
            read = { _, uid -> rows.getValue(uid) },
            write = { _, uid, status -> release.await(); rows[uid] = status }, onError = { throw AssertionError(it) })
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        compose.setContent {
            val state by editor.state.collectAsState()
            MaterialTheme {
                DnsLogRuleControls(state, listOf(42 to "Shared UID 42 (2 apps)", -1000 to "All apps"),
                    listOf(RethinkChoiceOption(Status.NONE, "No rule"), RethinkChoiceOption(Status.BLOCK, "Block"), RethinkChoiceOption(Status.TRUST, "Trust"))) {
                    uid, status -> scope.launch { editor.select(uid, status) }
                }
            }
        }
        try {
            compose.onNodeWithText("Shared UID 42 (2 apps)").assertExists()
            compose.onNodeWithText("All apps").assertExists()
            compose.onAllNodesWithText("Block")[0].performClick()
            compose.onAllNodesWithText("Block")[0].assertIsNotEnabled()
            compose.onAllNodesWithText("Trust")[1].assertIsNotEnabled()
            compose.onAllNodesWithText("No rule")[0].assertIsSelected()
            compose.runOnIdle { release.complete(Unit) }
            compose.onAllNodesWithText("Block")[0].assertIsEnabled().assertIsSelected()
            compose.onAllNodesWithText("No rule")[1].assertIsSelected()
        } finally { release.complete(Unit) }
    }

    @Test fun fullDetailsSheetKeepsGlobalActionsAndDismissReachableOnSmallScreens() {
        compose.setContent {
            MaterialTheme {
                RethinkLogDetailsSheet(
                    title = "example.com",
                    details = (1..12).map { RethinkLogDetailEntry("Detail $it", "Value $it") },
                    dismissLabel = "Done",
                    onDismiss = {},
                    actions = {
                        DnsLogRuleControls(DnsRuleEditorState(mapOf(42 to Status.NONE, -1000 to Status.NONE)),
                            listOf(42 to "UID 42", -1000 to "All apps"),
                            listOf(RethinkChoiceOption(Status.NONE, "No rule"), RethinkChoiceOption(Status.BLOCK, "Block"), RethinkChoiceOption(Status.TRUST, "Trust"))) { _, _ -> }
                    },
                )
            }
        }
        compose.onAllNodesWithText("Trust")[1].performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }

    @Test fun failureIsVisibleAndDoesNotSelectAnUnsavedRule() {
        val editor = DnsQueryRuleEditor("example.com", setOf(42), read = { _, _ -> Status.NONE },
            write = { _, _, _ -> throw IllegalStateException("DB closed") }, onError = { })
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        compose.setContent {
            val state by editor.state.collectAsState()
            MaterialTheme {
                DnsLogRuleControls(state, listOf(42 to "UID 42"),
                    listOf(RethinkChoiceOption(Status.NONE, "No rule"), RethinkChoiceOption(Status.BLOCK, "Block"))) {
                    uid, status -> scope.launch { editor.select(uid, status) }
                }
            }
        }
        compose.onNodeWithText("Block").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.dns_rule_save_failed)).assertExists()
        compose.onNodeWithText("No rule").assertIsSelected()
        compose.onNodeWithText("Block").assertIsEnabled().assertIsNotSelected()
    }
}
