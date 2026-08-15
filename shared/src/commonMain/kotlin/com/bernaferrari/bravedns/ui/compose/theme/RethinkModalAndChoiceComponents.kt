/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Two-target shared choice control for compact manual/automatic configuration modes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkTwoOptionSegmentedRow(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = SharedDimensions.buttonHeightSm,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = leftSelected,
            onCheckedChange = { if (it) onLeftClick() },
            modifier = Modifier.weight(1f).heightIn(min = minHeight),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(leftLabel)
        }
        ToggleButton(
            checked = !leftSelected,
            onCheckedChange = { if (it) onRightClick() },
            modifier = Modifier.weight(1f).heightIn(min = minHeight),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(rightLabel)
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
    icon: (@Composable (() -> Unit))? = null,
) {
    RethinkDialogScaffold(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        message = message,
        text = text,
        confirmButton = {
            Button(onClick = onPrimary, colors = rethinkDialogConfirmColors(isPrimaryDestructive)) {
                Text(primaryText)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
                if (secondaryText != null && onSecondary != null) TextButton(onClick = onSecondary) { Text(secondaryText) }
                if (tertiaryText != null && onTertiary != null) TextButton(onClick = onTertiary) { Text(tertiaryText) }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkBottomSheetDragHandle(modifier: Modifier = Modifier) {
    BottomSheetDefaults.DragHandle(modifier = modifier)
}

/** Shared M3 sheet chrome. Hosts retain only their source of state and mutations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = { RethinkBottomSheetDragHandle() },
    containerColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = SharedDimensions.spacingXl, vertical = SharedDimensions.spacingLg),
    verticalSpacing: Dp = SharedDimensions.spacingLg,
    includeBottomSpacer: Boolean = true,
    expandOnShow: Boolean = false,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
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
    LaunchedEffect(sheetState) {
        sheetState.show()
    }
    val scope = rememberCoroutineScope()
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    var dismissing by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            // Remove the host immediately. On WebAssembly, waiting for hide() can leave the
            // sheet's semantics/scrim mounted after it has visually disappeared.
            currentOnDismissRequest()
            scope.launch {
                sheetState.hide()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        dragHandle = dragHandle,
        containerColor = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerLow else containerColor,
        sheetState = sheetState,
    ) {
        Column(
            modifier = modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            content(dismiss)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = SharedDimensions.spacingNone,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            content = content,
        )
    }
}
