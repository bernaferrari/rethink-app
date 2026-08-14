/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Shared pause renderer. The host owns VPN state and the repeating long-press action; this file
 * owns the complete visual and touch treatment on every Compose target.
 */
package com.bernaferrari.bravedns.ui.compose.home

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.delay

enum class RethinkPauseAdjustment { Increase, Decrease }

data class RethinkPauseState(
    val timerText: String,
    val timerDescription: String? = null,
)

data class RethinkPauseStrings(
    val title: String,
    val pauseLabel: String,
    val resume: String,
    val decreaseDuration: String = "Decrease duration",
    val increaseDuration: String = "Increase duration",
)

@Composable
fun RethinkPauseScreen(
    state: RethinkPauseState,
    strings: RethinkPauseStrings,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onResume: () -> Unit,
    onAutoAdjustmentStart: (RethinkPauseAdjustment) -> Unit = {},
    onAutoAdjustmentStop: () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharedDimensions.cornerRadius5xl),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SharedDimensions.spacingXl,
                            vertical = SharedDimensions.spacing2xl,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    Text(
                        text = strings.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.timerText,
                        style = MaterialTheme.typography.displayLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (!state.timerDescription.isNullOrBlank()) {
                        Text(
                            text = state.timerDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RethinkPauseAdjustButton(
                            icon = MaterialSymbols.Filled.Remove,
                            contentDescription = strings.decreaseDuration,
                            onClick = onDecrease,
                            onLongClick = { onAutoAdjustmentStart(RethinkPauseAdjustment.Decrease) },
                            onRelease = onAutoAdjustmentStop,
                        )
                        RethinkPauseAdjustButton(
                            icon = MaterialSymbols.Filled.Add,
                            contentDescription = strings.increaseDuration,
                            onClick = onIncrease,
                            onLongClick = { onAutoAdjustmentStart(RethinkPauseAdjustment.Increase) },
                            onRelease = onAutoAdjustmentStop,
                        )
                    }
                }
            }

            Spacer(Modifier.height(SharedDimensions.spacingXl))
            FilledTonalButton(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SharedDimensions.buttonHeight),
                shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
            ) {
                Icon(
                    MaterialSymbols.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(SharedDimensions.iconSizeSm),
                )
                Spacer(Modifier.width(SharedDimensions.spacingSm))
                Text(
                    strings.resume,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RethinkPauseAdjustButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRelease: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var repeating by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(400)
            repeating = true
            onLongClick()
        } else {
            if (repeating) onRelease()
            repeating = false
        }
    }
    IconButton(
        onClick = { if (!repeating) onClick() },
        interactionSource = interactionSource,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(SharedDimensions.iconSizeMd),
        )
    }
}
