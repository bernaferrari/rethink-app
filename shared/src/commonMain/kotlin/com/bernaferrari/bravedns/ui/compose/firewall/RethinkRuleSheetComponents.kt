/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

val RethinkRuleSheetBottomPaddingWithActions: Dp = SharedDimensions.spacing3xl + SharedDimensions.spacingMd
val RethinkRuleSheetBottomPaddingCompact: Dp = SharedDimensions.spacing2xl + SharedDimensions.spacingSm

/** Target-neutral base sheet for firewall-rule editing flows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkRuleSheetModal(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    RethinkModalBottomSheet(
        onDismissRequest = onDismissRequest,
        contentPadding = PaddingValues(SharedDimensions.spacingNone),
        verticalSpacing = SharedDimensions.spacingNone,
        includeBottomSpacer = false,
        // Rule editors and the Android LAN/reachability wrappers contain save actions. Opening
        // them fully avoids a partially-expanded sheet hiding those actions on short viewports.
        expandOnShow = true,
        content = content,
    )
}

@Composable
fun RethinkRuleSheetLayout(
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = SharedDimensions.spacingMd,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

@Composable
fun RethinkRuleSheetDeleteAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(label)
        }
    }
}

@Composable
fun RethinkRuleSheetDeleteDialog(
    title: String,
    message: String,
    deleteLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmText = deleteLabel,
        dismissText = cancelLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isConfirmDestructive = true,
    )
}

@Composable
fun RethinkRuleSheetSummaryPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
    textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = SharedDimensions.spacingMd, vertical = SharedDimensions.spacingSm),
        )
    }
}
