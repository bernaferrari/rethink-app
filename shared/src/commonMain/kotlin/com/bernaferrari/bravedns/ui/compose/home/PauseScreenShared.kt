/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Shared pause renderer. The host owns VPN state and the repeating long-press action; this file
 * owns the complete visual and touch treatment on every Compose target.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.delay

enum class RethinkPauseAdjustment { Increase, Decrease }

data class RethinkPauseState(
    val timerText: String,
    val timerDescription: String? = null,
)

data class RethinkPauseStrings(
    val title: String,
    val resume: String,
    val decreaseDuration: String,
    val increaseDuration: String,
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
                shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
                color = MaterialTheme.colorScheme.surfaceContainer,
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
                        // Tabular figures keep the digits from jittering as the countdown ticks,
                        // without switching to a monospace face.
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
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
                    val decreaseInteraction = remember { MutableInteractionSource() }
                    val increaseInteraction = remember { MutableInteractionSource() }
                    Spacer(Modifier.height(SharedDimensions.spacingSm))
                    ButtonGroup(
                        overflowIndicator = {},
                        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm, Alignment.CenterHorizontally),
                    ) {
                        customItem(
                            buttonGroupContent = {
                                RethinkPauseAdjustButton(
                                    icon = MaterialSymbols.Filled.Remove,
                                    contentDescription = strings.decreaseDuration,
                                    interactionSource = decreaseInteraction,
                                    onClick = onDecrease,
                                    onLongClick = { onAutoAdjustmentStart(RethinkPauseAdjustment.Decrease) },
                                    onRelease = onAutoAdjustmentStop,
                                    modifier = Modifier.animateWidth(decreaseInteraction),
                                )
                            },
                            menuContent = {},
                        )
                        customItem(
                            buttonGroupContent = {
                                RethinkPauseAdjustButton(
                                    icon = MaterialSymbols.Filled.Add,
                                    contentDescription = strings.increaseDuration,
                                    interactionSource = increaseInteraction,
                                    onClick = onIncrease,
                                    onLongClick = { onAutoAdjustmentStart(RethinkPauseAdjustment.Increase) },
                                    onRelease = onAutoAdjustmentStop,
                                    modifier = Modifier.animateWidth(increaseInteraction),
                                )
                            },
                            menuContent = {},
                        )
                    }
                }
            }

            Spacer(Modifier.height(SharedDimensions.spacingXl))
            // Resuming is the screen's one primary action: a filled button, not tonal.
            Button(
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
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    FilledTonalIconButton(
        onClick = { if (!repeating) onClick() },
        shapes = IconButtonDefaults.shapes(
            shape = IconButtonDefaults.mediumRoundShape,
            pressedShape = IconButtonDefaults.mediumPressedShape,
        ),
        interactionSource = interactionSource,
        modifier = modifier.size(IconButtonDefaults.mediumContainerSize()),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}
