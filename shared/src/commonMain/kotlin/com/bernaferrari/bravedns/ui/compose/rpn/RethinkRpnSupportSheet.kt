/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.rpn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
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
                    RethinkFilterChip(
                        selected = category == item,
                        onClick = { category = if (category == item) null else item },
                        label = item,
                        minHeight = 40.dp,
                        shape = RoundedCornerShape(SharedDimensions.cornerRadiusPill),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        border = BorderStroke(
                            SharedDimensions.dividerThickness,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        ),
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharedDimensions.cornerRadiusXl),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    SharedDimensions.dividerThickness,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = SharedDimensions.spacingXs),
                ) {
                    SupportDataToggle(
                        label = strings.includeSubscription,
                        icon = MaterialSymbols.Filled.VpnKey,
                        checked = includeSubscription,
                        enabled = !sending,
                        onCheckedChange = { includeSubscription = it },
                    )
                    SupportDataToggle(
                        label = strings.includeHistory,
                        icon = MaterialSymbols.Filled.EventNote,
                        checked = includeHistory,
                        enabled = !sending,
                        onCheckedChange = { includeHistory = it },
                    )
                    SupportDataToggle(
                        label = strings.includeDiagnostics,
                        icon = MaterialSymbols.Filled.BugReport,
                        checked = includeDiagnostics,
                        enabled = !sending,
                        onCheckedChange = { includeDiagnostics = it },
                    )
                }
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
                modifier = Modifier.heightIn(min = SharedDimensions.touchTargetSm),
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
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MaterialSymbols.Filled.Headset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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

@Composable
private fun SupportDataToggle(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = SharedDimensions.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
        )
    }
}
