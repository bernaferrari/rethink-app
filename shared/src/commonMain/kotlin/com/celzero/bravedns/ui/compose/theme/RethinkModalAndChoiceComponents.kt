/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Two-target shared choice control for compact manual/automatic configuration modes. */
@Composable
fun RethinkTwoOptionSegmentedRow(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = SharedDimensions.spacingNone,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        listOf(true, false).forEachIndexed { index, selected ->
            val isSelected = selected == leftSelected
            SegmentedButton(
                modifier = Modifier.weight(1f).heightIn(min = minHeight),
                selected = isSelected,
                onClick = { if (!isSelected) if (selected) onLeftClick() else onRightClick() },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                label = { Text(if (selected) leftLabel else rightLabel) },
            )
        }
    }
}

/**
 * Radio-choice content for dialogs and sheets. The row, not only the radio glyph, is interactive;
 * the capped viewport keeps long preference lists usable without pushing dialog actions off-screen.
 */
@Composable
fun <T> RethinkRadioChoiceList(
    options: List<T>,
    selected: (T) -> Boolean,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: ((T) -> String?)? = null,
    enabled: (T) -> Boolean = { true },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        RethinkListGroup {
            options.forEachIndexed { index, option ->
                val isSelected = selected(option)
                val isEnabled = enabled(option)
                RethinkListItem(
                    headline = label(option),
                    supporting = supporting?.invoke(option),
                    position = cardPositionFor(index, options.lastIndex),
                    enabled = isEnabled,
                    highlighted = isSelected,
                    onClick = { if (isEnabled) onSelected(option) },
                    trailing = {
                        RadioButton(
                            selected = isSelected,
                            enabled = isEnabled,
                            onClick = null,
                        )
                    },
                )
            }
        }
    }
}

/** Shared confirmation dialog with up to three actions. */
@Composable
fun RethinkMultiActionDialog(
    onDismissRequest: () -> Unit,
    title: String,
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryText: String? = null,
    onTertiary: (() -> Unit)? = null,
    isPrimaryDestructive: Boolean = false,
    text: (@Composable (() -> Unit))? = null,
) {
    val primaryColors =
        if (isPrimaryDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.textButtonColors()
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = text ?: message?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onPrimary, colors = primaryColors) { Text(primaryText) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                if (secondaryText != null && onSecondary != null) TextButton(onClick = onSecondary) { Text(secondaryText) }
                if (tertiaryText != null && onTertiary != null) TextButton(onClick = onTertiary) { Text(tertiaryText) }
            }
        },
    )
}

@Composable
fun RethinkBottomSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(top = SharedDimensions.spacingXs, bottom = SharedDimensions.spacingSm),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(44.dp).height(5.dp),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
        ) {}
    }
}

/** Shared M3 sheet chrome. Hosts retain only their source of state and mutations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = { RethinkBottomSheetDragHandle() },
    containerColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingSm),
    verticalSpacing: Dp = SharedDimensions.spacingLg,
    includeBottomSpacer: Boolean = true,
    expandOnShow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues =
            if (expandOnShow) {
                setOf(SheetValue.Hidden, SheetValue.Expanded)
            } else {
                setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
            },
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = dragHandle,
        containerColor = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surface else containerColor,
        sheetState = sheetState,
    ) {
        Column(
            modifier = modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            content()
            if (includeBottomSpacer) Spacer(Modifier.height(SharedDimensions.spacing2xl))
        }
    }
}

@Composable
fun RethinkBottomSheetCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(SharedDimensions.cornerRadius3xl),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(SharedDimensions.dividerThicknessBold, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
        tonalElevation = SharedDimensions.spacingNone,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            content = content,
        )
    }
}
