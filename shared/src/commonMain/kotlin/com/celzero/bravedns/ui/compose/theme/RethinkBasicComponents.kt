/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.compose.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
