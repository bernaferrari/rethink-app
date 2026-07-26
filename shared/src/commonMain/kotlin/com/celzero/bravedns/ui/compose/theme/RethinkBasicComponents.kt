/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalPadding: Dp = SharedDimensions.spacingNone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = SharedDimensions.spacingMd, bottom = SharedDimensions.spacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, color = color)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = SharedDimensions.spacingSm, vertical = 0.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color, letterSpacing = 0.1.sp)
            }
        }
    }
}

@Composable
fun SectionHeaderWithSubtitle(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalPadding: Dp = SharedDimensions.spacingNone,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = SharedDimensions.spacingMd, bottom = SharedDimensions.spacingSm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp, color = color)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = SharedDimensions.spacingSm, vertical = 0.dp)) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = color, letterSpacing = 0.1.sp)
                }
            }
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = SharedDimensions.spacingXs)) }
    }
}

/**
 * A compact heading and content group for information-dense forms and detail screens.
 *
 * The content deliberately sits directly on the screen surface. Individual controls already
 * provide their own Material containers, so adding another card behind every section makes forms
 * look heavy and creates the nested-background effect that is especially noticeable on Web.
 */
@Composable
fun RethinkFormSection(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm),
            content = content,
        )
    }
}

/**
 * The canonical text input for Rethink forms.
 *
 * Keeping the expressive field shape here prevents individual settings screens and sheets from
 * quietly falling back to Material's much tighter default outline. Form fields are intentionally
 * full width; compact search controls and inline filters should continue to use their dedicated
 * components instead.
 */
@Composable
fun RethinkFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText,
        textStyle = textStyle,
        shape = RoundedCornerShape(SharedDimensions.cornerRadiusLg),
    )
}

/**
 * Standard action treatment for an editor presented in a sheet or dialog.
 *
 * A quiet text dismissal and one filled confirmation action keeps reversible edits legible
 * without turning every compact form into a second full-width footer.  Centralising it also
 * prevents individual sheets from drifting in action order, spacing, or button treatment.
 */
@Composable
fun RethinkFormActionRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dismissLabel != null && onDismiss != null) {
            TextButton(onClick = onDismiss, enabled = dismissEnabled) { Text(dismissLabel) }
        }
        Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.height(SharedDimensions.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        icon?.let { Icon(it, null, modifier = Modifier.size(SharedDimensions.iconSizeSm)); Spacer(Modifier.width(SharedDimensions.spacingSm)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(SharedDimensions.buttonHeight), enabled = enabled, shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius)) {
        icon?.let { Icon(it, null, modifier = Modifier.size(SharedDimensions.iconSizeSm)); Spacer(Modifier.width(SharedDimensions.spacingSm)) }
        Text(text)
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier, isHighlighted: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.5).sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), letterSpacing = 0.2.sp)
    }
}
