/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** Portable connection-summary row. Hosts provide platform artwork and the navigation action. */
@Composable
fun RethinkStatisticsSummaryItem(
    title: String,
    subtitle: String?,
    countText: String,
    flagText: String? = null,
    showProgress: Boolean,
    progress: Float,
    progressColor: Color,
    showIndicator: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    position: CardPosition = CardPosition.Single,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val rowShape = rethinkGroupedListShape(position)
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().padding(top = if (position == CardPosition.First || position == CardPosition.Single) SharedDimensions.spacingNone else SharedDimensions.spacingGridTile).clip(rowShape),
        shape = rowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.cardPaddingSm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(SharedDimensions.iconContainerMd), contentAlignment = Alignment.Center) {
                    when {
                        !flagText.isNullOrEmpty() -> Text(flagText, style = MaterialTheme.typography.titleLarge)
                        leadingContent != null -> leadingContent()
                    }
                }

                Spacer(Modifier.width(SharedDimensions.spacingMd))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showProgress) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingXs),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }

                Spacer(Modifier.width(SharedDimensions.spacingSm))
                Text(countText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (showIndicator && onClick != null) {
                    Spacer(Modifier.width(SharedDimensions.spacingSm))
                    Icon(
                        MaterialSymbols.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(SharedDimensions.iconSizeMd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
