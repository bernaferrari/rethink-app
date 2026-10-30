package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class RethinkListItemColors(
    val leadingIconTint: Color,
    val leadingIconContainerColor: Color?,
    val defaultContainerColor: Color,
    val highlightContainerColor: Color,
)

@Composable
fun rethinkListItemColors(
    destructive: Boolean = false,
    leadingIconTint: Color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color? = null,
    defaultContainerColor: Color = LocalRethinkListContainerColor.current ?: MaterialTheme.colorScheme.surfaceContainerLow,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
): RethinkListItemColors = RethinkListItemColors(
    leadingIconTint, leadingIconContainerColor, defaultContainerColor, highlightContainerColor,
)
