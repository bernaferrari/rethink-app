/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.ui.compose.theme

import androidx.compose.ui.unit.dp

/**
 * Portable counterpart of the Android [Dimensions] design token set.
 *
 * Do not create a second spacing scale for the browser: common screens use these values on every
 * target, while Android screens are progressively switched to those same common renderers.
 */
object SharedDimensions {
    val spacingNone = 0.dp
    val spacingXs = 4.dp
    val spacingGridTile = 2.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingSmMd = 10.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val spacing2xl = 32.dp
    val spacing3xl = 48.dp

    val cornerRadius2xs = 3.dp
    val cornerRadiusXs = 4.dp
    val cornerRadiusSm = 6.dp
    val cornerRadiusSmMd = 10.dp
    val cornerRadiusMd = 12.dp
    val cornerRadiusMdLg = 14.dp
    val cornerRadiusLg = 16.dp
    val cornerRadiusXl = 20.dp
    val cornerRadius2xl = 20.dp
    val cornerRadius3xl = 20.dp
    val cornerRadius4xl = 24.dp
    val cornerRadius5xl = 28.dp
    val cornerRadiusPill = 50.dp
    val cornerRadiusFull = 999.dp

    val cardCornerRadius = 20.dp
    val cardCornerRadiusLarge = 28.dp
    val heroCornerRadius = 32.dp
    val chipCornerRadius = 50.dp
    val iconContainerRadius = 12.dp
    val buttonCornerRadius = 50.dp
    val buttonCornerRadiusLarge = 50.dp

    val cardPadding = 16.dp
    val cardPaddingSm = 12.dp
    val screenPaddingHorizontal = 16.dp
    val screenPaddingVertical = 12.dp

    val iconSizeXs = 16.dp
    val iconSizeSm = 20.dp
    val iconSizeMd = 24.dp
    val iconSizeLg = 32.dp
    val iconSizeXl = 48.dp
    val iconContainerSm = 32.dp
    val iconContainerMd = 40.dp
    val iconContainerLg = 48.dp

    val touchTargetMin = 48.dp
    val touchTargetSm = 44.dp
    val buttonHeight = 52.dp
    val buttonHeightSm = 44.dp
    val buttonHeightLg = 60.dp
    val heroButtonHeight = 48.dp
    val listItemHeight = 64.dp
    val listItemHeightSm = 56.dp
    val listItemHeightLg = 72.dp
    val progressBarHeight = 10.dp
    val tabIndicatorHeight = 4.dp
    val dividerThickness = 0.5.dp
    val dividerThicknessBold = 1.dp
}
