/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Shared pause renderer. The host owns VPN state and the repeating long-press action; this file
 * owns the complete visual and touch treatment on every Compose target.
 */
package com.celzero.bravedns.ui.compose.home

import com.celzero.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

enum class RethinkPauseAdjustment { Increase, Decrease }

data class RethinkPauseState(
    val timerText: String,
    val timerDescription: String? = null,
)

data class RethinkPauseStrings(
    val title: String,
    val pauseLabel: String,
    val resume: String,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RethinkLargeTopBar(title = strings.title) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            Spacer(Modifier.height(SharedDimensions.spacingMd))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharedDimensions.cornerRadius5xl),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingXl, vertical = SharedDimensions.spacing2xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                ) {
                    Surface(
                        shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = strings.pauseLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = 5.dp),
                        )
                    }

                    Spacer(Modifier.height(SharedDimensions.spacingSm))
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

                    Spacer(Modifier.height(SharedDimensions.cornerRadiusXl))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RethinkPauseControlButton(
                            icon = MaterialSymbols.Filled.Remove,
                            size = 52.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            onClick = onDecrease,
                            onLongClick = { onAutoAdjustmentStart(RethinkPauseAdjustment.Decrease) },
                            onRelease = onAutoAdjustmentStop,
                        )
                        Spacer(Modifier.width(SharedDimensions.spacingLg))
                        RethinkPauseControlButton(
                            icon = MaterialSymbols.Filled.Stop,
                            size = 72.dp,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = onResume,
                        )
                        Spacer(Modifier.width(SharedDimensions.spacingLg))
                        RethinkPauseControlButton(
                            icon = MaterialSymbols.Filled.Add,
                            size = 52.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            onClick = onIncrease,
                            onLongClick = { onAutoAdjustmentStart(RethinkPauseAdjustment.Increase) },
                            onRelease = onAutoAdjustmentStop,
                        )
                    }
                }
            }

            ElevatedButton(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().height(SharedDimensions.buttonHeight),
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(MaterialSymbols.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(SharedDimensions.spacingSm))
                Text(strings.resume, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RethinkPauseControlButton(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onRelease: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(size / 3),
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                    onPress = { tryAwaitRelease(); onRelease() },
                )
            },
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.44f))
        }
    }
}
