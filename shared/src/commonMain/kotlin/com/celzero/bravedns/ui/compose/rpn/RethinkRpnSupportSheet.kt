/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.rpn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkListGroup
import com.celzero.bravedns.ui.compose.theme.RethinkListItem
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.ui.compose.theme.cardPositionFor

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

/** Shared RPN support sheet. Hosts create and attach platform diagnostics only after submit. */
@Composable
fun RethinkRpnSupportSheet(
    strings: RethinkRpnSupportStrings,
    sending: Boolean,
    onSubmit: (description: String, category: String?, includeSubscription: Boolean, includeHistory: Boolean, includeDiagnostics: Boolean) -> Unit,
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
        verticalSpacing = SharedDimensions.spacingMd,
        includeBottomSpacer = false,
    ) {
        Text(strings.title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text(
            strings.description,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            ) {
                strings.categories.forEach { item ->
                    RethinkFilterChip(
                        selected = category == item,
                        onClick = { category = if (category == item) null else item },
                        label = item,
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = description,
                onValueChange = { description = it },
                label = { Text(strings.reportHint) },
                minLines = 3,
                enabled = !sending,
            )
            RethinkListGroup {
                listOf(
                    strings.includeSubscription to includeSubscription,
                    strings.includeHistory to includeHistory,
                    strings.includeDiagnostics to includeDiagnostics,
                ).forEachIndexed { index, (label, checked) ->
                    RethinkListItem(
                        headline = label,
                        position = cardPositionFor(index, 2),
                        enabled = !sending,
                        onClick = {
                            when (index) {
                                0 -> includeSubscription = !includeSubscription
                                1 -> includeHistory = !includeHistory
                                else -> includeDiagnostics = !includeDiagnostics
                            }
                        },
                        trailing = {
                            Switch(
                                checked = checked,
                                enabled = !sending,
                                onCheckedChange = { enabled ->
                                    when (index) {
                                        0 -> includeSubscription = enabled
                                        1 -> includeHistory = enabled
                                        else -> includeDiagnostics = enabled
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss, enabled = !sending) { Text(strings.cancel) }
            Spacer(Modifier.width(SharedDimensions.spacingSm))
            Button(
                onClick = { onSubmit(description.trim(), category, includeSubscription, includeHistory, includeDiagnostics) },
                enabled = !sending && (description.isNotBlank() || category != null),
            ) { Text(if (sending) strings.preparing else strings.createEmail) }
        }
    }
}
