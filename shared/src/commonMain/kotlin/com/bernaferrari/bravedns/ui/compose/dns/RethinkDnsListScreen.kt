/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.compose.theme.RethinkPill
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillDefaults
import com.bernaferrari.bravedns.ui.compose.theme.RethinkPillSize
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.WindowWidthClass
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.rethinkSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen

enum class RethinkDnsCapability {
    Fast,
    Private,
    Secure,
    Anonymous,
}

data class RethinkDnsProtocol(
    val id: Int,
    val label: String,
    val title: String,
    val capabilities: List<RethinkDnsCapability>,
    val icon: ImageVector = MaterialSymbols.Filled.Dns,
)

data class RethinkDnsListStrings(
    val title: String,
    val subtitle: String,
    val configure: String,
    val fast: String,
    val private: String,
    val secure: String,
    val anonymous: String,
    val notWorking: String = "",
)

/** Target-neutral DNS protocol chooser; the host owns the selected protocol and navigation. */
@Composable
fun RethinkDnsListScreen(
    protocols: List<RethinkDnsProtocol>,
    selectedProtocolId: Int?,
    selectedProtocolWorking: Boolean,
    strings: RethinkDnsListStrings,
    onProtocolClick: (RethinkDnsProtocol) -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        subtitle = strings.subtitle,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        rethinkSection(strings.configure) {
            RethinkAdaptiveWidth { widthClass ->
                DnsProtocolGrid(
                    protocols = protocols,
                    selectedProtocolId = selectedProtocolId,
                    selectedProtocolWorking = selectedProtocolWorking,
                    strings = strings,
                    onProtocolClick = onProtocolClick,
                    widthClass = widthClass,
                )
            }
        }
    }
}

@Composable
private fun DnsProtocolGrid(
    protocols: List<RethinkDnsProtocol>,
    selectedProtocolId: Int?,
    selectedProtocolWorking: Boolean,
    strings: RethinkDnsListStrings,
    onProtocolClick: (RethinkDnsProtocol) -> Unit,
    widthClass: WindowWidthClass,
) {
    val columns = if (widthClass.isExpanded) 3 else 2
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        protocols.chunked(columns).forEach { row ->
            // Intrinsic height lets every card in a row stretch to the tallest one.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                row.forEach { protocol ->
                    DnsProtocolCard(
                        protocol = protocol,
                        selected = selectedProtocolId == protocol.id,
                        selectedWorking = selectedProtocolWorking,
                        strings = strings,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onProtocolClick(protocol) },
                    )
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DnsProtocolCard(
    protocol: RethinkDnsProtocol,
    selected: Boolean,
    selectedWorking: Boolean,
    strings: RethinkDnsListStrings,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // Selection is a calm tonal shift (secondaryContainer + check), not a saturated fill.
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val cardShape = RoundedCornerShape(SharedDimensions.groupCornerRadius)
    // Only show the long name when it adds information beyond the short label
    // (e.g. "DoH" → "DNS-over-HTTPS"), never "DNSCrypt / DNSCrypt".
    val secondary = protocol.title.takeUnless { it.isRedundantWith(protocol.label) }
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.clip(cardShape),
        shape = cardShape,
        color = containerColor,
        tonalElevation = SharedDimensions.spacingNone,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SharedDimensions.cardPaddingSm),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bare icon: a backplate on every card carried no state.
                Icon(protocol.icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(SharedDimensions.iconSizeMd))
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(
                        MaterialSymbols.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(SharedDimensions.iconSizeSm),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(
                    protocol.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                secondary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // One line of chips: whatever does not fit collapses into a "+N" chip instead of
            // wrapping a lone chip onto a second line.
            DnsCapabilityChipRow(protocol.capabilities, strings)
            if (selected && !selectedWorking && strings.notWorking.isNotBlank()) {
                // A failing resolver is a status, in the status colour, not a footnote.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
                ) {
                    Icon(MaterialSymbols.Filled.Warning, null, tint = RethinkStatusColors.blocked, modifier = Modifier.size(SharedDimensions.iconSizeXs))
                    Text(
                        strings.notWorking,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RethinkStatusColors.blocked,
                    )
                }
            }
        }
    }
}

@Composable
private fun DnsCapabilityChipRow(capabilities: List<RethinkDnsCapability>, strings: RethinkDnsListStrings) {
    if (capabilities.isEmpty()) return
    // One neutral tag style: capabilities are facts, not statuses, so they don't each get a colour.
    val tagColors = RethinkPillDefaults.neutral()
    val gap = SharedDimensions.spacingXs
    Layout(
        content = {
            capabilities.forEach { capability -> RethinkPill(strings.labelFor(capability), colors = tagColors, size = RethinkPillSize.Small) }
            // One candidate overflow chip per possible hidden count; only the needed one is placed.
            for (hidden in 1..capabilities.size) RethinkPill("+$hidden", colors = tagColors, size = RethinkPillSize.Small)
        },
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val chips = measurables.take(capabilities.size).map { it.measure(loose) }
        val overflow = measurables.drop(capabilities.size).map { it.measure(loose) }
        val gapPx = gap.roundToPx()
        fun widthOf(count: Int): Int = chips.take(count).sumOf { it.width } + gapPx * (count - 1).coerceAtLeast(0)
        var shown = chips.size
        if (widthOf(shown) > constraints.maxWidth) {
            shown = (chips.size - 1 downTo 0).firstOrNull { count ->
                val tail = overflow[chips.size - count - 1].width + if (count > 0) gapPx else 0
                widthOf(count) + tail <= constraints.maxWidth
            } ?: 0
        }
        val placed = chips.take(shown) + if (shown < chips.size) listOf(overflow[chips.size - shown - 1]) else emptyList()
        val height = placed.maxOfOrNull { it.height } ?: 0
        val width = (placed.sumOf { it.width } + gapPx * (placed.size - 1).coerceAtLeast(0)).coerceAtMost(constraints.maxWidth)
        layout(width, height) {
            var x = 0
            placed.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + gapPx
            }
        }
    }
}

/** True when one label adds nothing to the other ("DNSCrypt"/"DNSCrypt", "Rethink"/"RethinkDNS"). */
internal fun String.isRedundantWith(other: String): Boolean {
    val a = filter(Char::isLetterOrDigit).lowercase()
    val b = other.filter(Char::isLetterOrDigit).lowercase()
    return a.isEmpty() || a.contains(b) || b.contains(a)
}

private fun RethinkDnsListStrings.labelFor(capability: RethinkDnsCapability): String = when (capability) {
    RethinkDnsCapability.Fast -> fast
    RethinkDnsCapability.Private -> private
    RethinkDnsCapability.Secure -> secure
    RethinkDnsCapability.Anonymous -> anonymous
}
