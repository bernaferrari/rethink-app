/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.celzero.bravedns.ui.compose.theme.SharedDimensions

/** Target-neutral presentation state for one app-wise connection entry. */
data class RethinkAppConnectionItem(
    val flag: String,
    val title: String,
    val supporting: String? = null,
    val count: String,
    val ruleState: RethinkConnectionRuleState = RethinkConnectionRuleState.None,
    /** Null omits the lightweight activity indicator. */
    val activity: Float? = null,
)

enum class RethinkConnectionRuleState { None, Trust, Block, Bypass }

/**
 * Expressive, shared row used for domain, IP, and ASN connection histories.
 * Platform adapters map their database objects and rule-manager results into [RethinkAppConnectionItem].
 */
@Composable
fun RethinkAppConnectionRow(
    item: RethinkAppConnectionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (item.ruleState) {
        RethinkConnectionRuleState.Block -> MaterialTheme.colorScheme.error
        RethinkConnectionRuleState.Trust,
        RethinkConnectionRuleState.Bypass -> MaterialTheme.colorScheme.tertiary
        RethinkConnectionRuleState.None -> MaterialTheme.colorScheme.primary
    }
    val rowShape = RoundedCornerShape(SharedDimensions.cardCornerRadiusLarge)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().clip(rowShape),
        shape = rowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            SharedDimensions.dividerThickness,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(SharedDimensions.spacingMd),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                verticalAlignment = Alignment.Top,
            ) {
                if (item.flag.isNotBlank()) {
                    Surface(
                        modifier = Modifier.size(SharedDimensions.iconSizeMd),
                        shape = CircleShape,
                        color = accent.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(item.flag, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    item.supporting?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f)) {
                    Text(
                        item.count,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
                    )
                }
            }
            item.activity?.let { activity ->
                LinearProgressIndicator(
                    progress = { activity.coerceIn(0.04f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}
