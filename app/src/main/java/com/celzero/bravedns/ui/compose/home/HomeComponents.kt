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
package com.celzero.bravedns.ui.compose.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions

@Composable
fun DashboardCard(
    title: String,
    iconId: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(Dimensions.cardCornerRadiusLarge))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(
            width = Dimensions.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.Elevation.none)
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
            ) {
                // Tinted circle background for icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = iconId),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSizeMd)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimensions.spacingLg))
            content()
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingXs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StartStopButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "buttonScale"
    )

    val containerColor = if (isPlaying) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val contentColor = if (isPlaying) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val text = if (isPlaying) {
        stringResource(R.string.lbl_stop)
    } else {
        stringResource(R.string.lbl_start)
    }

    val icon = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale)
            .clip(CircleShape) // Capsule shape
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 4.dp // Add some elevation for prominence
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingMd))
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
        }
    }
}
