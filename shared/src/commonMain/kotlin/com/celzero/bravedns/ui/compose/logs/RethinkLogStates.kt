package com.celzero.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

@Composable
fun RethinkLogLoadingState(label: String) = LogStateSurface {
    Row(Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingLg), horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RethinkLogEmptyState(label: String) = LogStateSurface {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingXl, vertical = SharedDimensions.spacingLg))
}

@Composable
fun RethinkLogErrorState(message: String, retryLabel: String, onRetry: () -> Unit) = LogStateSurface {
    Row(Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingLg, vertical = SharedDimensions.spacingMd), verticalAlignment = Alignment.CenterVertically) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text(retryLabel) }
    }
}

@Composable
private fun LogStateSurface(content: @Composable () -> Unit) = Surface(
    shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl), color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingSm), content = content,
)
