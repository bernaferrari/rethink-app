/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.rpn

import com.bernaferrari.bravedns.ui.compose.theme.RethinkIconWell
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListSelection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFilterChip
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

data class RethinkRpnSupportStrings(
    val title: String,
    val description: String,
    val categories: List<String>,
    val reportHint: String,
    val includeSubscription: String,
    val includeHistory: String,
    val includeDiagnostics: String,
    val createEmail: String,
    val preparing: String,
    val cancel: String,
)

/** Shared RPN support composer. Hosts create and attach platform diagnostics only after submit. */
@Composable
fun RethinkRpnSupportSheet(
    strings: RethinkRpnSupportStrings,
    sending: Boolean,
    onSubmit: (
        description: String,
        category: String?,
        includeSubscription: Boolean,
        includeHistory: Boolean,
        includeDiagnostics: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var includeSubscription by remember { mutableStateOf(true) }
    var includeHistory by remember { mutableStateOf(true) }
    var includeDiagnostics by remember { mutableStateOf(true) }

    RethinkModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = SharedDimensions.screenPaddingHorizontal,
            vertical = SharedDimensions.spacingSm,
        ),
        verticalSpacing = SharedDimensions.spacingLg,
        includeBottomSpacer = false,
        expandOnShow = true,
    ) { dismissSheet ->
        SupportSheetHeader(strings.title, strings.description)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                strings.categories.forEach { item ->
                    // Same chip family as the rest of the app's filters: tonal selection, no outline.
                    RethinkFilterChip(
                        selected = category == item,
                        onClick = { category = if (category == item) null else item },
                        label = item,
                        leadingIcon = if (category == item) {
                            { Icon(MaterialSymbols.Filled.Check, null, Modifier.size(SharedDimensions.iconSizeXs)) }
                        } else {
                            null
                        },
                    )
                }
            }
            RethinkFormTextField(
                value = description,
                onValueChange = { description = it },
                label = strings.reportHint,
                minLines = 4,
                enabled = !sending,
            )
            RethinkListGroup {
                SupportDataToggle(strings.includeSubscription, MaterialSymbols.Filled.VpnKey, includeSubscription, !sending, CardPosition.First) { includeSubscription = it }
                SupportDataToggle(strings.includeHistory, MaterialSymbols.Filled.EventNote, includeHistory, !sending, CardPosition.Middle) { includeHistory = it }
                SupportDataToggle(strings.includeDiagnostics, MaterialSymbols.Filled.BugReport, includeDiagnostics, !sending, CardPosition.Last) { includeDiagnostics = it }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            PrimaryButton(
                text = if (sending) strings.preparing else strings.createEmail,
                icon = MaterialSymbols.Filled.Email,
                onClick = {
                    onSubmit(
                        description.trim(),
                        category,
                        includeSubscription,
                        includeHistory,
                        includeDiagnostics,
                    )
                },
                enabled = !sending && (description.isNotBlank() || category != null),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = dismissSheet,
                enabled = !sending,
                modifier = Modifier.heightIn(min = SharedDimensions.touchTargetMin),
            ) {
                Text(strings.cancel)
            }
        }
    }
}

@Composable
private fun SupportSheetHeader(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RethinkIconWell(
            icon = MaterialSymbols.Filled.Headset,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            size = SharedDimensions.iconContainerLg,
            iconSize = SharedDimensions.iconSizeMd,
            shape = CircleShape,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Diagnostics opt-in as a grouped switch row, matching every other toggle list in the app. */
@Composable
private fun SupportDataToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    position: CardPosition,
    onCheckedChange: (Boolean) -> Unit,
) {
    RethinkListItem(
        headline = label,
        leadingIcon = icon,
        position = position,
        enabled = enabled,
        // The sheet itself is surfaceContainerLow; one step up keeps the grouped rows visible.
        trailing = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
        selection = RethinkListSelection(checked = checked, onCheckedChange = onCheckedChange),
    )
}
