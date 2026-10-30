/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Container + content pair of a [RethinkPill], plus the label weight that goes with it: verdicts,
 * statuses and emphasized counts read SemiBold, plain metadata Medium. Build one through
 * [RethinkPillDefaults].
 */
@Immutable
data class RethinkPillColors(val container: Color, val content: Color, val fontWeight: FontWeight = FontWeight.Medium)

/**
 * Pill density.
 * - [Small]: tags inside a dense row's supporting line (log type, app status, event source).
 * - [Medium]: standalone counts, statuses and metadata.
 * - [Large]: header readouts (home Live/Off, app-info verdict, statistics count).
 */
enum class RethinkPillSize { Small, Medium, Large }

object RethinkPillDefaults {
    /** Neutral metadata (row/column counts, endpoint, event type). */
    @Composable @ReadOnlyComposable
    fun neutral(): RethinkPillColors = RethinkPillColors(
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    /** A status/accent color (usually from [RethinkStatusColors]) as text on a light wash of itself. */
    @Composable @ReadOnlyComposable
    fun status(accent: Color): RethinkPillColors = RethinkPillColors(
        container = accent.copy(alpha = STATUS_CONTAINER_ALPHA),
        content = accent,
        fontWeight = FontWeight.SemiBold,
    )

    /** Emphasized counts (active filters, section and result counts): the one count colour. */
    @Composable @ReadOnlyComposable
    fun primary(): RethinkPillColors = RethinkPillColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
    )

    /** Selection/summary information (protocol, "N selected", config chips). */
    @Composable @ReadOnlyComposable
    fun secondary(): RethinkPillColors = RethinkPillColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    /** Routing/proxy information. */
    @Composable @ReadOnlyComposable
    fun tertiary(): RethinkPillColors = RethinkPillColors(
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
    )

    /** A verdict: allowed traffic, on the semantic green container. */
    @Composable @ReadOnlyComposable
    fun allowed(): RethinkPillColors = RethinkPillColors(
        container = RethinkStatusColors.allowedContainer,
        content = RethinkStatusColors.onAllowedContainer,
        fontWeight = FontWeight.SemiBold,
    )

    /** A verdict: blocked traffic, on the error container. */
    @Composable @ReadOnlyComposable
    fun blocked(): RethinkPillColors = RethinkPillColors(
        container = RethinkStatusColors.blockedContainer,
        content = RethinkStatusColors.onBlockedContainer,
        fontWeight = FontWeight.SemiBold,
    )

    /**
     * Facts on a coloured hero card (resolver, protocol, version): [content] on a faint wash of
     * itself, so the pill takes the card's own colour in every state. Use [RethinkPillSize.Large].
     */
    fun onContainer(content: Color): RethinkPillColors = RethinkPillColors(
        container = content.copy(alpha = ON_CONTAINER_ALPHA),
        content = content,
        fontWeight = FontWeight.SemiBold,
    )

    private const val STATUS_CONTAINER_ALPHA = 0.14f
    private const val ON_CONTAINER_ALPHA = 0.1f
}

/**
 * The one display-only pill (badge, tag, count, status). Never interactive: filter and menu
 * chips are [RethinkFilterChip] / FilterChip. Text stays on one line and ellipsizes, so pass
 * `Modifier.weight(1f, fill = false)` when it shares a row with other content.
 */
@Composable
fun RethinkPill(
    text: String,
    modifier: Modifier = Modifier,
    colors: RethinkPillColors = RethinkPillDefaults.neutral(),
    size: RethinkPillSize = RethinkPillSize.Medium,
    leadingIcon: ImageVector? = null,
    /** A status dot before the text; ignored when [leadingIcon] is set. */
    dotColor: Color? = null,
    fontWeight: FontWeight = colors.fontWeight,
    monospace: Boolean = false,
    /** Long free-form values (proxy details) may wrap instead of ellipsizing. */
    maxLines: Int = 1,
) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusFull),
        color = colors.container,
        contentColor = colors.content,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(size.padding(hasLeadingIcon = leadingIcon != null)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (leadingIcon != null) SharedDimensions.spacingXs else SharedDimensions.spacingSm),
        ) {
            when {
                leadingIcon != null -> Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(SharedDimensions.iconSizeXs))
                dotColor != null -> Box(Modifier.size(SharedDimensions.spacingSm).background(dotColor, CircleShape))
            }
            Text(
                text = text,
                style = size.textStyle().copy(fontFeatureSettings = "tnum"),
                fontWeight = fontWeight,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = colors.content,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable @ReadOnlyComposable
private fun RethinkPillSize.textStyle(): TextStyle = when (this) {
    RethinkPillSize.Small -> MaterialTheme.typography.labelSmall
    RethinkPillSize.Medium -> MaterialTheme.typography.labelMedium
    RethinkPillSize.Large -> MaterialTheme.typography.labelLarge
}

private fun RethinkPillSize.padding(hasLeadingIcon: Boolean): PaddingValues = when (this) {
    RethinkPillSize.Small -> PaddingValues(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingGridTile)
    RethinkPillSize.Medium -> PaddingValues(horizontal = SharedDimensions.spacingSmMd, vertical = SharedDimensions.spacingXs)
    RethinkPillSize.Large -> PaddingValues(
        start = if (hasLeadingIcon) SharedDimensions.spacingSmMd else SharedDimensions.spacingMd,
        end = SharedDimensions.spacingMd,
        top = SharedDimensions.spacingXs + SharedDimensions.spacingGridTile,
        bottom = SharedDimensions.spacingXs + SharedDimensions.spacingGridTile,
    )
}
