/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Every bottom-bar tab, and both views of Activity (Log · Insights), must compose and settle.
 * waitForIdle fails with a timeout if a screen keeps recomposing or remeasuring (a composition
 * loop is what an "isn't responding" freeze on a tab switch looks like).
 */
@OptIn(ExperimentalTestApi::class)
class RethinkTabsSettleTest {

    @Test
    fun everyTabComposesAndSettles() = runSkikoComposeUiTest(
        size = Size(412f, 915f),
        testTimeout = 60.seconds,
    ) {
        setContent { RethinkAppContent(reducedMotion = true) }
        waitForIdle()

        for (tab in listOf("Apps", "Activity", "Settings", "Home", "Activity")) {
            click(tab)
        }
        click("Insights")
        click("Log")
        click("Home")
    }

    private fun SkikoComposeUiTest.click(label: String) {
        onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        waitForIdle()
    }
}
