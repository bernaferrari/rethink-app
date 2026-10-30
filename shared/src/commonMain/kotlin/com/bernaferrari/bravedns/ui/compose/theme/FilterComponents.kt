package com.bernaferrari.bravedns.ui.compose.theme

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun RethinkSearchField(
    query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    // Pill on the same tonal level as toolbar actions, so search + filter buttons read as one strip.
    enabled: Boolean = true, shape: RoundedCornerShape = RoundedCornerShape(SharedDimensions.cornerRadiusFull),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow, textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary, iconSize: Dp = SharedDimensions.iconSizeSm,
    trailingIconSize: Dp = iconSize, trailingIconButtonSize: Dp? = null, clearQueryContentDescription: String? = null,
    closeWhenEmptyContentDescription: String? = null, onClearQuery: (() -> Unit)? = null, onCloseWhenEmpty: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    /** Replaces the search glyph (e.g. with the scoped app's icon); null removes the leading slot. */
    leadingContent: (@Composable () -> Unit)? = { Icon(MaterialSymbols.Filled.Search, null, Modifier.size(iconSize), leadingIconTint) },
) = TextField(
    value = query, onValueChange = onQueryChange, modifier = modifier, singleLine = true, enabled = enabled, textStyle = textStyle,
    keyboardOptions = keyboardOptions,
    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = leadingContent,
    trailingIcon = {
        val action = if (query.isNotEmpty()) onClearQuery ?: { onQueryChange("") } else onCloseWhenEmpty
        val actionModifier = trailingIconButtonSize?.let { requestedSize ->
            Modifier.size(requestedSize.coerceAtLeast(SharedDimensions.touchTargetMin))
        } ?: Modifier
        if (action != null) IconButton(action, actionModifier) {
            Icon(MaterialSymbols.Filled.Close, if (query.isNotEmpty()) clearQueryContentDescription else closeWhenEmptyContentDescription, Modifier.size(trailingIconSize))
        }
    },
    shape = shape, colors = TextFieldDefaults.colors(focusedContainerColor = containerColor, unfocusedContainerColor = containerColor, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
)

@Composable
fun RethinkFilterChip(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
    // Selection family shared with connected groups and toggle actions.
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer, selectedLabelColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow, labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium, leadingIcon: (@Composable () -> Unit)? = null,
    selectedLeadingIconColor: Color = selectedLabelColor, leadingIconColor: Color = labelColor, border: BorderStroke? = null,
    minHeight: Dp = SharedDimensions.buttonHeightSm, selectedLabelWeight: FontWeight = FontWeight.Medium, defaultLabelWeight: FontWeight = FontWeight.Medium,
    labelMaxLines: Int = 1, labelOverflow: TextOverflow = TextOverflow.Ellipsis,
    /** A trailing caret marks a chip that opens a menu rather than toggling. */
    trailingIcon: (@Composable () -> Unit)? = null,
) = FilterChip(
    modifier = modifier.heightIn(min = minHeight), selected = selected, onClick = onClick,
    label = { Text(label, fontWeight = if (selected) selectedLabelWeight else defaultLabelWeight, style = textStyle, maxLines = labelMaxLines, overflow = labelOverflow) },
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape, border = border,
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = selectedContainerColor, selectedLabelColor = selectedLabelColor, containerColor = containerColor, labelColor = labelColor, selectedLeadingIconColor = selectedLeadingIconColor, iconColor = leadingIconColor, selectedTrailingIconColor = selectedLeadingIconColor),
)

/** A chip that opens a menu: the label shows the current choice, a caret marks it as a menu. */
@Composable
fun RethinkMenuChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        RethinkFilterChip(
            label = label,
            selected = selected,
            onClick = { expanded = true },
            trailingIcon = { Icon(MaterialSymbols.Filled.KeyboardArrowDown, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menuContent { expanded = false }
        }
    }
}
