@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

@Composable
internal fun RethinkListItemContent(
    headline: String,
    headlineAnnotated: AnnotatedString?,
    supporting: String?,
    supportingAnnotated: AnnotatedString?,
    contentOffset: Modifier,
    leadingIcon: ImageVector?,
    leadingContent: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    colors: RethinkListItemColors,
    headlineColor: Color,
    shape: Shape,
    alpha: Float,
    singleLineHeadline: Boolean,
    showTrailingChevron: Boolean,
) {
    // The expressive ListItem overload centers trailing controls (switches, radios) even on
    // three-line rows, where the legacy one top-aligns them.
    ListItem(
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Text(
                text = headlineAnnotated ?: AnnotatedString(headline),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = headlineColor.copy(alpha = alpha),
                ),
                color = if (headlineAnnotated != null) Color.Unspecified else headlineColor.copy(alpha = alpha),
                maxLines = if (singleLineHeadline) 1 else Int.MAX_VALUE,
                overflow = if (singleLineHeadline) TextOverflow.MiddleEllipsis else TextOverflow.Clip,
                modifier = contentOffset,
            )
        },
        supportingContent = rethinkListSupporting(supporting, supportingAnnotated, contentOffset, alpha),
        // Material's ListItem reserves a leading column whenever it receives a
        // lambda, even when that lambda emits nothing. Keep it null for text-only
        // rows so their content aligns with the card inset instead of a phantom icon.
        leadingContent = rethinkListLeading(leadingIcon, leadingContent, colors.leadingIconTint, colors.leadingIconContainerColor, alpha),
        trailingContent = when {
            trailing != null -> trailing
            showTrailingChevron -> {
                { androidx.compose.material3.Icon(MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(shape).padding(vertical = 1.dp),
    )
}

@Composable
private fun rethinkListSupporting(
    supporting: String?,
    supportingAnnotated: AnnotatedString?,
    contentOffset: Modifier,
    alpha: Float,
): (@Composable () -> Unit)? = if (supporting != null || supportingAnnotated != null) {
    {
        Text(
            text = supportingAnnotated ?: AnnotatedString(supporting.orEmpty()),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            ),
            color = if (supportingAnnotated != null) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = contentOffset.then(Modifier.padding(top = SharedDimensions.spacingXs)),
        )
    }
} else null

@Composable
private fun rethinkListLeading(
    leadingIcon: ImageVector?,
    leadingContent: (@Composable () -> Unit)?,
    leadingIconTint: Color,
    leadingIconContainerColor: Color?,
    alpha: Float,
): (@Composable () -> Unit)? = when {
    leadingContent != null -> leadingContent
    leadingIcon != null && leadingIconContainerColor != null -> {
        {
            RethinkIconWell(containerColor = leadingIconContainerColor, contentColor = leadingIconTint, shape = RoundedCornerShape(SharedDimensions.iconContainerRadius)) {
                androidx.compose.material3.Icon(leadingIcon, null, tint = leadingIconTint.copy(alpha = alpha), modifier = Modifier.size(SharedDimensions.iconSizeSm))
            }
        }
    }
    // Bare icon in the same 40dp slot, so text lines up with rows that do have a well.
    leadingIcon != null -> {
        {
            Box(Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(leadingIcon, null, tint = leadingIconTint.copy(alpha = alpha), modifier = Modifier.size(SharedDimensions.iconSizeMd))
            }
        }
    }
    else -> null
}
