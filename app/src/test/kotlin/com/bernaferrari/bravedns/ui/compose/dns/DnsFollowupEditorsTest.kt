package com.bernaferrari.bravedns.ui.compose.dns

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.data.DnsBootstrapAddresses
import com.bernaferrari.bravedns.data.SmartDnsProfile
import com.bernaferrari.bravedns.ui.TestDialogActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w320dp-h480dp")
class DnsFollowupEditorsTest {
    @get:Rule val compose = createAndroidComposeRule<TestDialogActivity>()

    @Test fun bootstrapEditorRetainsDraftAndRejectsNamesBeforeSubmittingNumericAddresses() {
        val restoration = StateRestorationTester(compose)
        var accepted: List<String>? = null
        restoration.setContent {
            MaterialTheme {
                RethinkDohEndpointEditor("Add DNS", "Name", "URL", "Mine", "https://resolver.example/dns-query",
                    "Insecure", RethinkEndpointEditorStrings("Cancel", "Add"), "Bootstrap addresses", "Numeric addresses only",
                    onSubmit = { name, url, _, bootstrap ->
                        val parsed = DnsBootstrapAddresses.parse(bootstrap)
                        if (parsed == null) "Invalid bootstrap" else { accepted = listOf(name, url, parsed); null }
                    }, onDismiss = {})
            }
        }
        compose.onNode(hasSetTextAction().and(hasText("Bootstrap addresses"))).performTextInput("resolver.other.example")
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Invalid bootstrap").assertExists()
        assertNull(accepted)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("resolver.other.example").assertExists()
        compose.onNode(hasSetTextAction().and(hasText("Bootstrap addresses"))).performTextReplacement("2001:db8::53")
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("Mine", "https://resolver.example/dns-query", "2001:db8::53"), accepted) }
    }

    @Test fun profileRowsSelectExactlyTheChosenPersistentValue() {
        var selected by mutableStateOf(SmartDnsProfile.None)
        val labels = SmartDnsProfileLabels("Filtering", SmartDnsProfile.entries.associateWith { it.name to "Description ${it.name}" })
        compose.setContent { MaterialTheme { RethinkSmartDnsProfiles(selected, labels, "Updating resolver choices for up to 10 seconds") { selected = it } } }
        compose.onNodeWithText("Updating resolver choices for up to 10 seconds").assertExists()
        compose.onNodeWithText("Family").performClick()
        compose.runOnIdle { assertEquals(SmartDnsProfile.Family, selected) }
        compose.onNodeWithText("Family").assertIsSelected()
        compose.onNodeWithText("None").assertIsNotSelected()
    }
    @Test fun addAndCancelRemainReachableWithImeSpaceAndLargeText() {
        var submitted = false
        var cancelled = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.35f)) {
                MaterialTheme {
                    // A compact landscape-sized content area with simulated IME insets.
                    Box(Modifier.width(320.dp).height(360.dp).padding(bottom = 72.dp)) {
                        RethinkDohEndpointEditor("Add DNS", "Name", "URL", "Mine", "https://resolver.example/dns-query",
                            "Insecure", RethinkEndpointEditorStrings("Cancel", "Add"), "Bootstrap addresses", "Numeric addresses only",
                            onSubmit = { _, _, _, _ -> submitted = true; null }, onDismiss = { cancelled = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Add", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Insecure").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(submitted) }
        compose.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

}
