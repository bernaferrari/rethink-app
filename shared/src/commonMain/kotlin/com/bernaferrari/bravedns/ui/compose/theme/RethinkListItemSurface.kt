/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role

@Composable
internal fun RethinkListItemSurface(
    selection: RethinkListSelection?, enabled: Boolean,
    onClick: (() -> Unit)?, onLongClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource, shape: Shape,
    containerColor: Color, surfaceModifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val interaction = when {
        selection != null -> if (selection.role == Role.RadioButton) {
            Modifier.selectable(
                selected = selection.checked, interactionSource = interactionSource,
                indication = ripple(), enabled = enabled, role = selection.role,
                onClick = { selection.onCheckedChange(true) },
            )
        } else {
            Modifier.toggleable(
                value = selection.checked, interactionSource = interactionSource,
                indication = ripple(), enabled = enabled, role = selection.role,
                onValueChange = selection.onCheckedChange,
            )
        }
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            interactionSource = interactionSource, indication = ripple(), enabled = enabled,
            onLongClick = onLongClick, onClick = onClick, role = Role.Button,
        )
        onClick != null -> Modifier.clickable(
            interactionSource = interactionSource, indication = ripple(), enabled = enabled, onClick = onClick, role = Role.Button,
        )
        else -> Modifier
    }
    Surface(shape = shape, color = containerColor, modifier = surfaceModifier) {
        // Expressive ListItem owns its own merged label node. Put interactions on that
        // same node so TalkBack receives the label, role, state and actions together.
        content(interaction)
    }
}
