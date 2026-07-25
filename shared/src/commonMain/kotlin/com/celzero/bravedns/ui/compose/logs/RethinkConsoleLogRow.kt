/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkConsoleLogItem(
    val level: Char,
    val timestamp: String,
    val message: String,
)

/** Portable console-log row; only time formatting and data retrieval remain platform-specific. */
@Composable
fun RethinkConsoleLogRow(
    item: RethinkConsoleLogItem,
    modifier: Modifier = Modifier,
) {
    val color = when (item.level) {
        'W' -> MaterialTheme.colorScheme.tertiary
        'E' -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            SharedDimensions.dividerThickness,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSmMd),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(SharedDimensions.iconSizeMd),
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusMd),
                color = color.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.level.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(item.timestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.message, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}
