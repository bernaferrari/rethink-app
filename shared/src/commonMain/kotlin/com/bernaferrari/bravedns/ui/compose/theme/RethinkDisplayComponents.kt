/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.PaddingValues

/**
 * The one title block for bottom sheets: titleLarge title, optional bodyMedium subtitle, optional
 * leading visual (icon well) and trailing action.
 */
@Composable
fun RethinkSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

enum class RethinkMetricSize { Default, Large }

/**
 * A number with a quiet caption beneath it (usage tiles, activity counters). Tabular numerals keep
 * values aligned as they tick; [RethinkMetricSize.Default] steps the value down to fit narrow tiles.
 */
@Composable
fun RethinkMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    size: RethinkMetricSize = RethinkMetricSize.Default,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    labelMaxLines: Int = 2,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(
            if (size == RethinkMetricSize.Default) SharedDimensions.spacingXs else SharedDimensions.spacingNone,
        ),
    ) {
        val typography = MaterialTheme.typography
        Text(
            value,
            style = (if (size == RethinkMetricSize.Large) typography.headlineSmall else typography.titleLarge)
                .copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = if (size == RethinkMetricSize.Default) {
                TextAutoSize.StepBased(minFontSize = typography.titleSmall.fontSize, maxFontSize = typography.titleLarge.fontSize)
            } else {
                null
            },
        )
        Text(label, style = typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = labelMaxLines)
    }
}

/** Leading mark of a [RethinkStatusLabel]. */
@Immutable
sealed interface RethinkStatusIndicator {
    data object None : RethinkStatusIndicator
    data class Dot(val size: Dp = SharedDimensions.spacingSm) : RethinkStatusIndicator
    data class Icon(val icon: ImageVector) : RethinkStatusIndicator
}

/** A single-line colored status (Active, Reachable, Allowed) with an optional dot or icon. */
@Composable
fun RethinkStatusLabel(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    indicator: RethinkStatusIndicator = RethinkStatusIndicator.Dot(),
    style: TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
    ) {
        when (indicator) {
            RethinkStatusIndicator.None -> Unit
            is RethinkStatusIndicator.Dot -> Box(Modifier.size(indicator.size).background(color, CircleShape))
            is RethinkStatusIndicator.Icon -> Icon(indicator.icon, null, tint = color, modifier = Modifier.size(SharedDimensions.iconSizeSm))
        }
        Text(label, style = style, fontWeight = fontWeight, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The one standalone card (overview, notice, warning, stats strip): the grouped-list radius and
 * row tone, so a lone card belongs to the same family as the grouped rows around it. Inside
 * sheets it follows [LocalRethinkListContainerColor] one tone up; pass [color] for a tonal notice
 * (its content colour follows).
 */
@Composable
fun RethinkContainedCard(
    modifier: Modifier = Modifier,
    color: Color = LocalRethinkListContainerColor.current ?: MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = contentColorFor(color).takeOrElse { LocalContentColor.current },
    contentPadding: PaddingValues = PaddingValues(SharedDimensions.cardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SharedDimensions.spacingSm),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = color,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
