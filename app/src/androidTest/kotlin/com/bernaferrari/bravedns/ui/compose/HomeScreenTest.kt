/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.ui.compose.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTheme
import org.junit.Assert.assertEquals
import androidx.test.platform.app.InstrumentationRegistry
import com.bernaferrari.bravedns.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_displaysCorrectInitialState() {
        val uiState = HomeScreenUiState(
            isVpnActive = false,
        )

        composeTestRule.setContent {
            RethinkTheme {
                HomeScreen(
                    uiState = uiState,
                    onStartStopClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.lbl_start)).assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysStopButton_whenVpnIsActive() {
        val uiState = HomeScreenUiState(
            isVpnActive = true,
        )

        composeTestRule.setContent {
            RethinkTheme {
                HomeScreen(
                    uiState = uiState,
                    onStartStopClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.lbl_stop)).assertIsDisplayed()
    }

    @Test
    fun homeScreen_startStopButton_triggersCallback() {
        var clickCount = 0
        val uiState = HomeScreenUiState(isVpnActive = false)

        composeTestRule.setContent {
            RethinkTheme {
                HomeScreen(
                    uiState = uiState,
                    onStartStopClick = { clickCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.lbl_start)).performClick()

        // Verify callback was triggered
        assertEquals(1, clickCount)
    }

    @Test
    fun homeScreen_displaysActivity() {
        val uiState = HomeScreenUiState(
            allowedToday = 432,
            blockedToday = 87,
        )

        composeTestRule.setContent {
            RethinkTheme {
                HomeScreen(
                    uiState = uiState,
                    onStartStopClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("432", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("87", substring = true).assertIsDisplayed()
    }
}
