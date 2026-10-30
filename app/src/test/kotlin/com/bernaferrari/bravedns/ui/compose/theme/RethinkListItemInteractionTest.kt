package com.bernaferrari.bravedns.ui.compose.theme

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.TestDialogActivity
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [33])
class RethinkListItemInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestDialogActivity>()

    @Test
    fun wholeSwitchRowTogglesExactlyOnce() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            RethinkTheme {
                RethinkSwitchListItem("Protection", checked = false, onCheckedChange = { changes.add(it) })
            }
        }
        composeRule.onNodeWithText("Protection").assertIsToggleable().assertIsOff().performClick()
        composeRule.runOnIdle { assertEquals(listOf(true), changes) }
    }

    @Test
    fun disabledSwitchRetainsDisabledSemantics() {
        composeRule.setContent {
            RethinkTheme {
                RethinkSwitchListItem("Protection", checked = false, onCheckedChange = {}, enabled = false)
            }
        }
        composeRule.onNodeWithText("Protection").assertIsToggleable().assertIsNotEnabled()
    }

    @Test
    fun extractedContentRetainsCustomLeadingAndSupportingText() {
        composeRule.setContent {
            RethinkTheme {
                RethinkListItem(headline = "Firewall", supporting = "Custom rules", leadingContent = { Text("12") })
            }
        }
        composeRule.onNodeWithText("Firewall").assertIsDisplayed()
        composeRule.onNodeWithText("Custom rules").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun connectedSelectedIconModeKeepsEveryLabelAndSelectionAction() {
        val selections = mutableListOf<Int>()
        composeRule.setContent {
            RethinkTheme {
                RethinkConnectedChoiceGroup(
                    options = listOf(
                        RethinkChoiceOption(1, "Name", MaterialSymbols.Filled.Security),
                        RethinkChoiceOption(2, "Package", MaterialSymbols.Filled.Security),
                    ),
                    selected = 1,
                    onSelect = { selections.add(it) },
                    labels = RethinkChoiceLabels.SelectedIcon,
                )
            }
        }
        composeRule.onNodeWithText("Name").assertIsDisplayed()
        composeRule.onNodeWithText("Package").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(listOf(2), selections) }
    }

}
