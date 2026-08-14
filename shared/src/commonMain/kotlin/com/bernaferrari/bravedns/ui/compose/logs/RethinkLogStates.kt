package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.common.RethinkLoadingState
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

@Composable
fun RethinkLogLoadingState(label: String) = LogStateSurface {
    RethinkLoadingState(label = label)
}

@Composable
fun RethinkLogEmptyState(label: String) = LogStateSurface {
    RethinkEmptyState(
        icon = MaterialSymbols.Filled.Info,
        title = label,
        message = "",
    )
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
