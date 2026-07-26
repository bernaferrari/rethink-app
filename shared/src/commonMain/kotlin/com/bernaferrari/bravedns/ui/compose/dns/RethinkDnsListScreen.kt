/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLazyColumnScreenScaffold
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
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
        containerColor = MaterialTheme.colorScheme.background,
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
                SectionHeader(title = strings.configure)
                DnsProtocolGrid(
                    protocols = protocols,
                    selectedProtocolId = selectedProtocolId,
                    selectedProtocolWorking = selectedProtocolWorking,
                    onProtocolClick = onProtocolClick,
                )
            }
            item { DnsCapabilityLegend(strings) }
    }
}

@Composable
private fun DnsProtocolGrid(
    protocols: List<RethinkDnsProtocol>,
    selectedProtocolId: Int?,
    selectedProtocolWorking: Boolean,
    onProtocolClick: (RethinkDnsProtocol) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
        protocols.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                row.forEach { protocol ->
                    DnsProtocolCard(
                        protocol = protocol,
                        selected = selectedProtocolId == protocol.id,
                        selectedWorking = selectedProtocolWorking,
                        modifier = Modifier.weight(1f),
                        onClick = { onProtocolClick(protocol) },
                    )
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DnsProtocolCard(
    protocol: RethinkDnsProtocol,
    selected: Boolean,
    selectedWorking: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when {
        selected && selectedWorking -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val labelColor = when {
        selected && selectedWorking -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = when {
        selected && selectedWorking -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val cardShape = RoundedCornerShape(SharedDimensions.cornerRadius3xl)
    Surface(
        modifier = modifier.height(164.dp).clip(cardShape),
        shape = cardShape,
        color = containerColor,
        tonalElevation = 0.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(SharedDimensions.spacingMd),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(SharedDimensions.iconContainerRadius),
                    color = titleColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(protocol.icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(22.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    protocol.capabilities.forEach { DnsCapabilityDot(it) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                Text(
                    protocol.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = labelColor,
                )
                Text(
                    protocol.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                )
            }
        }
    }
}

@Composable
private fun DnsCapabilityLegend(strings: RethinkDnsListStrings) {
    Surface(
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = SharedDimensions.spacingLg,
                vertical = SharedDimensions.spacingMd,
            ).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DnsLegendItem(RethinkDnsCapability.Fast, strings.fast)
            DnsLegendItem(RethinkDnsCapability.Private, strings.private)
            DnsLegendItem(RethinkDnsCapability.Secure, strings.secure)
            DnsLegendItem(RethinkDnsCapability.Anonymous, strings.anonymous)
        }
    }
}

@Composable
private fun DnsLegendItem(capability: RethinkDnsCapability, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        DnsCapabilityDot(capability)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DnsCapabilityDot(capability: RethinkDnsCapability, modifier: Modifier = Modifier) {
    val color = when (capability) {
        RethinkDnsCapability.Fast -> MaterialTheme.colorScheme.error
        RethinkDnsCapability.Private -> MaterialTheme.colorScheme.tertiary
        RethinkDnsCapability.Secure -> MaterialTheme.colorScheme.primary
        RethinkDnsCapability.Anonymous -> MaterialTheme.colorScheme.secondary
    }
    Box(modifier.size(10.dp).background(color = color, shape = CircleShape))
}
