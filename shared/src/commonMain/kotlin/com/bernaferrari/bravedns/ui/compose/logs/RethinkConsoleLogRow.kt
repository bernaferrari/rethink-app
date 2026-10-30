/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedRow

data class RethinkConsoleLogItem(
    val level: Char,
    val timestamp: String,
    val message: String,
)

/** Severity buckets for console lines; the glyph and tint carry the level, so it is shown once. */
enum class RethinkConsoleLogSeverity { Debug, Info, Warning, Error }

fun rethinkConsoleLogSeverity(level: Char): RethinkConsoleLogSeverity = when (level.uppercaseChar()) {
    'E', 'F', 'A' -> RethinkConsoleLogSeverity.Error
    'W' -> RethinkConsoleLogSeverity.Warning
    'I' -> RethinkConsoleLogSeverity.Info
    else -> RethinkConsoleLogSeverity.Debug
}

private val RethinkConsoleLogSeverity.icon: ImageVector
    get() = when (this) {
        RethinkConsoleLogSeverity.Error -> MaterialSymbols.Filled.Cancel
        RethinkConsoleLogSeverity.Warning -> MaterialSymbols.Filled.Warning
        RethinkConsoleLogSeverity.Info -> MaterialSymbols.Filled.Info
        RethinkConsoleLogSeverity.Debug -> MaterialSymbols.Filled.BugReport
    }

@Composable
private fun RethinkConsoleLogSeverity.tint(): Color = when (this) {
    RethinkConsoleLogSeverity.Error -> RethinkStatusColors.blocked
    RethinkConsoleLogSeverity.Warning -> RethinkStatusColors.mixed
    RethinkConsoleLogSeverity.Info -> RethinkStatusColors.allowed
    RethinkConsoleLogSeverity.Debug -> RethinkStatusColors.idle
}

/**
 * Portable console-log row; only time formatting and data retrieval remain platform-specific.
 *
 * @param position grouped-list position when the host renders rows as one connected group;
 * null keeps a compact standalone shape for dense paged lists.
 * @param severityDescription accessibility label for the severity glyph (e.g. "Warning").
 */
@Composable
fun RethinkConsoleLogRow(
    item: RethinkConsoleLogItem,
    modifier: Modifier = Modifier,
    position: CardPosition? = null,
    severityDescription: String? = null,
) {
    val severity = rethinkConsoleLogSeverity(item.level)
    val tint = severity.tint()
    val shape = position?.let(::rethinkGroupedListShape) ?: RoundedCornerShape(SharedDimensions.groupCornerRadius)
    Surface(
        modifier = if (position != null) modifier.rethinkGroupedRow(position) else modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSmMd),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
            verticalAlignment = Alignment.Top,
        ) {
            RethinkIconWell(
                icon = severity.icon,
                contentDescription = severityDescription,
                tint = tint,
                containerColor = RethinkIconWellDefaults.tinted(tint),
                size = SharedDimensions.iconSizeMd,
                iconSize = SharedDimensions.iconSizeXs,
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusSm),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(item.timestamp, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    item.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (severity == RethinkConsoleLogSeverity.Error) tint else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
