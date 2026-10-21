package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

data class RethinkUsageOverview(
    val download: String,
    val upload: String,
    val connections: String,
)

/**
 * Usage totals as one connected tile group (download first), matching the grouped-list surface
 * language instead of a bordered card.
 */
@Composable
fun RethinkUsageOverviewCard(
    overview: RethinkUsageOverview,
    overallLabel: String,
    downloadLabel: String,
    uploadLabel: String,
    connectionsLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title = overallLabel)
        // IntrinsicSize.Min + fillMaxHeight keeps the three tiles equal height even when one
        // value or label wraps (large font scales, long translations).
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            UsageTile(downloadLabel, overview.download, MaterialTheme.colorScheme.primary, UsageTilePosition.Leading, Modifier.weight(1f).fillMaxHeight())
            UsageTile(uploadLabel, overview.upload, MaterialTheme.colorScheme.tertiary, UsageTilePosition.Middle, Modifier.weight(1f).fillMaxHeight())
            UsageTile(connectionsLabel, overview.connections, MaterialTheme.colorScheme.onSurface, UsageTilePosition.Trailing, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private enum class UsageTilePosition { Leading, Middle, Trailing }

private fun usageTileShape(position: UsageTilePosition): RoundedCornerShape {
    val outer = SharedDimensions.groupCornerRadius
    val inner = SharedDimensions.cornerRadiusSm
    return RoundedCornerShape(
        topStart = if (position == UsageTilePosition.Leading) outer else inner,
        bottomStart = if (position == UsageTilePosition.Leading) outer else inner,
        topEnd = if (position == UsageTilePosition.Trailing) outer else inner,
        bottomEnd = if (position == UsageTilePosition.Trailing) outer else inner,
    )
}

@Composable
private fun UsageTile(label: String, value: String, valueColor: Color, position: UsageTilePosition, modifier: Modifier) {
    Surface(modifier = modifier, shape = usageTileShape(position), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        // The number is the hero; the label is a quiet caption beneath it. Tabular numerals keep
        // the three values visually aligned as they tick between windows.
        Column(
            Modifier.padding(horizontal = SharedDimensions.cardPaddingSm, vertical = SharedDimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = MaterialTheme.typography.titleSmall.fontSize,
                    maxFontSize = MaterialTheme.typography.titleLarge.fontSize,
                ),
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
