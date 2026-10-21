/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.navigation

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.TestDialogActivity
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [33])
class RethinkAppNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<TestDialogActivity>()

    /**
     * Root entries are built while Home is active. A push from another root must still land on
     * that root's stack, not on the Home stack captured at first composition.
     */
    @Test
    fun pushFromNonHomeRoot_landsOnActiveRootStack() {
        composeRule.setContent {
            RethinkAppNavigation(
                destinations = RethinkRootDestination.entries.map {
                    RethinkNavigationItem(id = it.name, label = "tab-${it.name}", selectedIcon = MaterialSymbols.Filled.Home)
                },
            ) { nav ->
                entry<RethinkRoute.Home> { Text("home-root") }
                entry<RethinkRoute.Statistics> { Text("statistics-root") }
                entry<RethinkRoute.Configure> {
                    Button(onClick = { nav.push(RethinkRoute.About) }) { Text("open-about") }
                }
                entry<RethinkRoute.About> { Text("about-detail") }
            }
        }

        composeRule.onNodeWithText("tab-Configure").performClick()
        composeRule.onNodeWithText("open-about").performClick()
        composeRule.onNodeWithText("about-detail").assertIsDisplayed()

        composeRule.onNodeWithText("tab-Home").performClick()
        composeRule.onNodeWithText("home-root").assertIsDisplayed()
    }
}
