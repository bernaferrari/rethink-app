@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString

/**
 * A row whose whole surface is one switch: tapping anywhere toggles, TalkBack announces
 * "Switch, on". The trailing [Switch] is display-only so it never becomes a second target.
 */
@Composable
fun RethinkSwitchListItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingAnnotated: AnnotatedString? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
    singleLineHeadline: Boolean = false,
) = RethinkListItem(
    modifier = modifier,
    headline = headline,
    supporting = supporting,
    supportingAnnotated = supportingAnnotated,
    leadingIcon = leadingIcon,
    colors = rethinkListItemColors(leadingIconTint = leadingIconTint, leadingIconContainerColor = leadingIconContainerColor, highlightContainerColor = highlightContainerColor),
    leadingContent = leadingContent,
    position = position,
    enabled = enabled,
    highlighted = highlighted,
    singleLineHeadline = singleLineHeadline,
    selection = RethinkListSelection(checked = checked, role = Role.Switch, onCheckedChange = onCheckedChange),
    trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
)

/**
 * The app's one single-choice row: the whole row is the radio (TalkBack: "Radio button,
 * selected"), the selected row fills with secondaryContainer, the selection family shared with
 * chips and connected groups, and a display-only trailing [RadioButton] shows the state. Put
 * these in a `RethinkListGroup(Modifier.selectableGroup())`.
 */
@Composable
fun RethinkRadioListItem(
    headline: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
) = RethinkListItem(
    modifier = modifier,
    headline = headline,
    supporting = supporting,
    leadingIcon = leadingIcon,
    position = position,
    enabled = enabled,
    highlighted = selected,
    colors = rethinkListItemColors(highlightContainerColor = MaterialTheme.colorScheme.secondaryContainer),
    selection = RethinkListSelection(checked = selected, role = Role.RadioButton) { onSelect() },
    trailing = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
)

/** [RethinkSwitchListItem] counterpart for multi-select lists: the whole row is one checkbox. */
@Composable
fun RethinkCheckboxListItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingAnnotated: AnnotatedString? = null,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = MaterialTheme.colorScheme.primary,
    leadingIconContainerColor: Color? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    position: CardPosition = CardPosition.Middle,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    highlightContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
    singleLineHeadline: Boolean = false,
) = RethinkListItem(
    modifier = modifier,
    headline = headline,
    supporting = supporting,
    supportingAnnotated = supportingAnnotated,
    leadingIcon = leadingIcon,
    colors = rethinkListItemColors(leadingIconTint = leadingIconTint, leadingIconContainerColor = leadingIconContainerColor, highlightContainerColor = highlightContainerColor),
    leadingContent = leadingContent,
    position = position,
    enabled = enabled,
    highlighted = highlighted,
    singleLineHeadline = singleLineHeadline,
    selection = RethinkListSelection(checked = checked, role = Role.Checkbox, onCheckedChange = onCheckedChange),
    trailing = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
)

/**
 * Trailing control for a split row: the row itself navigates (opens details/logs) and this
 * switch is a separate target. No divider: it squeezed titles onto two lines, and the switch is
 * already its own target; it carries [contentDescription] so it is announced on its own.
 */
@Composable
fun RethinkSplitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    )
}
