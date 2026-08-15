/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.bernaferrari.bravedns.ui.compose.common.RethinkAdaptiveWidth
import com.bernaferrari.bravedns.ui.compose.common.WindowWidthClass
import com.bernaferrari.bravedns.ui.compose.common.isExpanded
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLazyColumnScreenScaffold
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
    RethinkLazyColumnScreenScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = strings.subtitle,
                onBackClick = onBackClick,
            )
        },
        listState = rememberLazyListState(),
        contentPadding = PaddingValues(
            start = SharedDimensions.screenPaddingHorizontal,
            end = SharedDimensions.screenPaddingHorizontal,
            top = SharedDimensions.spacingMd,
            bottom = SharedDimensions.spacingLg,
        ),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
    ) {
        item {
            RethinkAdaptiveWidth { widthClass ->
                RethinkFormSection(strings.configure) {
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
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        protocols.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                row.forEach { protocol ->
                    DnsProtocolCard(
                        protocol = protocol,
                        selected = selectedProtocolId == protocol.id,
                        selectedWorking = selectedProtocolWorking,
                        strings = strings,
                        modifier = Modifier.weight(1f),
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
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val cardShape = RoundedCornerShape(SharedDimensions.cornerRadiusXl)
    Surface(
        modifier = modifier.clip(cardShape),
        shape = cardShape,
        color = containerColor,
        tonalElevation = SharedDimensions.spacingNone,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.spacingMd),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
        ) {
            Surface(
                shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
                color = titleColor.copy(alpha = 0.12f),
                modifier = Modifier.size(SharedDimensions.iconContainerSm),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(protocol.icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(SharedDimensions.iconSizeSm))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(
                    protocol.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                )
                Text(
                    protocol.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                protocol.capabilities.forEach { capability ->
                    DnsCapabilityChip(capability, strings.labelFor(capability))
                }
            }
            if (selected && !selectedWorking && strings.notWorking.isNotBlank()) {
                Text(
                    strings.notWorking,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DnsCapabilityChip(capability: RethinkDnsCapability, label: String) {
    val color = capability.tint()
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
        color = color.copy(alpha = 0.14f),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
        )
    }
}

@Composable
private fun RethinkDnsCapability.tint(): Color = when (this) {
    RethinkDnsCapability.Fast -> MaterialTheme.colorScheme.primary
    RethinkDnsCapability.Private -> MaterialTheme.colorScheme.secondary
    RethinkDnsCapability.Secure -> MaterialTheme.colorScheme.tertiary
    RethinkDnsCapability.Anonymous -> MaterialTheme.colorScheme.secondary
}

private fun RethinkDnsListStrings.labelFor(capability: RethinkDnsCapability): String = when (capability) {
    RethinkDnsCapability.Fast -> fast
    RethinkDnsCapability.Private -> private
    RethinkDnsCapability.Secure -> secure
    RethinkDnsCapability.Anonymous -> anonymous
}
