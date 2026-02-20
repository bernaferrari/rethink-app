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
package com.celzero.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design system dimensions for consistent spacing throughout the app.
 */
object Dimensions {
    // Spacing scale
    val spacingNone: Dp = 0.dp
    val spacingXs: Dp = 4.dp
    val spacingSm: Dp = 8.dp
    val spacingMd: Dp = 12.dp
    val spacingSmMd: Dp = 10.dp
    val spacingLg: Dp = 16.dp
    val spacingXl: Dp = 24.dp
    val spacing2xl: Dp = 32.dp
    val spacing3xl: Dp = 48.dp

    // Component dimensions
    val cardCornerRadius: Dp = 24.dp
    val cardCornerRadiusLarge: Dp = 32.dp
    val buttonCornerRadius: Dp = 14.dp
    val buttonCornerRadiusLarge: Dp = 24.dp

    // Card padding
    val cardPadding: Dp = 18.dp
    val cardPaddingSm: Dp = 12.dp

    // Screen padding
    val screenPaddingHorizontal: Dp = 20.dp
    val screenPaddingVertical: Dp = 14.dp

    // Icon sizes
    val iconSizeXs: Dp = 16.dp
    val iconSizeSm: Dp = 20.dp
    val iconSizeMd: Dp = 24.dp
    val iconSizeLg: Dp = 32.dp
    val iconSizeXl: Dp = 48.dp

    // Touch targets (minimum 48dp for accessibility)
    val touchTargetMin: Dp = 48.dp
    val touchTargetSm: Dp = 44.dp

    // Button dimensions
    val buttonHeight: Dp = 50.dp
    val buttonHeightSm: Dp = 40.dp
    val buttonHeightLg: Dp = 56.dp

    // List item dimensions
    val listItemHeight: Dp = 68.dp
    val listItemHeightSm: Dp = 56.dp
    val listItemHeightLg: Dp = 72.dp

    // Divider
    val dividerThickness: Dp = 0.5.dp
    val dividerThicknessBold: Dp = 1.dp

    // Opacity values for consistent theming
    object Opacity {
        const val FULL: Float = 1f
        const val HIGH: Float = 0.87f
        const val MEDIUM: Float = 0.7f
        const val DISABLED: Float = 0.38f
        const val LOW: Float = 0.5f
        const val HINT: Float = 0.6f
    }

    // Elevation values
    object Elevation {
        val none: Dp = 0.dp
        val low: Dp = 1.dp
        val medium: Dp = 4.dp
        val high: Dp = 8.dp
    }
}

/**
 * Standard padding values for common use cases.
 */
object Paddings {
    val none = PaddingValues(0.dp)
    val xs = PaddingValues(Dimensions.spacingXs)
    val sm = PaddingValues(Dimensions.spacingSm)
    val md = PaddingValues(Dimensions.spacingMd)
    val lg = PaddingValues(Dimensions.spacingLg)
    val xl = PaddingValues(Dimensions.spacingXl)
    
    val screen = PaddingValues(
        horizontal = Dimensions.screenPaddingHorizontal,
        vertical = Dimensions.screenPaddingVertical
    )
    
    val card = PaddingValues(Dimensions.cardPadding)
    val cardSm = PaddingValues(Dimensions.cardPaddingSm)
}
