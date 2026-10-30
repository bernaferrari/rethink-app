@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
    containerColor: Color, border: BorderStroke?, surfaceModifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (selection != null) {
        val controlModifier = if (selection.role == Role.RadioButton) {
            Modifier.selectable(
                selected = selection.checked,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = selection.role,
                onClick = { selection.onCheckedChange(true) },
            )
        } else {
            Modifier.toggleable(
                value = selection.checked,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = selection.role,
                onValueChange = selection.onCheckedChange,
            )
        }
        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            modifier = surfaceModifier.then(controlModifier),
            content = content,
        )
    } else if (onClick != null && onLongClick != null) {
        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            modifier = surfaceModifier.combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
            content = content,
        )
    } else if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = shape,
            color = containerColor,
            border = border,
            modifier = surfaceModifier,
            content = content,
        )
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            modifier = surfaceModifier,
            content = content,
        )
    }
}
