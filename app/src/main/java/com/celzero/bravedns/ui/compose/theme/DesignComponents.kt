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
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.compose.theme.Dimensions.Elevation
import com.celzero.bravedns.ui.compose.theme.Dimensions.Opacity

/**
 * Reusable design components following Material 3 design guidelines.
 */

// ==================== CARDS ====================

/**
 * Standard app card with consistent styling.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(Dimensions.spacingMd))
            }
            content()
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
            FilledTonalButton(onClick = onAction) {
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
    val clickModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Row(
        modifier = clickModifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.listItemHeight)
            .padding(horizontal = Dimensions.cardPadding, vertical = Dimensions.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading icon
        if (icon != null || iconPainter != null) {
            Box(
                modifier = Modifier
                    .size(Dimensions.iconSizeXl)
                    .padding(Dimensions.spacingSm),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSizeMd)
                    )
                } else if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSizeMd)
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimensions.spacingMd))
        }

        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Trailing content
        if (trailing != null) {
            Spacer(modifier = Modifier.width(Dimensions.spacingMd))
            trailing()
        }
    }
}

// ==================== SECTION HEADERS ====================

/**
 * Section header with optional action button.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Dimensions.spacingSm,
                end = Dimensions.spacingSm,
                top = Dimensions.spacingMd,
                bottom = Dimensions.spacingSm
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
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
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Opacity.LOW)
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
 * Primary action button with consistent styling.
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
        Text(text = text)
    }
}

/**
 * Secondary action button.
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
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = Opacity.MEDIUM)
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
                    initialScale = 0.9f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
               scaleOut(
                   targetScale = 0.9f,
                   animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
               )
    ) {
        content()
    }
}

// Extension for clickable modifier
private val spacerDp = 1.dp // For internal use

// ==================== EXPRESSIVE LAYOUT PRIMITIVES ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_back_24),
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
        )
    )
}

@Composable
fun RethinkListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadiusLarge + 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        border = BorderStroke(
            width = Dimensions.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.spacingXs),
            content = content
        )
    }
}

@Composable
fun RethinkListItem(
    headline: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconPainter: Painter? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val itemShape = RoundedCornerShape(20.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = supporting?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = {
                if (leadingIcon != null || leadingIconPainter != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (leadingIcon != null) {
                                Icon(
                                    imageVector = leadingIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else if (leadingIconPainter != null) {
                                Icon(
                                    painter = leadingIconPainter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = trailing ?: if (onClick != null) {
                {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_right_arrow_white),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            } else {
                null
            },
            colors = ListItemDefaults.colors(
                containerColor = if (onClick != null) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            ),
            modifier = Modifier
                .clip(itemShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = Dimensions.spacingSm, vertical = 1.dp)
        )
        if (showDivider) {
            AppDivider(
                modifier = Modifier.padding(start = 72.dp, end = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}
