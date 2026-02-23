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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions.Elevation
import com.celzero.bravedns.ui.compose.theme.Dimensions.Opacity

// ==================== ANIMATED SECTIONS ====================

/**
 * Animated section that reveals content with fade and slide animation.
 * Similar to animations seen in Statistics screens and reference UI.
 */
@Composable
fun RethinkAnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = fadeOut(
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + slideOutVertically(
            targetOffsetY = { -it / 4 },
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessLow
            )
        )
    ) {
        content()
    }
}

/**
 * Animated section with expand/collapse support for collapsible content.
 */
@Composable
fun AnimatedExpandSection(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            )
        ),
        exit = shrinkVertically(
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    ) {
        content()
    }
}

// ==================== CARDS ====================

/**
 * Standard app card with M3 Expressive corner radius.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.medium),
            onClick = onClick,
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
            content = content
        )
    }
}

/**
 * Card with header section for grouped content.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Dimensions.cardPadding)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                ) {
                    if (titleIcon != null) {
                        Icon(
                            imageVector = titleIcon,
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
                Spacer(modifier = Modifier.height(Dimensions.spacingMd))
            }
            content()
        }
    }
}

/**
 * Modern stat card for displaying statistics with icon, value, label, and optional trend indicator.
 * Uses M3 Expressive tonal palettes and animations.
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    trendValue: String? = null,
    isPositiveTrend: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "statCardScale"
    )

    val cardModifier = if (onClick != null) {
        modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        modifier.scale(scale)
    }

    ElevatedCard(
        modifier = cardModifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = Elevation.low,
            pressedElevation = Elevation.medium
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.cardPadding),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (icon != null) {
                    Surface(
                        shape = RoundedCornerShape(Dimensions.iconContainerRadius),
                        color = iconBackgroundColor.copy(alpha = 0.75f),
                        modifier = Modifier.size(Dimensions.iconContainerMd)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(Dimensions.iconContainerMd)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(Dimensions.iconSizeSm)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(Dimensions.iconContainerMd))
                }

                if (trendValue != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isPositiveTrend) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositiveTrend) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Dimensions.iconSizeXs)
                        )
                        Text(
                            text = trendValue,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPositiveTrend) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingMd))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(Dimensions.spacingXs))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ==================== EMPTY STATES ====================

/**
 * Empty state view with illustration, message, and optional action.
 */
@Composable
fun EmptyStateView(
    modifier: Modifier = Modifier,
    illustration: Painter? = null,
    title: String? = null,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustration != null) {
            Icon(
                painter = illustration,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .alpha(Opacity.MEDIUM),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimensions.spacingXl))
        }

        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimensions.spacingSm))
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(Opacity.MEDIUM)
        )

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Dimensions.spacingXl))
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(Dimensions.buttonCornerRadius)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * Compact empty state for inline use in lists/sections.
 */
@Composable
fun CompactEmptyState(
    modifier: Modifier = Modifier,
    message: String,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.spacingLg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSizeSm).alpha(Opacity.HINT),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingSm))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(Opacity.HINT)
        )
    }
}

// ==================== LIST ITEMS ====================

/**
 * Standard list item with icon, title, description, and optional trailing content.
 * Enhanced with spring press animation, elevation changes, and better visual hierarchy.
 */
@Composable
fun AppListItem(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    title: String,
    description: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "itemScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) Elevation.medium else Elevation.none,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "itemElevation"
    )

    val clickModifier = if (onClick != null) {
        modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        modifier
    }

    Surface(
        modifier = clickModifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.listItemHeight)
                .padding(horizontal = Dimensions.cardPadding, vertical = Dimensions.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || iconPainter != null) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.iconContainerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.size(Dimensions.iconContainerMd)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(Dimensions.iconContainerMd)
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.iconSizeSm)
                            )
                        } else if (iconPainter != null) {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.iconSizeSm)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(Dimensions.spacingMd))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.15).sp
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.sp
                    )
                }
            }

            if (trailing != null) {
                Spacer(modifier = Modifier.width(Dimensions.spacingMd))
                trailing()
            }
        }
    }
}

/**
 * Shared grid tile used by configure/about quick actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkGridTile(
    title: String,
    iconRes: Int,
    accentColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val iconShape = MaterialShapes.Sunny.toShape()

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = iconShape,
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                trailing?.invoke()
            }
        }
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = iconShape,
                    color = accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                trailing?.invoke()
            }
        }
    }
}

// ==================== SECTION HEADERS ====================

enum class RethinkSecondaryActionStyle { TONAL, OUTLINED, TEXT }

@Composable
fun <T> RethinkSegmentedChoiceRow(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillEqually: Boolean = false,
    minHeight: Dp = 0.dp,
    icon: (@Composable (option: T, selected: Boolean) -> Unit)? = null,
    label: @Composable (option: T, selected: Boolean) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selectedOption
            SegmentedButton(
                modifier =
                    Modifier
                        .then(if (fillEqually) Modifier.weight(1f) else Modifier)
                        .heightIn(min = minHeight),
                selected = isSelected,
                onClick = { if (!isSelected) onOptionSelected(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    icon?.invoke(option, isSelected)
                },
                label = {
                    label(option, isSelected)
                }
            )
        }
    }
}

@Composable
fun RethinkTwoOptionSegmentedRow(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp
) {
    RethinkSegmentedChoiceRow(
        options = listOf(true, false),
        selectedOption = leftSelected,
        onOptionSelected = { selected ->
            if (selected) onLeftClick() else onRightClick()
        },
        modifier = modifier,
        fillEqually = true,
        minHeight = minHeight,
        label = { selected, _ ->
            Text(text = if (selected) leftLabel else rightLabel)
        }
    )
}

@Composable
fun RethinkDropdownSelector(
    selectedText: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(Dimensions.cornerRadiusMdLg),
            color = containerColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true }
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Dimensions.spacingLg,
                            vertical = Dimensions.spacingMd
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
fun RethinkConfirmDialog(
    onDismissRequest: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = onDismissRequest,
    isConfirmDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    text: (@Composable (() -> Unit))? = null
) {
    val confirmColors =
        if (isConfirmDestructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title?.let { { Text(text = it) } },
        text = text ?: message?.let { { Text(text = it) } },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = confirmColors, enabled = confirmEnabled) {
                Text(text = confirmText)
            }
        },
        dismissButton =
            if (dismissText != null && onDismiss != null) {
                {
                    TextButton(onClick = onDismiss, enabled = dismissEnabled) {
                        Text(text = dismissText)
                    }
                }
            } else {
                null
            }
    )
}

@Composable
fun RethinkMultiActionDialog(
    onDismissRequest: () -> Unit,
    title: String,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryText: String? = null,
    onTertiary: (() -> Unit)? = null,
    isPrimaryDestructive: Boolean = false,
    text: (@Composable (() -> Unit))? = null
) {
    val primaryColors =
        if (isPrimaryDestructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = text ?: message?.let { { Text(text = it) } },
        confirmButton = {
            TextButton(onClick = onPrimary, colors = primaryColors) {
                Text(text = primaryText)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
                if (secondaryText != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) {
                        Text(text = secondaryText)
                    }
                }
                if (tertiaryText != null && onTertiary != null) {
                    TextButton(onClick = onTertiary) {
                        Text(text = tertiaryText)
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkBottomSheetDragHandle(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimensions.spacingXs, bottom = Dimensions.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(44.dp)
                .height(5.dp),
            shape = RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        ) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = { RethinkBottomSheetDragHandle() },
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Dimensions.screenPaddingHorizontal,
        vertical = Dimensions.spacingSm
    ),
    verticalSpacing: Dp = Dimensions.spacingLg,
    includeBottomSpacer: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = dragHandle
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            content()
            if (includeBottomSpacer) {
                Spacer(modifier = Modifier.height(Dimensions.spacing2xl))
            }
        }
    }
}

@Composable
fun RethinkBottomSheetCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(Dimensions.cornerRadius3xl),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingSm),
            content = content
        )
    }
}

@Composable
fun RethinkBottomSheetActionRow(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    secondaryStyle: RethinkSecondaryActionStyle = RethinkSecondaryActionStyle.TONAL,
    useCardContainer: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.spacingMd,
                        vertical = Dimensions.spacingSmMd
                    ),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSmMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (secondaryText != null && onSecondaryClick != null) {
                when (secondaryStyle) {
                    RethinkSecondaryActionStyle.TONAL -> {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onSecondaryClick,
                            enabled = secondaryEnabled
                        ) {
                            Text(text = secondaryText)
                        }
                    }
                    RethinkSecondaryActionStyle.OUTLINED -> {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onSecondaryClick,
                            enabled = secondaryEnabled
                        ) {
                            Text(text = secondaryText)
                        }
                    }
                    RethinkSecondaryActionStyle.TEXT -> {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = onSecondaryClick,
                            enabled = secondaryEnabled
                        ) {
                            Text(text = secondaryText)
                        }
                    }
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onPrimaryClick,
                    enabled = primaryEnabled
                ) {
                    Text(text = primaryText)
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onPrimaryClick,
                    enabled = primaryEnabled
                ) {
                    Text(text = primaryText)
                }
            }
        }
    }

    if (useCardContainer) {
        RethinkBottomSheetCard(
            modifier = modifier,
            shape = RoundedCornerShape(Dimensions.cornerRadiusXl),
            contentPadding = PaddingValues(0.dp)
        ) {
            content()
        }
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.screenPaddingHorizontal,
                        vertical = Dimensions.spacingXs
                    )
        ) {
            content()
        }
    }
}

/**
 * Section header — M3 Expressive style with more prominent styling and typography.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacingSm
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
            color = color
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = Dimensions.spacingSm, vertical = 0.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    letterSpacing = 0.1.sp
                )
            }
        }
    }
}

/**
 * Section header with optional subtitle for more context.
 */
@Composable
fun SectionHeaderWithSubtitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacingSm
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
                color = color
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = Dimensions.spacingSm, vertical = 0.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        letterSpacing = 0.1.sp
                    )
                }
            }
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(Dimensions.spacingXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp
            )
        }
    }
}

// ==================== DIVIDERS ====================

/**
 * Standard horizontal divider.
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    indent: Dp = Dimensions.spacingNone,
    color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = indent),
        thickness = Dimensions.dividerThickness,
        color = color
    )
}

// ==================== BUTTONS ====================

/**
 * Primary action button — M3 Expressive full-pill shape.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(Dimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(Dimensions.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingSm))
        }
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Secondary action button — pill shape.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(Dimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(Dimensions.buttonCornerRadius)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.iconSizeSm)
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingSm))
        }
        Text(text = text)
    }
}

// ==================== STAT ITEMS ====================

/**
 * Stat display for dashboard cards.
 */
@Composable
fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = Opacity.MEDIUM),
            letterSpacing = 0.2.sp
        )
    }
}

// ==================== ANIMATIONS ====================

/**
 * Animated visibility with fade and scale.
 */
@Composable
fun AnimatedVisibilityFadeScale(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
    ) {
        content()
    }
}

// ==================== EXPRESSIVE LAYOUT PRIMITIVES ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp
            )
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back)
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkLargeTopBar(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    titleStartPadding: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    LargeTopAppBar(
        title = {
            Column(
                modifier = Modifier.padding(start = titleStartPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_navigate_back)
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

enum class CardPosition {
    First, Middle, Last, Single
}

fun cardPositionFor(index: Int, lastIndex: Int): CardPosition {
    return when {
        lastIndex <= 0 -> CardPosition.Single
        index == 0 -> CardPosition.First
        index == lastIndex -> CardPosition.Last
        else -> CardPosition.Middle
    }
}

/**
 * Expressive grouped list container — M3 Expressive card shape.
 */
@Composable
fun RethinkListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        content = content
    )
}

/**
 * Full-width hero header for settings/detail screens — M3 Expressive bold style.
 */
@Composable
fun RethinkScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingIconPainter: Painter? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Dimensions.screenPaddingHorizontal,
                end = Dimensions.screenPaddingHorizontal,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacingSm
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMd)
    ) {
        if (leadingIcon != null || leadingIconPainter != null) {
            Surface(
                shape = RoundedCornerShape(Dimensions.iconContainerRadius),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(Dimensions.iconContainerMd)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(Dimensions.iconContainerMd)
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(Dimensions.iconSizeSm)
                        )
                    } else if (leadingIconPainter != null) {
                        Icon(
                            painter = leadingIconPainter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(Dimensions.iconSizeSm)
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                letterSpacing = (-0.2).sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Expressive list item with spring press animation and tinted icon container.
 */
@Composable
fun RethinkListItem(
    headline: String,
    headlineAnnotated: AnnotatedString? = null,
    supporting: String? = null,
    supportingAnnotated: AnnotatedString? = null,
    modifier: Modifier = Modifier,
    contentOffset: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconPainter: Painter? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    trailing: @Composable (() -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    defaultContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
    showTrailingChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "listItemScale"
    )

    val itemShape = when (position) {
        CardPosition.Single -> RoundedCornerShape(Dimensions.cornerRadius3xl)
        CardPosition.First -> RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        CardPosition.Last -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
        CardPosition.Middle -> RoundedCornerShape(Dimensions.cornerRadiusSm)
    }

    val contentAlpha = if (enabled) 1f else 0.5f
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "listItemHighlight"
    )
    val containerColor = lerp(defaultContainerColor, highlightContainerColor, highlightAlpha)

    Column(modifier = modifier.fillMaxWidth().scale(scale)) {
        Surface(
            shape = itemShape,
            color = containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (position == CardPosition.First || position == CardPosition.Single) 0.dp else 2.dp
                ),
            onClick = onClick ?: {},
            enabled = onClick != null && enabled,
            interactionSource = interactionSource
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = headlineAnnotated ?: AnnotatedString(headline),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        letterSpacing = 0.sp,
                        modifier = contentOffset
                    )
                },
                supportingContent =
                    if (supporting != null || supportingAnnotated != null) {
                        {
                            Text(
                                text = supportingAnnotated ?: AnnotatedString(supporting.orEmpty()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f * contentAlpha),
                                letterSpacing = 0.sp,
                                modifier = contentOffset.then(Modifier.padding(top = Dimensions.spacingXs))
                            )
                        }
                    } else {
                        null
                    },
                leadingContent = {
                    if (leadingIcon != null || leadingIconPainter != null) {
                        Surface(
                            shape = RoundedCornerShape(Dimensions.iconContainerRadius),
                            color = leadingIconContainerColor.copy(alpha = (leadingIconContainerColor.alpha * 0.72f) * contentAlpha),
                            modifier = Modifier.size(Dimensions.iconContainerSm)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(Dimensions.iconContainerSm)
                            ) {
                                if (leadingIcon != null) {
                                    Icon(
                                        imageVector = leadingIcon,
                                        contentDescription = null,
                                        tint = leadingIconTint.copy(alpha = contentAlpha),
                                        modifier = Modifier.size(Dimensions.iconSizeSm)
                                    )
                                } else if (leadingIconPainter != null) {
                                    Icon(
                                        painter = leadingIconPainter,
                                        contentDescription = null,
                                        tint = leadingIconTint.copy(alpha = contentAlpha),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                trailingContent =
                    when {
                        trailing != null -> trailing
                        showTrailingChevron && onClick != null && enabled -> {
                            {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        else -> null
                    },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .clip(itemShape)
                    .padding(horizontal = Dimensions.spacingNone, vertical = 1.dp)
            )
        }
    }
}

@Composable
fun RethinkActionListItem(
    title: String,
    description: String? = null,
    iconRes: Int? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    position: CardPosition = CardPosition.Middle,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val resolvedPainter = iconPainter ?: iconRes?.let { painterResource(id = it) }
    RethinkListItem(
        headline = title,
        supporting = description,
        leadingIcon = icon,
        leadingIconPainter = resolvedPainter,
        leadingIconTint = accentColor,
        leadingIconContainerColor = accentColor.copy(alpha = 0.14f),
        position = position,
        highlighted = highlighted,
        enabled = enabled,
        showTrailingChevron = false,
        trailing = trailing,
        onClick = onClick
    )
}

@Composable
fun RethinkToggleListItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconRes: Int? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    position: CardPosition = CardPosition.Middle,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onRowClick: (() -> Unit)? = null,
    trailingPrefix: @Composable (() -> Unit)? = null
) {
    RethinkActionListItem(
        title = title,
        description = description,
        iconRes = iconRes,
        icon = icon,
        iconPainter = iconPainter,
        accentColor = accentColor,
        position = position,
        highlighted = highlighted,
        enabled = enabled,
        trailing = {
            if (trailingPrefix == null) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    trailingPrefix()
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                        enabled = enabled
                    )
                }
            }
        },
        onClick = onRowClick ?: { onCheckedChange(!checked) }
    )
}

@Composable
fun RethinkRadioListItem(
    title: String,
    description: String? = null,
    selected: Boolean,
    onSelect: () -> Unit,
    iconRes: Int? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    position: CardPosition = CardPosition.Middle,
    highlighted: Boolean = false,
    onInfoClick: (() -> Unit)? = null
) {
    RethinkActionListItem(
        title = title,
        description = description,
        iconRes = iconRes,
        icon = icon,
        iconPainter = iconPainter,
        accentColor = accentColor,
        position = position,
        highlighted = highlighted,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onInfoClick != null) {
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(id = R.string.lbl_info),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                RadioButton(
                    selected = selected,
                    onClick = onSelect
                )
            }
        },
        onClick = onSelect
    )
}

// ==================== SETTINGS COMPONENTS ====================

/**
 * Collapsible settings section with animation.
 */
@Composable
fun CollapsibleSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    headerIcon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "chevronRotation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(
                topStart = if (expanded) Dimensions.cardCornerRadius else Dimensions.cardCornerRadius,
                topEnd = if (expanded) Dimensions.cardCornerRadius else Dimensions.cardCornerRadius,
                bottomStart = if (expanded) 0.dp else Dimensions.cardCornerRadius,
                bottomEnd = if (expanded) 0.dp else Dimensions.cardCornerRadius
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.cardPadding,
                        vertical = Dimensions.spacingMd
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
                ) {
                    if (headerIcon != null) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.iconSizeMd)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.1).sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(Dimensions.iconSizeMd)
                        .graphicsLayer { rotationZ = rotationAngle }
                )
            }
        }

        AnimatedExpandSection(expanded = expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = Dimensions.cardCornerRadius,
                    bottomEnd = Dimensions.cardCornerRadius
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = Dimensions.spacingSm),
                    content = content
                )
            }
        }
    }
}

/**
 * Settings toggle row with animation and proper visual feedback.
 */
@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "toggleRowScale"
    )

    val contentAlpha = if (enabled) 1f else 0.5f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.cardPadding,
                    vertical = Dimensions.spacingMd
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.iconContainerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f * contentAlpha),
                    modifier = Modifier.size(Dimensions.iconContainerSm)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(Dimensions.iconContainerSm)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                            modifier = Modifier.size(Dimensions.iconSizeSm)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Dimensions.spacingMd))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    letterSpacing = 0.sp
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f * contentAlpha),
                        letterSpacing = 0.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(Dimensions.spacingMd))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = 1.1f
                    scaleY = 1.1f
                }
            )
        }
    }
}

/**
 * Settings clickable row with chevron indicator.
 */
@Composable
fun SettingClickableRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "clickableRowScale"
    )

    val contentAlpha = if (enabled) 1f else 0.5f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.cardPadding,
                    vertical = Dimensions.spacingMd
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(Dimensions.iconContainerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f * contentAlpha),
                    modifier = Modifier.size(Dimensions.iconContainerSm)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(Dimensions.iconContainerSm)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                            modifier = Modifier.size(Dimensions.iconSizeSm)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Dimensions.spacingMd))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    letterSpacing = 0.sp
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f * contentAlpha),
                        letterSpacing = 0.sp
                    )
                }
            }

            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * contentAlpha),
                    modifier = Modifier.size(Dimensions.iconSizeMd)
                )
            }
        }
    }
}
