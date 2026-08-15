/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.theme

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bernaferrari.bravedns.ui.TestDialogActivity
import com.bernaferrari.bravedns.util.Themes
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [33],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<TestDialogActivity>()

    @Test
    fun emptyState_light() {
        capture("empty_state_light") { DesignSystemEmptyState(showCta = false) }
    }

    @Test
    fun emptyState_dark() {
        capture("empty_state_dark", dark = true) { DesignSystemEmptyState(showCta = false) }
    }

    @Test
    fun emptyStateCta_light() {
        capture("empty_state_cta_light") { DesignSystemEmptyState(showCta = true) }
    }

    @Test
    fun emptyStateCta_dark() {
        capture("empty_state_cta_dark", dark = true) { DesignSystemEmptyState(showCta = true) }
    }

    @Test
    fun loadingState_light() {
        capture("loading_state_light", freezeClock = true) { DesignSystemLoadingState() }
    }

    @Test
    fun loadingState_dark() {
        capture("loading_state_dark", dark = true, freezeClock = true) { DesignSystemLoadingState() }
    }

    @Test
    fun statusColorChips_light() {
        capture("status_color_chips_light") { DesignSystemStatusColorChips() }
    }

    @Test
    fun statusColorChips_dark() {
        capture("status_color_chips_dark", dark = true) { DesignSystemStatusColorChips() }
    }

    @Test
    fun firewallToggles_light() {
        capture("firewall_toggles_light") { DesignSystemFirewallToggles() }
    }

    @Test
    fun firewallToggles_dark() {
        capture("firewall_toggles_dark", dark = true) { DesignSystemFirewallToggles() }
    }

    @Test
    fun firewallToggles_reducedMotion() {
        capture("firewall_toggles_reduced_motion", reducedMotion = true) {
            DesignSystemFirewallToggles()
        }
    }

    @Test
    fun logRows_light() {
        capture("log_rows_light") { DesignSystemLogRows() }
    }

    @Test
    fun logRows_dark() {
        capture("log_rows_dark", dark = true) { DesignSystemLogRows() }
    }

    @Test
    fun logRows_reducedMotion() {
        capture("log_rows_reduced_motion", reducedMotion = true) { DesignSystemLogRows() }
    }

    @Test
    fun appearanceSwatches_light() {
        capture("appearance_swatches_light") { DesignSystemAppearanceCard() }
    }

    @Test
    fun appearanceSwatches_dark() {
        capture("appearance_swatches_dark", dark = true) { DesignSystemAppearanceCard() }
    }

    @Test
    fun appearanceSwatches_reducedMotion() {
        capture("appearance_swatches_reduced_motion", reducedMotion = true) {
            DesignSystemAppearanceCard()
        }
    }

    private fun capture(
        name: String,
        dark: Boolean = false,
        reducedMotion: Boolean = false,
        freezeClock: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (freezeClock) {
            composeRule.mainClock.autoAdvance = false
        }
        composeRule.setContent {
            RethinkTheme(
                themePreference = if (dark) Themes.DARK.id else Themes.LIGHT.id,
                colorPreset = RethinkColorPreset.CORAL,
            ) {
                CompositionLocalProvider(
                    LocalRethinkMotion provides RethinkMotion(reducedMotion = reducedMotion),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .testTag(ScreenshotTag),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        content()
                    }
                }
            }
        }
        if (freezeClock) {
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.onNodeWithTag(ScreenshotTag)
            .captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val ScreenshotTag = "design_system_screenshot"
    }
}
