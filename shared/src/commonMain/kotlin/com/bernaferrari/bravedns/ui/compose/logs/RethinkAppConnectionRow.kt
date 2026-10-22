/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWellDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkGroupedListShape

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
    position: CardPosition = CardPosition.Single,
) {
    val accent = when (item.ruleState) {
        RethinkConnectionRuleState.Block -> RethinkStatusColors.blocked
        RethinkConnectionRuleState.Trust -> RethinkStatusColors.allowed
        RethinkConnectionRuleState.Bypass -> RethinkStatusColors.mixed
        RethinkConnectionRuleState.None -> RethinkStatusColors.idle
    }
    // Grouped-list row: the host's list supplies the 2dp gap, the shape carries the grouping.
    val rowShape = rethinkGroupedListShape(position)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().clip(rowShape),
        shape = rowShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    RethinkIconWell(
                        containerColor = RethinkIconWellDefaults.tinted(accent),
                        contentColor = accent,
                        size = SharedDimensions.iconSizeMd,
                        shape = CircleShape,
                    ) {
                        Text(item.flag, style = MaterialTheme.typography.labelMedium)
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
                RethinkPill(item.count, colors = RethinkPillDefaults.status(accent), fontWeight = FontWeight.SemiBold)
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
