package com.bernaferrari.bravedns.ui.compose.theme

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp

@Composable
fun RethinkSearchField(
    query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, shape: RoundedCornerShape = RoundedCornerShape(SharedDimensions.cornerRadiusMdLg),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh, textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary, iconSize: Dp = SharedDimensions.iconSizeSm,
    trailingIconSize: Dp = iconSize, trailingIconButtonSize: Dp? = null, clearQueryContentDescription: String? = null,
    closeWhenEmptyContentDescription: String? = null, onClearQuery: (() -> Unit)? = null, onCloseWhenEmpty: (() -> Unit)? = null,
) = TextField(
    value = query, onValueChange = onQueryChange, modifier = modifier, singleLine = true, enabled = enabled, textStyle = textStyle,
    placeholder = { Text(placeholder) }, leadingIcon = { Icon(MaterialSymbols.Filled.Search, null, Modifier.size(iconSize), leadingIconTint) },
    trailingIcon = {
        val action = if (query.isNotEmpty()) onClearQuery ?: { onQueryChange("") } else onCloseWhenEmpty
        val actionModifier = trailingIconButtonSize?.let { requestedSize ->
            Modifier.size(requestedSize.coerceAtLeast(SharedDimensions.touchTargetSm))
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
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow, labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium, leadingIcon: (@Composable () -> Unit)? = null,
    selectedLeadingIconColor: Color = selectedLabelColor, leadingIconColor: Color = labelColor, border: BorderStroke? = null,
    minHeight: Dp = 0.dp, selectedLabelWeight: FontWeight = FontWeight.SemiBold, defaultLabelWeight: FontWeight = FontWeight.Normal,
    labelMaxLines: Int = 1, labelOverflow: TextOverflow = TextOverflow.Ellipsis,
) = FilterChip(
    modifier = modifier.heightIn(min = minHeight), selected = selected, onClick = onClick,
    label = { Text(label, fontWeight = if (selected) selectedLabelWeight else defaultLabelWeight, style = textStyle, maxLines = labelMaxLines, overflow = labelOverflow) },
    leadingIcon = leadingIcon, shape = shape, border = border,
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = selectedContainerColor, selectedLabelColor = selectedLabelColor, containerColor = containerColor, labelColor = labelColor, selectedLeadingIconColor = selectedLeadingIconColor, iconColor = leadingIconColor),
)
