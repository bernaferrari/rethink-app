package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile)) {
            UsageTile(downloadLabel, overview.download, MaterialTheme.colorScheme.primary, UsageTilePosition.Leading, Modifier.weight(1f))
            UsageTile(uploadLabel, overview.upload, MaterialTheme.colorScheme.tertiary, UsageTilePosition.Middle, Modifier.weight(1f))
            UsageTile(connectionsLabel, overview.connections, MaterialTheme.colorScheme.onSurface, UsageTilePosition.Trailing, Modifier.weight(1f))
        }
    }
}

private enum class UsageTilePosition { Leading, Middle, Trailing }

private fun usageTileShape(position: UsageTilePosition): RoundedCornerShape {
    val outer = SharedDimensions.cornerRadius4xl
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
        Column(
            Modifier.padding(horizontal = SharedDimensions.cardPaddingSm, vertical = SharedDimensions.cardPaddingSm),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingGridTile),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
