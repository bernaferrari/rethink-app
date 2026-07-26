/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import kotlinx.coroutines.launch

/** Platform-neutral snapshot of a WireGuard configuration that can be used as a hop. */
data class RethinkWireguardHopItem(
    val id: String,
    val name: String,
    val status: String,
    val isActive: Boolean,
    val hasIpv4: Boolean = false,
    val hasIpv6: Boolean = false,
    val isSplitTunnel: Boolean = false,
    val isAmnezia: Boolean = false,
    val isHopSource: Boolean = false,
    val isAlreadyHop: Boolean = false,
    val properties: String = "",
)

data class RethinkWireguardHopPickerStrings(
    val title: String,
    val done: String,
    val ipv4: String,
    val ipv6: String,
    val splitTunnel: String,
    val amnezia: String,
    val hopSource: String,
    val alreadyHop: String,
)

/** Result of a platform-owned hop test and persistence operation. */
data class RethinkWireguardHopToggleResult(
    val succeeded: Boolean,
    val selectedId: String? = null,
)

/**
 * Portable hop-selection content. The host supplies the WireGuard validation and persistence
 * operation; all selection, loading, cards, chips, and action layout live in common code.
 */
@Composable
fun RethinkWireguardHopPicker(
    items: List<RethinkWireguardHopItem>,
    selectedId: String?,
    strings: RethinkWireguardHopPickerStrings,
    onToggle: suspend (RethinkWireguardHopItem, Boolean) -> RethinkWireguardHopToggleResult,
    onSelectedIdChange: (String?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inProgressId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun toggle(item: RethinkWireguardHopItem, checked: Boolean) {
        if (inProgressId != null) return
        scope.launch {
            inProgressId = item.id
            val result = onToggle(item, checked)
            if (result.succeeded) onSelectedIdChange(result.selectedId)
            inProgressId = null
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSmMd),
    ) {
        Text(
            text = strings.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            shape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                items(items, key = { it.id }) { item ->
                    val checked = item.id == selectedId
                    RethinkWireguardHopRow(
                        item = item,
                        checked = checked,
                        inProgress = item.id == inProgressId,
                        strings = strings,
                        onCheckedChange = { toggle(item, it) },
                    )
                }
            }
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onDone) { Text(strings.done) }
    }
}

@Composable
private fun RethinkWireguardHopRow(
    item: RethinkWireguardHopItem,
    checked: Boolean,
    inProgress: Boolean,
    strings: RethinkWireguardHopPickerStrings,
    onCheckedChange: (Boolean) -> Unit,
) {
    val strokeColor = when {
        checked && item.isActive -> MaterialTheme.colorScheme.tertiary
        checked -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val chips = buildList {
        if (item.hasIpv4) add(strings.ipv4)
        if (item.hasIpv6) add(strings.ipv6)
        if (item.isSplitTunnel) add(strings.splitTunnel)
        if (item.isAmnezia) add(strings.amnezia)
        if (item.isHopSource) add(strings.hopSource)
        if (item.isAlreadyHop) add(strings.alreadyHop)
        if (item.properties.isNotBlank()) add(item.properties)
    }
    Card(
        onClick = { onCheckedChange(!checked) },
        enabled = !inProgress,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SharedDimensions.spacingSm, vertical = SharedDimensions.spacingXs),
        shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = if (checked) {
            BorderStroke(2.dp, strokeColor)
        } else {
            BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SharedDimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${item.name} (${item.id})", style = MaterialTheme.typography.titleMedium)
                    Text(item.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = { if (!inProgress) onCheckedChange(it) },
                    enabled = !inProgress,
                )
            }
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                    chips.forEach { label -> AssistChip(onClick = {}, label = { Text(label) }) }
                }
            }
            if (inProgress) {
                Spacer(Modifier.height(SharedDimensions.spacingXs))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
