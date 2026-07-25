package com.celzero.bravedns.ui.compose.statistics

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

data class RethinkUsageOverview(
    val download: String,
    val upload: String,
    val connections: String,
)

@Composable
fun RethinkUsageOverviewCard(
    overview: RethinkUsageOverview,
    overallLabel: String,
    downloadLabel: String,
    uploadLabel: String,
    connectionsLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(overallLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsagePill(downloadLabel, overview.download, MaterialTheme.colorScheme.primaryContainer.copy(alpha = .44f), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                UsagePill(uploadLabel, overview.upload, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .42f), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                UsagePill(connectionsLabel, overview.connections, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsagePill(label: String, value: String, container: Color, valueColor: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg), color = container) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
