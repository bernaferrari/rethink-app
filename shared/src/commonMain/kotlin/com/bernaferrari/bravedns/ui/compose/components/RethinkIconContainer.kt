/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** Consistent tinted icon vessel for portable settings and information rows. */
@Composable
fun RethinkSharedIconContainer(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
        color = accent.copy(alpha = 0.16f),
        modifier = modifier.size(SharedDimensions.iconContainerMd),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
