/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.ui.unit.dp

/**
 * The one spacing, corner and size scale for every target (Android, desktop, web). Screens use
 * these tokens instead of raw dp values.
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

    val cornerRadiusXs = 4.dp
    val cornerRadiusSm = 8.dp
    val cornerRadiusMd = 12.dp
    val cornerRadiusLg = 16.dp
    val cornerRadiusXl = 20.dp
    val cornerRadius2xl = 28.dp
    val cornerRadiusFull = 999.dp

    /**
     * The one card radius: grouped lists' outer corners (inner corners use [cornerRadiusSm]) and
     * every standalone card, hero and pane. Dialogs and sheets use [cornerRadius2xl].
     */
    val groupCornerRadius = 24.dp

    val iconContainerRadius = 12.dp
    val buttonCornerRadius = 50.dp

    val cardPadding = 16.dp
    val cardPaddingSm = 12.dp
    val screenPaddingHorizontal = 16.dp
    val screenPaddingVertical = 12.dp

    /** Gap between a screen's top bar and its first content row (every list screen). */
    val screenContentTop = spacingMd

    /** Bottom content inset that keeps the last row scrollable clear of a floating action button. */
    val fabClearance = 96.dp

    /** Height cap for scrollable lists inside bottom sheets and editors. */
    val sheetContentMaxHeight = 560.dp

    val iconSizeXs = 16.dp
    val iconSizeSm = 20.dp
    val iconSizeMd = 24.dp
    val iconSizeLg = 32.dp
    val iconSizeXl = 48.dp
    val iconContainerSm = 32.dp
    val iconContainerMd = 40.dp
    val iconContainerLg = 48.dp

    val touchTargetMin = 48.dp
    val brandMarkSize = 120.dp
    val buttonHeight = 56.dp
    val buttonHeightSm = 40.dp
    val dividerThickness = 0.5.dp
    val dividerThicknessBold = 1.dp
}
