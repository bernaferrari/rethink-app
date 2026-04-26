/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.celzero.bravedns.R
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.Utilities
import org.koin.android.ext.android.inject

/**
 * Base activity for all UI screens in the app.
 *
 * Responsibilities:
 * - Applies the alpha-build purple accent theme overlay before views are inflated, so
 *   testers on pre-release builds can immediately identify them at a glance.
 *
 * Usage:
 * All activities should extend [BaseActivity] instead of [AppCompatActivity] directly.
 * Each subclass must still apply its user-selected theme before calling super.onCreate()
 * so the ordering is maintained: user-theme → alpha overlay → view inflation.
 *
 * Example:
 * ```
 * class MyActivity : BaseActivity(R.layout.activity_my) {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
 *         super.onCreate(savedInstanceState) // BaseActivity applies the alpha overlay here
 *         ...
 *     }
 * }
 * ```
 */
abstract class BaseActivity(@LayoutRes contentLayoutId: Int = 0) :
    AppCompatActivity(contentLayoutId) {

    private val persistentState: PersistentState by inject()

    /**
     * Returns true when the device is currently in dark (night) mode.
     * Defined as a Context extension so callers read naturally without needing a receiver.
     */
    private fun Context.isDarkThemeOn(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        // Alpha overlay must be applied after the subclass sets the user theme and before
        // AppCompatActivity.onCreate() inflates the views, so it is inserted here — at the
        // very start of BaseActivity.onCreate() which is called via super() from each subclass.
        if (Utilities.isAlphaBuild()) {
            applyAlphaThemeOverlay()
        }
        super.onCreate(savedInstanceState)
    }

    /**
     * Overlays a purple accent colour on whichever theme the user has selected, making
     * alpha builds visually distinct from production at all times.
     *
     * Purple 200 (#CE93D8) is used for dark/black themes — it provides good contrast on
     * dark surfaces. Purple 700 (#7B1FA2) is used for light themes — it achieves
     * sufficient contrast on white/light surfaces.
     *
     * Must be called after the base theme is applied and before super.onCreate().
     */
    private fun applyAlphaThemeOverlay() {
        val resolvedTheme = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        val isLightTheme = resolvedTheme == R.style.AppThemeWhite ||
                resolvedTheme == R.style.AppThemeWhitePlus
        val overlayRes = if (isLightTheme) {
            R.style.ThemeOverlay_App_AlphaLight
        } else {
            R.style.ThemeOverlay_App_AlphaDark
        }
        theme.applyStyle(overlayRes, true)
    }
}


